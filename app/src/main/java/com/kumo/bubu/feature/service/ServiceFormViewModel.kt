package com.kumo.bubu.feature.service

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

class ServiceFormViewModel(
    private val serviceRepository: ServiceRepository,
    private val vehicleRepository: VehicleRepository,
    private val editId: Long? = null,
    private val initialVehicleId: Long? = null,
    nowProvider: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val initialNow = nowProvider()
    private val _uiState = MutableStateFlow(
        ServiceFormUiState(
            date = initialNow.toLocalDate().toString(),
            time = initialNow.toLocalTime().withSecond(0).withNano(0).toString(),
            editingId = editId,
        ),
    )
    val uiState: StateFlow<ServiceFormUiState> = _uiState.asStateFlow()

    private val saved = Channel<Unit>(Channel.BUFFERED)
    val savedEffects = saved.receiveAsFlow()
    private var preservedPaymentMethod: PaymentMethod? = null

    init {
        viewModelScope.launch { serviceRepository.ensureDefaultServiceTypes() }
        viewModelScope.launch {
            combine(
                vehicleRepository.observeGarage(),
                serviceRepository.observeServiceTypes(),
            ) { garage, types -> garage to types }.collect { (garage, types) ->
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
                        isLoading = false,
                    )
                }
            }
        }
        if (editId != null) {
            viewModelScope.launch { load(editId) }
        }
    }

    fun onEvent(event: ServiceFormEvent) {
        if (_uiState.value.isSaving) return
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
        val stagedPaths = _uiState.value.attachments.filter { it.isStaged }.map { it.relativePath }
        if (stagedPaths.isEmpty()) {
            onFinished()
            return
        }
        viewModelScope.launch {
            val cleanupFailed = stagedPaths.any { path ->
                runCatching { serviceRepository.discardStagedServiceAttachment(path) }.isFailure
            }
            if (cleanupFailed) {
                _uiState.update { it.copy(error = ServiceFormError.ATTACHMENT_CLEANUP_FAILED) }
            } else {
                onFinished()
            }
        }
    }

    suspend fun readAttachmentBytes(relativePath: String): ByteArray? =
        runCatching { serviceRepository.readServiceAttachmentBytes(relativePath) }.getOrNull()

    private suspend fun load(id: Long) {
        val details = serviceRepository.getServiceRecord(id) ?: run {
            _uiState.update { it.copy(isLoading = false, error = ServiceFormError.RECORD_NOT_FOUND) }
            return
        }
        val recordVehicle = vehicleRepository.getVehicle(details.record.vehicleId)
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
        viewModelScope.launch {
            runCatching { serviceRepository.stageServiceAttachments(uriStrings) }
                .onSuccess { staged ->
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
                            isImportingAttachments = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isImportingAttachments = false, error = throwable.toAttachmentError())
                    }
                }
        }
    }

    private fun removeAttachment(relativePath: String) {
        val attachment = _uiState.value.attachments.firstOrNull { it.relativePath == relativePath } ?: return
        change { copy(attachments = attachments.filterNot { it.relativePath == relativePath }) }
        if (attachment.isStaged) {
            viewModelScope.launch { runCatching { serviceRepository.discardStagedServiceAttachment(relativePath) } }
        }
    }

    private fun save() {
        if (_uiState.value.isSaving) return
        val state = _uiState.value
        val input = state.toValidatedInput() ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (state.editingId == null) {
                    serviceRepository.createServiceRecord(input)
                } else {
                    serviceRepository.updateServiceRecord(state.editingId, input)
                }
            }.onSuccess {
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
        fun factory(
            serviceRepository: ServiceRepository,
            vehicleRepository: VehicleRepository,
            editId: Long? = null,
            initialVehicleId: Long? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ServiceFormViewModel(serviceRepository, vehicleRepository, editId, initialVehicleId) }
        }
    }
}

private fun minuteOfDayToText(minuteOfDay: Int): String =
    LocalTime.ofSecondOfDay(minuteOfDay * 60L).toString()
