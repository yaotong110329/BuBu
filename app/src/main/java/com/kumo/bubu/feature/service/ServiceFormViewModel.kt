package com.kumo.bubu.feature.service

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.ServiceAttachmentInput
import com.kumo.bubu.domain.model.MAX_SERVICE_ATTACHMENTS_PER_RECORD
import com.kumo.bubu.domain.model.PaymentMethod
import com.kumo.bubu.domain.model.ServiceItemInput
import com.kumo.bubu.domain.model.ServiceRecordInput
import com.kumo.bubu.domain.model.ServiceQuantityUnit
import com.kumo.bubu.domain.model.ServiceTypeInput
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.ServiceAttachmentException
import com.kumo.bubu.domain.repository.ServiceRecordException
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.domain.repository.ServiceWriteStage
import com.kumo.bubu.domain.repository.VehicleRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.serialization.json.Json
import com.kumo.bubu.core.state.FormDraftPersistence
import com.kumo.bubu.domain.repository.FormDraftRepository

class ServiceFormViewModel(
    private val serviceRepository: ServiceRepository,
    private val vehicleRepository: VehicleRepository,
    private val editId: Long? = null,
    private val initialVehicleId: Long? = null,
    private val initialServiceTypeId: Long? = null,
    nowProvider: () -> LocalDateTime = LocalDateTime::now,
    savedStateHandle: SavedStateHandle,
    draftRepository: FormDraftRepository? = null,
) : ViewModel() {
    private val initialNow = nowProvider()
    private val _uiState = MutableStateFlow(ServiceFormUiState(
        date = initialNow.toLocalDate().toString(),
        time = initialNow.toLocalTime().withSecond(0).withNano(0).toString(),
        editingId = editId,
    ))
    val uiState: StateFlow<ServiceFormUiState> = _uiState.asStateFlow()
    private val draftPersistence = FormDraftPersistence(savedStateHandle, DRAFT_KEY, draftRepository, viewModelScope) { pending, failed ->
        _uiState.update { it.copy(isDraftSaving = pending, draftSaveFailed = failed) }
    }
    private var restoredDraft = decodeDraft(draftPersistence.inlineContents())
    private val draftId = draftPersistence.draftId
    private var sourcePublicId = restoredDraft?.sourcePublicId
    private var sourceReady = editId == null && restoredDraft == null && !draftPersistence.isFileBacked
    private var garageReady = false
    private var attachmentsReady = restoredDraft == null && !draftPersistence.isFileBacked
    private var draftDiscarded = false
    private var attachmentImportJob: Job? = null

    private val saved = Channel<Unit>(Channel.BUFFERED)
    val savedEffects = saved.receiveAsFlow()
    private var preservedPaymentMethod: PaymentMethod? = restoredDraft?.preservedPaymentMethod
    private var initialServiceTypeApplied = restoredDraft?.initialServiceTypeApplied ?: false

    init {
        if (draftPersistence.isFileBacked) viewModelScope.launch {
            try {
                if (editId == null && serviceRepository.findServiceRecordIdByPublicId(draftId) != null) {
                    withContext(NonCancellable) {
                        serviceRepository.discardServiceDraft(draftId)
                        discardDraft()
                    }
                    saved.send(Unit)
                } else {
                    restoredDraft = decodeDraft(draftPersistence.readFile())
                    initializeDraft()
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                draftPersistence.recordRestoreFailure(failure)
                invalidateSource(ServiceFormError.DRAFT_RESTORE_FAILED)
            }
        } else initializeDraft()
    }

    private fun decodeDraft(contents: String?): ServiceFormDraft? {
        if (contents == null) return null
        return try {
            Json.decodeFromString<ServiceFormDraft>(contents).also {
                require(it.sourceId == editId && it.draftId == draftPersistence.draftId)
                it.toUiState()
            }
        } catch (failure: Exception) {
            draftPersistence.recordRestoreFailure(failure)
            null
        }
    }

    private fun initializeDraft() {
        if (draftPersistence.restoreFailed) {
            _uiState.update { it.copy(isLoading = false, sourceInvalid = true, error = ServiceFormError.DRAFT_RESTORE_FAILED) }
            viewModelScope.launch { invalidateSource(ServiceFormError.DRAFT_RESTORE_FAILED) }
            return
        }
        val draft = restoredDraft
        sourcePublicId = draft?.sourcePublicId
        sourceReady = editId == null && draft == null
        attachmentsReady = draft == null
        preservedPaymentMethod = draft?.preservedPaymentMethod
        initialServiceTypeApplied = draft?.initialServiceTypeApplied ?: false
        _uiState.value = draft?.toUiState() ?: ServiceFormUiState(
            date = initialNow.toLocalDate().toString(),
            time = initialNow.toLocalTime().withSecond(0).withNano(0).toString(),
            editingId = editId,
        )
        if (draft != null) viewModelScope.launch {
            try {
                if (editId == null) {
                    if (serviceRepository.findServiceRecordIdByPublicId(draftId) != null) {
                        serviceRepository.discardServiceDraft(draftId)
                        discardDraft()
                        saved.send(Unit)
                        return@launch
                    }
                    sourceReady = true
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logger.getLogger(ServiceFormViewModel::class.java.name)
                    .log(Level.WARNING, "Unable to reconcile a restored service draft.", failure)
                _uiState.update { it.copy(isLoading = false, sourceInvalid = true, error = ServiceFormError.SAVE_FAILED) }
                return@launch
            }
            runCatching {
                serviceRepository.recoverServiceDraftAttachments(draftId, draft.attachments.filter { it.isStaged }.map { it.relativePath }.toSet())
            }.onSuccess { valid ->
                if (draftDiscarded || _uiState.value.sourceInvalid) return@onSuccess
                attachmentsReady = true
                _uiState.update { state ->
                    val retained = state.attachments.filter { !it.isStaged || it.relativePath in valid }
                    state.copy(attachments = retained, isLoading = !sourceReady || !garageReady,
                        error = if (retained.size != state.attachments.size) ServiceFormError.ATTACHMENT_COPY_FAILED else state.error)
                }
                persistDraft()
            }.onFailure {
                if (it is CancellationException) throw it
                _uiState.update { state -> state.copy(isLoading = false, sourceInvalid = true, error = ServiceFormError.ATTACHMENT_CLEANUP_FAILED) }
            }
        }
        viewModelScope.launch { serviceRepository.ensureDefaultServiceTypes() }
        viewModelScope.launch {
            combine(
                vehicleRepository.observeGarage(),
                serviceRepository.observeServiceTypes(),
            ) { garage, types -> garage to types }.collect { (garage, types) ->
                garageReady = true
                val previous = _uiState.value
                if (previous.sourceInvalid || draftDiscarded) return@collect
                val expectedVehiclePublicId = previous.selectedVehicle?.publicId ?: restoredDraft?.selectedVehiclePublicId
                if (previous.vehicleId != null && expectedVehiclePublicId != null && !draftDiscarded &&
                    garage.vehicles.none { it.id == previous.vehicleId && it.publicId == expectedVehiclePublicId }) {
                    invalidateSource(ServiceFormError.VEHICLE_NOT_FOUND)
                    return@collect
                }
                _uiState.update { state ->
                    val activeVehicles = garage.vehicles.filterNot { it.isArchived && it.id != state.vehicleId }
                    val selectedId = state.vehicleId?.takeIf { id -> activeVehicles.any { it.id == id } }
                        ?: initialVehicleId?.takeIf { id -> activeVehicles.any { it.id == id } }
                        ?: garage.currentVehiclePublicId
                            ?.let { publicId -> activeVehicles.firstOrNull { it.publicId == publicId }?.id }
                        ?: activeVehicles.firstOrNull()?.id
                    state.copy(
                        vehicles = activeVehicles,
                        types = types,
                        vehicleId = selectedId,
                        isLoading = !sourceReady || !attachmentsReady,
                    )
                }
                persistDraft()
                if (restoredDraft == null && !initialServiceTypeApplied && editId == null) {
                    types.firstOrNull { it.id == initialServiceTypeId }?.let { type ->
                        initialServiceTypeApplied = true
                        openTypeItemEditor(type)
                    }
                }
            }
        }
        if (editId != null) {
            viewModelScope.launch { load(editId) }
        }
    }

    fun onEvent(event: ServiceFormEvent) {
        if (_uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.sourceInvalid || draftDiscarded) return
        when (event) {
            is ServiceFormEvent.VehicleChanged -> change { copy(vehicleId = event.value) }
            is ServiceFormEvent.DateChanged -> change { copy(date = event.value) }
            is ServiceFormEvent.TimeChanged -> change { copy(time = event.value) }
            is ServiceFormEvent.OdometerChanged -> change { copy(odometer = event.value) }
            is ServiceFormEvent.RecordTypeChanged -> change { copy(recordType = event.value) }
            is ServiceFormEvent.TitleChanged -> change { copy(title = event.value) }
            is ServiceFormEvent.NoteChanged -> change { copy(note = event.value) }
            is ServiceFormEvent.AddTypeItem -> {
                change { copy(isItemPickerVisible = false) }
                openTypeItemEditor(event.type)
            }
            is ServiceFormEvent.EditItem -> openExistingItemEditor(event.draftKey)
            is ServiceFormEvent.RemoveItem -> change {
                copy(
                    items = items.filterNot { it.draftKey == event.draftKey },
                    editingItemKey = editingItemKey.takeUnless { it == event.draftKey },
                    itemEditorDraft = itemEditorDraft?.takeUnless { it.draftKey == event.draftKey },
                )
            }
            is ServiceFormEvent.ItemNameChanged -> updateEditorItem(event.draftKey) { copy(name = event.value) }
            is ServiceFormEvent.ItemAmountChanged -> updateEditorItem(event.draftKey) { copy(amount = event.value) }
            is ServiceFormEvent.ItemNoteChanged -> updateEditorItem(event.draftKey) { copy(note = event.value) }
            is ServiceFormEvent.CustomItemNameChanged -> change { copy(customItemName = event.value) }
            is ServiceFormEvent.SaveCustomItemAsTypeChanged -> change { copy(saveCustomItemAsType = event.value) }
            is ServiceFormEvent.AttachmentsSelected -> importAttachments(event.uriStrings)
            is ServiceFormEvent.RemoveAttachment -> removeAttachment(event.relativePath)
            ServiceFormEvent.OpenCustomItemDialog -> change { copy(isCustomItemDialogVisible = true) }
            ServiceFormEvent.OpenItemPicker -> change { copy(isItemPickerVisible = true) }
            ServiceFormEvent.CloseItemPicker -> change { copy(isItemPickerVisible = false) }
            ServiceFormEvent.CloseCustomItemDialog -> change {
                copy(
                    isCustomItemDialogVisible = false,
                    customItemName = "",
                    saveCustomItemAsType = false,
                )
            }
            ServiceFormEvent.AddCustomItem -> addCustomItem()
            ServiceFormEvent.DismissItemFeedback -> change { copy(itemFeedback = null) }
            ServiceFormEvent.CloseItemEditor -> change { copy(editingItemKey = null, itemEditorDraft = null) }
            ServiceFormEvent.CompleteItemEditor -> completeItemEditor()
            ServiceFormEvent.Save -> save()
        }
    }

    fun discardUnsavedAttachments(onFinished: () -> Unit) {
        if (_uiState.value.isSaving || _uiState.value.isImportingAttachments) return
        _uiState.update { it.copy(isImportingAttachments = true) }
        viewModelScope.launch {
            runCatching {
                withContext(NonCancellable) {
                    serviceRepository.discardServiceDraft(draftId)
                    discardDraft()
                }
            }.onSuccess {
                    _uiState.update { it.copy(isImportingAttachments = false, attachments = it.attachments.filterNot { attachment -> attachment.isStaged }) }
                    onFinished()
                }.onFailure {
                    if (it is CancellationException) throw it
                    _uiState.update { it.copy(isImportingAttachments = false, error = ServiceFormError.ATTACHMENT_CLEANUP_FAILED) }
                }
        }
    }

    private suspend fun invalidateSource(error: ServiceFormError) {
        sourceReady = false
        _uiState.update { it.copy(isLoading = false, sourceInvalid = true, error = error) }
        runCatching {
            withContext(NonCancellable) {
                attachmentImportJob?.cancelAndJoin()
                serviceRepository.discardServiceDraft(draftId)
                discardDraft()
            }
        }
            .onFailure {
                if (it is CancellationException) throw it
                _uiState.update { it.copy(error = ServiceFormError.ATTACHMENT_CLEANUP_FAILED) }
            }
    }

    private suspend fun discardDraft() {
        draftDiscarded = true
        try {
            withContext(NonCancellable) { draftPersistence.discard() }
        } catch (failure: Exception) {
            draftDiscarded = false
            throw failure
        }
    }

    private fun persistDraft() {
        if (!draftDiscarded && sourceReady && garageReady && attachmentsReady) {
            draftPersistence.save(Json.encodeToString(
                _uiState.value.toDraft(draftId, sourcePublicId, preservedPaymentMethod, initialServiceTypeApplied),
            ))
        }
    }

    suspend fun readAttachmentBytes(relativePath: String): ByteArray? =
        runCatching { serviceRepository.readServiceAttachmentBytes(relativePath) }.getOrNull()

    private suspend fun load(id: Long) {
        val details = serviceRepository.getServiceRecord(id)
        if (details == null || (restoredDraft != null && details.record.publicId != sourcePublicId)) {
            invalidateSource(ServiceFormError.RECORD_NOT_FOUND)
            return
        }
        val recordVehicle = vehicleRepository.getVehicle(restoredDraft?.vehicleId ?: details.record.vehicleId)
        if (recordVehicle == null || (restoredDraft?.selectedVehiclePublicId != null &&
                recordVehicle.publicId != restoredDraft?.selectedVehiclePublicId)) {
            invalidateSource(ServiceFormError.VEHICLE_NOT_FOUND)
            return
        }
        sourceReady = true
        sourcePublicId = details.record.publicId
        if (restoredDraft != null) {
            _uiState.update { it.copy(vehicles = (it.vehicles + recordVehicle).distinctBy { vehicle -> vehicle.id }, isLoading = !garageReady || !attachmentsReady) }
            persistDraft()
            return
        }
        _uiState.update { state ->
            state.copy(
                vehicles = (state.vehicles + listOfNotNull(recordVehicle)).distinctBy { it.id },
                vehicleId = details.record.vehicleId,
                date = LocalDate.ofEpochDay(details.record.dateEpochDay).toString(),
                time = details.record.timeMinuteOfDay?.let(::minuteOfDayToText).orEmpty(),
                odometer = details.record.odometerKm.toString(),
                recordType = details.record.recordType,
                title = details.record.title,
                note = details.record.note.orEmpty(),
                items = details.items.map { item ->
                    ServiceItemDraft(
                        id = item.id,
                        serviceTypeId = item.serviceTypeId,
                        name = item.nameSnapshot,
                        amount = item.subtotalTwd.toString(),
                        legacyNextDueOdometerKm = item.nextDueOdometerKm,
                        legacyNextDueDateEpochDay = item.nextDueDateEpochDay,
                        note = item.note.orEmpty(),
                    )
                },
                attachments = details.attachments.map { attachment ->
                    ServiceAttachmentDraft(
                        id = attachment.id,
                        relativePath = attachment.relativePath,
                        displayName = attachment.displayName,
                        mimeType = attachment.mimeType,
                        isStaged = false,
                    )
                },
                isLoading = false,
            )
        }
        preservedPaymentMethod = details.record.paymentMethod
        persistDraft()
    }

    private fun openTypeItemEditor(type: com.kumo.bubu.domain.model.ServiceType) {
        val existing = _uiState.value.items.firstOrNull { item ->
            item.serviceTypeId == type.id || item.name.trim().equals(type.name.trim(), ignoreCase = true)
        }
        if (existing != null) {
            openExistingItemEditor(existing.draftKey)
            return
        }
        val draft = ServiceItemDraft(serviceTypeId = type.id, name = type.name)
        change { copy(editingItemKey = draft.draftKey, itemEditorDraft = draft) }
    }

    private fun openExistingItemEditor(draftKey: Long) {
        val item = _uiState.value.items.firstOrNull { it.draftKey == draftKey } ?: return
        change { copy(editingItemKey = draftKey, itemEditorDraft = item) }
    }

    private fun completeItemEditor() {
        val draft = _uiState.value.itemEditorDraft ?: return
        if (draft.name.isBlank()) {
            _uiState.update { it.copy(error = ServiceFormError.ITEM_NAME_REQUIRED) }
            return
        }
        if (draft.amountTwd == null) {
            _uiState.update { it.copy(error = ServiceFormError.ITEM_AMOUNT_INVALID) }
            return
        }
        change {
            val exists = items.any { it.draftKey == draft.draftKey }
            copy(
                items = if (exists) {
                    items.map { item -> if (item.draftKey == draft.draftKey) draft else item }
                } else {
                    items + draft
                },
                editingItemKey = null,
                itemEditorDraft = null,
                itemFeedback = if (exists) null else ServiceItemFeedback.ADDED,
            )
        }
    }

    private fun addCustomItem() {
        val state = _uiState.value
        val name = state.customItemName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = ServiceFormError.CUSTOM_TYPE_NAME_REQUIRED) }
            return
        }
        val existing = state.items.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
        if (existing != null) {
            change {
                copy(
                    isCustomItemDialogVisible = false,
                    customItemName = "",
                    saveCustomItemAsType = false,
                    editingItemKey = existing.draftKey,
                    itemEditorDraft = existing,
                )
            }
            return
        }
        if (!state.saveCustomItemAsType) {
            val draft = ServiceItemDraft(name = name)
            change {
                copy(
                    isCustomItemDialogVisible = false,
                    customItemName = "",
                    saveCustomItemAsType = false,
                    editingItemKey = draft.draftKey,
                    itemEditorDraft = draft,
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                serviceRepository.createServiceType(
                    ServiceTypeInput(
                        name = name,
                        vehicleType = state.selectedVehicle?.vehicleType ?: VehicleType.CAR,
                    ),
                )
            }
                .onSuccess { typeId ->
                    val draft = ServiceItemDraft(serviceTypeId = typeId, name = name)
                    change {
                        copy(
                            isCustomItemDialogVisible = false,
                            customItemName = "",
                            saveCustomItemAsType = false,
                            editingItemKey = draft.draftKey,
                            itemEditorDraft = draft,
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(error = ServiceFormError.TYPE_SAVE_FAILED) } }
        }
    }

    private fun importAttachments(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        val state = _uiState.value
        if (state.attachments.size + uriStrings.size > MAX_SERVICE_ATTACHMENTS_PER_RECORD) {
            _uiState.update { it.copy(error = ServiceFormError.ATTACHMENT_LIMIT_EXCEEDED) }
            return
        }
        if (state.isImportingAttachments) return
        _uiState.update { it.copy(isImportingAttachments = true, error = null) }
        attachmentImportJob = viewModelScope.launch {
            try {
                val staged = serviceRepository.stageServiceAttachments(uriStrings, draftId)
                if (draftDiscarded || _uiState.value.sourceInvalid) {
                    withContext(NonCancellable) { serviceRepository.discardServiceDraft(draftId) }
                    return@launch
                }
                _uiState.update { current ->
                    current.copy(
                        attachments = current.attachments + staged.map { attachment ->
                            ServiceAttachmentDraft(
                                relativePath = attachment.relativePath,
                                displayName = attachment.displayName,
                                mimeType = attachment.mimeType,
                                isStaged = true,
                            )
                        },
                    )
                }
                persistDraft()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { current ->
                    if (current.sourceInvalid) current else current.copy(error = failure.toAttachmentError())
                }
            } finally {
                _uiState.update { it.copy(isImportingAttachments = false) }
            }
        }
    }

    private fun removeAttachment(relativePath: String) {
        if (_uiState.value.isImportingAttachments) return
        val attachment = _uiState.value.attachments.firstOrNull { it.relativePath == relativePath } ?: return
        if (!attachment.isStaged) {
            change { copy(attachments = attachments.filterNot { it.relativePath == relativePath }) }
        } else {
            _uiState.update { it.copy(isImportingAttachments = true) }
            viewModelScope.launch {
                runCatching { serviceRepository.discardStagedServiceAttachment(relativePath, draftId) }
                    .onSuccess { change { copy(isImportingAttachments = false, attachments = attachments.filterNot { it.relativePath == relativePath }) } }
                    .onFailure {
                        if (it is CancellationException) throw it
                        _uiState.update { it.copy(isImportingAttachments = false, error = ServiceFormError.ATTACHMENT_CLEANUP_FAILED) }
                    }
            }
        }
    }

    private fun save() {
        if (_uiState.value.isImportingAttachments) return
        if (_uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.sourceInvalid || draftDiscarded) return
        val state = _uiState.value
        val input = state.toValidatedInput() ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (state.editingId == null) {
                    serviceRepository.createServiceRecord(input, draftId)
                } else {
                    serviceRepository.updateServiceRecord(state.editingId, input)
                }
            }.onSuccess {
                withContext(NonCancellable) {
                    runCatching { serviceRepository.releaseServiceDraft(draftId) }
                        .onFailure { Logger.getLogger(ServiceFormViewModel::class.java.name).log(Level.WARNING, "Saved attachment draft ownership will be cleaned on startup.", it) }
                }
                runCatching { discardDraft() }.onFailure {
                    Logger.getLogger(ServiceFormViewModel::class.java.name)
                        .log(Level.WARNING, "Committed service draft cleanup will retry when restored.", it)
                }
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        attachments = current.attachments.map { attachment ->
                            attachment.copy(isStaged = false)
                        },
                    )
                }
                saved.send(Unit)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isSaving = false, error = throwable.toSaveError())
                }
            }
        }
    }

    private fun ServiceFormUiState.toValidatedInput(): ServiceRecordInput? {
        val selectedVehicleId = vehicleId ?: return fail(ServiceFormError.VEHICLE_REQUIRED)
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
            ?: return fail(ServiceFormError.INVALID_DATE)
        if (parsedDate.isAfter(LocalDate.now())) return fail(ServiceFormError.FUTURE_DATE)
        val parsedTime = if (time.isBlank()) {
            null
        } else {
            runCatching { LocalTime.parse(time) }.getOrNull()
                ?: return fail(ServiceFormError.INVALID_TIME)
        }
        val parsedOdometer = odometer.toLongOrNull()?.takeIf { it >= 0 }
            ?: return fail(ServiceFormError.INVALID_ODOMETER)
        if (title.isBlank()) return fail(ServiceFormError.TITLE_REQUIRED)
        if (items.isEmpty()) return fail(ServiceFormError.ITEM_REQUIRED)

        val parsedItems = mutableListOf<ServiceItemInput>()
        items.forEach { item ->
            if (item.name.isBlank()) return fail(ServiceFormError.ITEM_NAME_REQUIRED)
            val amountTwd = item.amountTwd ?: return fail(ServiceFormError.ITEM_AMOUNT_INVALID)
            parsedItems += ServiceItemInput(
                id = item.id,
                serviceTypeId = item.serviceTypeId,
                nameSnapshot = item.name,
                quantityMilli = 1_000L,
                quantityUnit = ServiceQuantityUnit.PIECE,
                unitPriceTwd = amountTwd,
                subtotalTwd = amountTwd,
                nextDueOdometerKm = item.legacyNextDueOdometerKm,
                nextDueDateEpochDay = item.legacyNextDueDateEpochDay,
                note = item.note,
            )
        }
        val total = runCatching {
            parsedItems.fold(0L) { sum, item -> Math.addExact(sum, item.subtotalTwd) }
        }.getOrElse { return fail(ServiceFormError.AMOUNT_OVERFLOW) }
        return ServiceRecordInput(
            vehicleId = selectedVehicleId,
            dateEpochDay = parsedDate.toEpochDay(),
            timeMinuteOfDay = parsedTime?.toSecondOfDay()?.div(60),
            odometerKm = parsedOdometer,
            recordType = recordType,
            title = title,
            paymentMethod = preservedPaymentMethod,
            totalCostTwd = total,
            note = note,
            items = parsedItems,
            attachments = attachments.map { attachment ->
                ServiceAttachmentInput(
                    id = attachment.id,
                    relativePath = attachment.relativePath,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                    isStaged = attachment.isStaged,
                )
            },
        )
    }

    private fun fail(error: ServiceFormError): Nothing? {
        _uiState.update { it.copy(error = error) }
        return null
    }

    private fun updateEditorItem(draftKey: Long, update: ServiceItemDraft.() -> ServiceItemDraft) {
        change {
            val editorDraft = itemEditorDraft ?: return@change this
            if (editorDraft.draftKey != draftKey) return@change this
            copy(itemEditorDraft = editorDraft.update())
        }
    }

    private fun change(block: ServiceFormUiState.() -> ServiceFormUiState) {
        _uiState.update { it.block().copy(error = null) }
        persistDraft()
    }

    private fun Throwable.toAttachmentError(): ServiceFormError = when (this) {
        is ServiceAttachmentException.TooMany -> ServiceFormError.ATTACHMENT_LIMIT_EXCEEDED
        is ServiceAttachmentException.Unsupported -> ServiceFormError.ATTACHMENT_UNSUPPORTED
        is ServiceAttachmentException.TooLarge -> ServiceFormError.ATTACHMENT_TOO_LARGE
        else -> ServiceFormError.ATTACHMENT_COPY_FAILED
    }

    private fun Throwable.toSaveError(): ServiceFormError = when (this) {
        is ServiceRecordException.VehicleArchived -> ServiceFormError.VEHICLE_ARCHIVED
        is ServiceRecordException.VehicleNotFound -> ServiceFormError.VEHICLE_NOT_FOUND
        is ServiceRecordException.RecordNotFound -> ServiceFormError.RECORD_NOT_FOUND
        is ServiceRecordException.WriteFailed -> when (stage) {
            ServiceWriteStage.RECORD -> ServiceFormError.RECORD_WRITE_FAILED
            ServiceWriteStage.ITEMS -> ServiceFormError.ITEM_WRITE_FAILED
            ServiceWriteStage.REMINDERS -> ServiceFormError.REMINDER_WRITE_FAILED
            ServiceWriteStage.ATTACHMENTS -> ServiceFormError.ATTACHMENT_LINK_FAILED
            ServiceWriteStage.ODOMETER -> ServiceFormError.ODOMETER_WRITE_FAILED
        }
        else -> ServiceFormError.SAVE_FAILED
    }

    companion object {
        private const val DRAFT_KEY = "service.form.draft.v1"

        fun factory(
            serviceRepository: ServiceRepository,
            vehicleRepository: VehicleRepository,
            editId: Long? = null,
            initialVehicleId: Long? = null,
            initialServiceTypeId: Long? = null,
            draftRepository: FormDraftRepository? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ServiceFormViewModel(serviceRepository, vehicleRepository, editId, initialVehicleId,
                    initialServiceTypeId, savedStateHandle = createSavedStateHandle(), draftRepository = draftRepository)
            }
        }
    }
}

private fun minuteOfDayToText(minuteOfDay: Int): String =
    LocalTime.ofSecondOfDay(minuteOfDay * 60L).toString()
