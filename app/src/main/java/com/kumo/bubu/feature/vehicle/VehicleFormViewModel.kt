package com.kumo.bubu.feature.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.VehicleRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.kumo.bubu.core.state.FormDraftPersistence
import com.kumo.bubu.domain.repository.FormDraftRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class VehicleFormViewModel(
    private val repository: VehicleRepository,
    private val vehicleId: Long? = null,
    savedStateHandle: SavedStateHandle,
    draftRepository: FormDraftRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VehicleFormUiState(vehicleId = vehicleId))
    val uiState: StateFlow<VehicleFormUiState> = _uiState.asStateFlow()
    private val draftPersistence = FormDraftPersistence(savedStateHandle, DRAFT_KEY, draftRepository, viewModelScope) { pending, failed ->
        _uiState.update { it.copy(isDraftSaving = pending, draftSaveFailed = failed) }
    }
    private var restoredDraft = decodeDraft(draftPersistence.inlineContents())
    private val draftId = draftPersistence.draftId
    private var sourcePublicId = restoredDraft?.sourcePublicId
    private var draftDiscarded = false

    private val effectChannel = Channel<VehicleFormEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        if (draftPersistence.isFileBacked) {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                try {
                    if (vehicleId == null && repository.findVehicleIdByPublicId(draftId) != null) {
                        clearDraft()
                        effectChannel.send(VehicleFormEffect.Saved)
                    } else {
                        restoredDraft = decodeDraft(draftPersistence.readFile())
                        initializeDraft()
                    }
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    draftPersistence.recordRestoreFailure(failure)
                    _uiState.update { it.copy(isLoading = false, loadFailed = true, draftRestoreFailed = true) }
                }
            }
        } else initializeDraft()
    }

    private fun decodeDraft(contents: String?): VehicleFormDraft? {
        if (contents == null) return null
        return try {
            Json.decodeFromString<VehicleFormDraft>(contents).also {
                require(it.sourceId == vehicleId && it.draftId == draftPersistence.draftId)
            }
        } catch (failure: Exception) {
            draftPersistence.recordRestoreFailure(failure)
            null
        }
    }

    private fun initializeDraft() {
        if (draftPersistence.restoreFailed) {
            _uiState.update { it.copy(isLoading = false, loadFailed = true, draftRestoreFailed = true) }
            return
        }
        sourcePublicId = restoredDraft?.sourcePublicId
        _uiState.value = (restoredDraft?.toUiState() ?: VehicleFormUiState(vehicleId = vehicleId))
            .copy(isLoading = vehicleId != null || restoredDraft != null)
        if (vehicleId != null) loadVehicle(vehicleId)
        else if (restoredDraft != null) viewModelScope.launch {
            runCatching { repository.findVehicleIdByPublicId(draftId) }
                .onSuccess { committedId ->
                    if (committedId != null) {
                        clearDraft()
                        effectChannel.send(VehicleFormEffect.Saved)
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                        persistDraft()
                    }
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    _uiState.update { state -> state.copy(isLoading = false, loadFailed = true) }
                }
        } else persistDraft()
    }

    fun onEvent(event: VehicleFormEvent) {
        when (event) {
            is VehicleFormEvent.NameChanged -> updateField { copy(name = event.value) }
            is VehicleFormEvent.VehicleTypeChanged -> updateField {
                copy(
                    vehicleType = event.value,
                    motorcycleClass = motorcycleClass.takeIf { event.value == VehicleType.MOTORCYCLE },
                )
            }
            is VehicleFormEvent.MotorcycleClassChanged -> updateField { copy(motorcycleClass = event.value) }
            is VehicleFormEvent.BrandChanged -> updateField { copy(brand = event.value) }
            is VehicleFormEvent.ModelChanged -> updateField { copy(model = event.value) }
            is VehicleFormEvent.ManufactureYearChanged -> updateField { copy(manufactureYear = event.value) }
            is VehicleFormEvent.EngineDisplacementChanged -> updateField { copy(engineDisplacementCc = event.value) }
            is VehicleFormEvent.LicensePlateChanged -> updateField { copy(licensePlate = event.value) }
            is VehicleFormEvent.PowertrainTypeChanged -> updateField { copy(powertrainType = event.value) }
            is VehicleFormEvent.TrackingStartDateChanged -> updateField { copy(trackingStartDate = event.value) }
            is VehicleFormEvent.TrackingStartOdometerChanged -> updateField { copy(trackingStartOdometerKm = event.value) }
            is VehicleFormEvent.NoteChanged -> updateField { copy(note = event.value) }
            is VehicleFormEvent.PrimaryInspectionMonthDayChanged -> updateField {
                copy(primaryInspectionMonthDay = event.value)
            }
            is VehicleFormEvent.SecondaryInspectionMonthDayChanged -> updateField {
                copy(secondaryInspectionMonthDay = event.value)
            }
            VehicleFormEvent.Save -> save()
        }
    }

    private fun loadVehicle(id: Long) = viewModelScope.launch {
        runCatching { repository.getVehicle(id) }
            .onSuccess { vehicle ->
                if (vehicle == null || (restoredDraft != null && vehicle.publicId != sourcePublicId)) {
                    discardDraft()
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                } else {
                    sourcePublicId = vehicle.publicId
                    _uiState.value = restoredDraft?.toUiState() ?: vehicle.toFormUiState()
                    persistDraft()
                }
            }
            .onFailure { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
    }

    private fun updateField(transform: VehicleFormUiState.() -> VehicleFormUiState) {
        if (_uiState.value.isLoading || _uiState.value.loadFailed || draftDiscarded) return
        _uiState.update { it.transform().copy(errors = emptyMap(), saveFailed = false) }
        persistDraft()
    }

    fun discardDraft(onFinished: () -> Unit = {}) {
        if (draftDiscarded) return
        draftDiscarded = true
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                clearDraft()
                onFinished()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                draftDiscarded = false
                _uiState.update { it.copy(draftSaveFailed = true) }
            }
        }
    }

    private suspend fun clearDraft() {
        draftDiscarded = true
        withContext(NonCancellable) { draftPersistence.discard() }
    }

    private fun persistDraft() {
        if (!draftDiscarded) {
            draftPersistence.save(Json.encodeToString(_uiState.value.toDraft(draftId, sourcePublicId)))
        }
    }

    private fun save() {
        if (_uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.loadFailed || draftDiscarded) return
        val validation = _uiState.value.validate()
        val input = validation.input
        if (input == null) {
            _uiState.update { it.copy(errors = validation.errors) }
            return
        }

        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                vehicleId?.let { repository.updateVehicle(it, input) }
                    ?: repository.createVehicle(input, draftId)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                java.util.logging.Logger.getLogger(VehicleFormViewModel::class.java.name)
                    .log(java.util.logging.Level.WARNING, "Vehicle save failed; checking whether creation committed.", failure)
                val committed = if (vehicleId == null) {
                    try {
                        repository.findVehicleIdByPublicId(draftId) != null
                    } catch (lookupFailure: Exception) {
                        if (lookupFailure is kotlinx.coroutines.CancellationException) throw lookupFailure
                        java.util.logging.Logger.getLogger(VehicleFormViewModel::class.java.name)
                            .log(java.util.logging.Level.WARNING, "Unable to check vehicle creation outcome.", lookupFailure)
                        _uiState.update { it.copy(isSaving = false, saveFailed = true, loadFailed = true) }
                        return@launch
                    }
                } else false
                if (!committed) {
                    _uiState.update { it.copy(isSaving = false, saveFailed = true) }
                    return@launch
                }
            }
            runCatching { clearDraft() }.onFailure {
                java.util.logging.Logger.getLogger(VehicleFormViewModel::class.java.name)
                    .log(java.util.logging.Level.WARNING, "Committed vehicle draft cleanup will retry when restored.", it)
            }
            _uiState.update { state -> state.copy(isSaving = false) }
            effectChannel.send(VehicleFormEffect.Saved)
        }
    }

    companion object {
        private const val DRAFT_KEY = "vehicle.form.draft.v1"

        fun factory(
            repository: VehicleRepository,
            vehicleId: Long? = null,
            draftRepository: FormDraftRepository? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { VehicleFormViewModel(repository, vehicleId, createSavedStateHandle(), draftRepository) }
        }
    }
}
