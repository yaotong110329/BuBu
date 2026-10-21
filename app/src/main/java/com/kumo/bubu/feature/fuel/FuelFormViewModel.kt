package com.kumo.bubu.feature.fuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.applyToCpcListPrice
import com.kumo.bubu.domain.model.detectFuelEconomyOutlier
import com.kumo.bubu.domain.model.toScaledDecimalText
import com.kumo.bubu.domain.repository.FuelPriceRepository
import com.kumo.bubu.domain.repository.FuelRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.kumo.bubu.core.state.FormDraftPersistence
import com.kumo.bubu.domain.repository.FormDraftRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class FuelFormViewModel(
    private val fuelRepository: FuelRepository,
    private val fuelPriceRepository: FuelPriceRepository,
    private val vehicleRepository: VehicleRepository,
    private val fuelRecordId: Long? = null,
    private val initialVehicleId: Long? = null,
    savedStateHandle: SavedStateHandle,
    draftRepository: FormDraftRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FuelFormUiState(fuelRecordId = fuelRecordId))
    val uiState: StateFlow<FuelFormUiState> = _uiState.asStateFlow()
    private val draftPersistence = FormDraftPersistence(savedStateHandle, DRAFT_KEY, draftRepository, viewModelScope) { pending, failed ->
        _uiState.update { it.copy(isDraftSaving = pending, draftSaveFailed = failed) }
    }
    private var restoredDraft = decodeDraft(draftPersistence.inlineContents())
    private val draftId = draftPersistence.draftId
    private var sourcePublicId = restoredDraft?.sourcePublicId
    private var sourceReady = fuelRecordId == null && restoredDraft == null && !draftPersistence.isFileBacked
    private var garageReady = false
    private var draftDiscarded = false
    private val effectChannel = Channel<FuelFormEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        if (draftPersistence.isFileBacked) viewModelScope.launch {
            try {
                if (fuelRecordId == null && fuelRepository.findFuelRecordIdByPublicId(draftId) != null) {
                    clearDraft()
                    effectChannel.send(FuelFormEffect.Saved)
                } else {
                    restoredDraft = decodeDraft(draftPersistence.readFile())
                    initializeDraft()
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                draftPersistence.recordRestoreFailure(failure)
                _uiState.update { it.copy(isLoading = false, loadFailed = true, draftRestoreFailed = true) }
            }
        } else initializeDraft()
    }

    private fun decodeDraft(contents: String?): FuelFormDraft? {
        if (contents == null) return null
        return try {
            Json.decodeFromString<FuelFormDraft>(contents).also {
                require(it.sourceId == fuelRecordId && it.draftId == draftPersistence.draftId)
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
        sourceReady = fuelRecordId == null && restoredDraft == null
        _uiState.value = restoredDraft?.toUiState() ?: FuelFormUiState(fuelRecordId = fuelRecordId)
        if (fuelRecordId != null) loadFuelRecord(fuelRecordId)
        else if (restoredDraft != null) viewModelScope.launch {
            runCatching { fuelRepository.findFuelRecordIdByPublicId(draftId) }
                .onSuccess { committedId ->
                    if (committedId != null) {
                        clearDraft()
                        effectChannel.send(FuelFormEffect.Saved)
                    } else {
                        sourceReady = true
                        _uiState.update { it.copy(isLoading = !garageReady) }
                        persistDraft()
                    }
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    _uiState.update { state -> state.copy(isLoading = false, loadFailed = true) }
                }
        }
        viewModelScope.launch {
            vehicleRepository.observeGarage().collect { garage ->
                garageReady = true
                val previous = _uiState.value
                val expectedVehiclePublicId = previous.selectedVehicle?.publicId ?: restoredDraft?.selectedVehiclePublicId
                if (previous.selectedVehicleId != null && expectedVehiclePublicId != null && !draftDiscarded &&
                    garage.vehicles.none { it.id == previous.selectedVehicleId && it.publicId == expectedVehiclePublicId }
                ) {
                    discardDraft()
                    _uiState.update { it.copy(activeVehicles = garage.vehicles, isLoading = false, loadFailed = true) }
                    return@collect
                }
                val selectedVehicleId = previous.selectedVehicleId?.takeIf { selected -> garage.vehicles.any { it.id == selected } }
                    ?: initialVehicleId?.takeIf { selected -> garage.vehicles.any { it.id == selected && !it.isArchived } }
                    ?: garage.currentVehiclePublicId?.let { current -> garage.vehicles.firstOrNull { it.publicId == current }?.id }
                    ?: garage.vehicles.firstOrNull { !it.isArchived }?.id
                val activeVehicles = garage.vehicles.filter { !it.isArchived || it.id == selectedVehicleId }
                _uiState.update { state -> state.copy(activeVehicles = activeVehicles, selectedVehicleId = selectedVehicleId, isLoading = !sourceReady) }
                if (sourceReady && !_uiState.value.loadFailed) persistDraft()
                if (restoredDraft == null && !previous.isEditing && previous.selectedVehicleId == null && selectedVehicleId != null) loadVehicleDefaults(selectedVehicleId)
            }
        }
    }

    fun onEvent(event: FuelFormEvent) {
        if (_uiState.value.isLoading || _uiState.value.loadFailed || draftDiscarded) return
        when (event) {
            is FuelFormEvent.VehicleChanged -> selectVehicle(event.vehicleId)
            is FuelFormEvent.DateChanged -> updateField { copy(date = event.value, odometerOrderReason = "") }
            is FuelFormEvent.TimeChanged -> updateField { copy(time = event.value, odometerOrderReason = "") }
            is FuelFormEvent.OdometerChanged -> updateField { copy(odometerKm = event.value, odometerOrderReason = "") }
            is FuelFormEvent.VolumeChanged -> updateField { withUserCalculationValue(FuelCalculationField.VOLUME, event.value) }
            is FuelFormEvent.PriceChanged -> updateField { withUserValue(FuelCalculationField.PRICE, event.value).copy(priceSource = FuelPriceSource.MANUAL, priceEffectiveDateEpochDay = null) }
            is FuelFormEvent.TotalCostChanged -> updateField { withUserCalculationValue(FuelCalculationField.TOTAL_COST, event.value) }
            is FuelFormEvent.FullTankChanged -> updateField { copy(isFullTank = event.value) }
            is FuelFormEvent.FuelProductChanged -> {
                updateField { copy(fuelProduct = event.value, priceSource = FuelPriceSource.NONE, priceEffectiveDateEpochDay = null) }
                refreshPriceIfNewRecord()
            }
            is FuelFormEvent.FuelingModeChanged -> {
                updateField { copy(fuelingMode = event.value, priceSource = FuelPriceSource.NONE, priceEffectiveDateEpochDay = null) }
                refreshPriceIfNewRecord()
            }
            is FuelFormEvent.FuelEconomyStatisticsStatusChanged -> updateField {
                copy(fuelEconomyStatisticsStatus = event.value)
            }
            FuelFormEvent.RefreshPrice -> refreshPriceIfNewRecord()
            is FuelFormEvent.NoteChanged -> updateField { copy(note = event.value) }
            is FuelFormEvent.OdometerOrderReasonChanged -> updateField { copy(odometerOrderReason = event.value, odometerOrderReasonRequired = false) }
            FuelFormEvent.ConfirmOdometerOrder -> confirmOdometerOrder()
            FuelFormEvent.DismissOdometerOrder -> updateField { copy(odometerOrderWarning = null, odometerOrderReason = "", odometerOrderReasonRequired = false, pendingSaveInput = null) }
            FuelFormEvent.ConfirmFuelEconomyOutlier -> confirmFuelEconomyOutlier()
            FuelFormEvent.ExcludeFuelEconomyOutlier -> excludeFuelEconomyOutlier()
            FuelFormEvent.DismissFuelEconomyOutlier -> _uiState.update { it.copy(fuelEconomyOutlier = null, pendingSaveInput = null) }
            FuelFormEvent.Save -> save()
        }
    }

    private fun updateField(transform: FuelFormUiState.() -> FuelFormUiState) {
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
        if (!draftDiscarded && sourceReady && garageReady) {
            draftPersistence.save(Json.encodeToString(_uiState.value.toDraft(draftId, sourcePublicId)))
        }
    }

    private fun selectVehicle(vehicleId: Long) {
        updateField { copy(selectedVehicleId = vehicleId, odometerOrderReason = "", fuelProduct = if (isEditing) fuelProduct else null, priceSource = FuelPriceSource.NONE, priceEffectiveDateEpochDay = null) }
        if (!uiState.value.isEditing) loadVehicleDefaults(vehicleId)
    }

    private fun loadVehicleDefaults(vehicleId: Long) = viewModelScope.launch {
        val publicId = _uiState.value.activeVehicles.firstOrNull { it.id == vehicleId }?.publicId ?: return@launch
        val fullTank = fuelRepository.getLastFullTankSetting(publicId)
        val product = fuelRepository.getLastFuelProduct(publicId)
        val fuelingMode = fuelRepository.getLastFuelingMode(publicId)
        _uiState.update { state ->
            if (state.selectedVehicleId == vehicleId && !state.isEditing) state.copy(
                isFullTank = fullTank ?: state.isFullTank,
                fuelProduct = product ?: state.fuelProduct,
                fuelingMode = fuelingMode ?: FuelingMode.FULL_SERVICE,
            ) else state
        }
        persistDraft()
        refreshPriceIfNewRecord()
    }

    private fun refreshPriceIfNewRecord() {
        val state = _uiState.value
        if (state.isEditing || draftDiscarded || state.loadFailed) return
        val vehicleId = state.selectedVehicleId ?: return
        val product = state.fuelProduct ?: return
        val fuelingMode = state.fuelingMode
        val date = runCatching { LocalDate.parse(state.date) }.getOrNull() ?: return
        _uiState.update { it.copy(priceSource = FuelPriceSource.LOADING, priceEffectiveDateEpochDay = null) }
        persistDraft()
        viewModelScope.launch {
            val quoteResult = runCatching { fuelPriceRepository.getCpcManualPrice(product, date) }
            val quote = quoteResult.getOrNull()
            if (!isPriceRequestCurrent(vehicleId, product, fuelingMode, date)) return@launch
            if (quote != null) {
                applyAutomaticPrice(
                    fuelingMode.applyToCpcListPrice(quote.pricePerLiterMilli).toScaledDecimalText(3),
                    FuelPriceSource.CPC_MANUAL,
                    quote.effectiveDateEpochDay,
                )
            } else {
                val lastPrice = runCatching { fuelRepository.getLastPriceForProduct(vehicleId, product, date.toEpochDay()) }.getOrNull()
                if (!isPriceRequestCurrent(vehicleId, product, fuelingMode, date)) return@launch
                if (lastPrice == null) {
                    _uiState.update {
                        it.copy(
                            priceSource = if (quoteResult.isFailure) FuelPriceSource.CPC_FETCH_FAILED else FuelPriceSource.UNAVAILABLE,
                            priceEffectiveDateEpochDay = null,
                        )
                    }
                } else {
                    applyAutomaticPrice(
                        lastPrice.toScaledDecimalText(3),
                        if (quoteResult.isFailure) FuelPriceSource.CPC_FETCH_FAILED_LAST_RECORD else FuelPriceSource.LAST_RECORD,
                        null,
                    )
                }
                persistDraft()
            }
        }
    }

    private fun isPriceRequestCurrent(vehicleId: Long, product: FuelProduct, fuelingMode: FuelingMode, date: LocalDate): Boolean =
        _uiState.value.let { state ->
            !state.isEditing && !draftDiscarded &&
                state.selectedVehicleId == vehicleId &&
                state.fuelProduct == product &&
                state.fuelingMode == fuelingMode &&
                state.date == date.toString() &&
                state.priceSource == FuelPriceSource.LOADING
        }

    private fun applyAutomaticPrice(value: String, source: FuelPriceSource, effectiveDateEpochDay: Long?) {
        _uiState.update { state ->
            state.withUserValue(FuelCalculationField.PRICE, value).copy(priceSource = source, priceEffectiveDateEpochDay = effectiveDateEpochDay)
        }
        persistDraft()
    }

    private fun loadFuelRecord(id: Long) = viewModelScope.launch {
        runCatching { fuelRepository.getFuelRecord(id) }.onSuccess { record ->
            if (record == null || (restoredDraft != null && record.publicId != sourcePublicId)) {
                discardDraft()
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@onSuccess
            }
            val selectedVehicle = vehicleRepository.getVehicle(restoredDraft?.selectedVehicleId ?: record.vehicleId)
            if (selectedVehicle == null || (restoredDraft?.selectedVehiclePublicId != null &&
                    selectedVehicle.publicId != restoredDraft?.selectedVehiclePublicId)) {
                discardDraft()
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@onSuccess
            }
            _uiState.update { it.copy(activeVehicles = (it.activeVehicles + selectedVehicle).distinctBy { vehicle -> vehicle.id }) }
            sourcePublicId = record.publicId
            sourceReady = true
            if (restoredDraft != null) {
                _uiState.update { it.copy(isLoading = !garageReady) }
            } else _uiState.update { state -> state.copy(selectedVehicleId = record.vehicleId, date = LocalDate.ofEpochDay(record.dateEpochDay).toString(), time = record.timeMinuteOfDay?.let(::minuteOfDayToText).orEmpty(), odometerKm = record.odometerKm.toString(), volumeLiters = record.fuelVolumeMl.toScaledDecimalText(3), pricePerLiter = record.pricePerLiterMilli?.toScaledDecimalText(3).orEmpty(), totalCostTwd = record.totalCostTwd.toString(), isFullTank = record.isFullTank, fuelProduct = record.fuelProduct, fuelingMode = record.fuelingMode, fuelEconomyStatisticsStatus = record.fuelEconomyStatisticsStatus, note = record.note.orEmpty(), calculationSources = emptyList(), calculatedField = null, isLoading = !garageReady) }
            persistDraft()
        }.onFailure { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
    }

    private fun save() {
        if (_uiState.value.isSaving || !_uiState.value.canSave || draftDiscarded) return
        val validation = _uiState.value.validate()
        val input = validation.input ?: run { _uiState.update { it.copy(errors = validation.errors) }; return }
        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch { runCatching { fuelRepository.getOdometerNeighbors(input, fuelRecordId) }.onSuccess { neighbors -> if (neighbors.breaksOrder(input.odometerKm)) _uiState.update { it.copy(isSaving = false, odometerOrderWarning = neighbors, odometerOrderReasonRequired = false, pendingSaveInput = input) } else checkFuelEconomyOutlier(input) }.onFailure { _uiState.update { it.copy(isSaving = false, saveFailed = true) } } }
    }

    private fun confirmOdometerOrder() {
        val state = _uiState.value
        if (state.isSaving) return
        val input = state.pendingSaveInput ?: return
        if (state.odometerOrderReason.isBlank()) { _uiState.update { it.copy(odometerOrderReasonRequired = true) }; return }
        _uiState.update { it.copy(isSaving = true, odometerOrderWarning = null) }
        viewModelScope.launch { checkFuelEconomyOutlier(input) }
    }

    private suspend fun checkFuelEconomyOutlier(input: com.kumo.bubu.domain.model.FuelRecordInput) {
        val existing = runCatching {
            fuelRepository.observeFuelRecords(input.vehicleId).first()
                .filter { it.id != fuelRecordId }
        }.getOrElse {
            _uiState.update { state -> state.copy(isSaving = false, saveFailed = true) }
            return
        }
        val candidate = FuelRecord(
            id = Long.MIN_VALUE,
            publicId = "pending",
            vehicleId = input.vehicleId,
            dateEpochDay = input.dateEpochDay,
            timeMinuteOfDay = input.timeMinuteOfDay,
            sequenceInDay = Int.MAX_VALUE,
            odometerKm = input.odometerKm,
            fuelVolumeMl = input.fuelVolumeMl,
            pricePerLiterMilli = input.pricePerLiterMilli,
            totalCostTwd = input.totalCostTwd,
            isFullTank = input.isFullTank,
            fuelProduct = input.fuelProduct,
            fuelingMode = input.fuelingMode,
            fuelEconomyStatisticsStatus = input.fuelEconomyStatisticsStatus,
            note = input.note,
            createdAt = 0,
            updatedAt = 0,
        )
        val outlier = detectFuelEconomyOutlier(existing, candidate)
        if (outlier == null) persist(input)
        else _uiState.update { it.copy(isSaving = false, fuelEconomyOutlier = outlier, pendingSaveInput = input) }
    }

    private fun confirmFuelEconomyOutlier() {
        val input = _uiState.value.pendingSaveInput ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, fuelEconomyOutlier = null) }
        viewModelScope.launch { persist(input.copy(fuelEconomyStatisticsStatus = com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus.INCLUDED)) }
    }

    private fun excludeFuelEconomyOutlier() {
        val input = _uiState.value.pendingSaveInput ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, fuelEconomyOutlier = null) }
        viewModelScope.launch { persist(input.copy(fuelEconomyStatisticsStatus = com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus.EXCLUDED)) }
    }

    private suspend fun persist(input: com.kumo.bubu.domain.model.FuelRecordInput) {
        try {
            fuelRecordId?.let { fuelRepository.updateFuelRecord(it, input) }
                ?: fuelRepository.createFuelRecord(input, draftId)
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            java.util.logging.Logger.getLogger(FuelFormViewModel::class.java.name)
                .log(java.util.logging.Level.WARNING, "Fuel save failed; checking whether creation committed.", failure)
            val committed = if (fuelRecordId == null) {
                try {
                    fuelRepository.findFuelRecordIdByPublicId(draftId) != null
                } catch (lookupFailure: Exception) {
                    if (lookupFailure is kotlinx.coroutines.CancellationException) throw lookupFailure
                    java.util.logging.Logger.getLogger(FuelFormViewModel::class.java.name)
                        .log(java.util.logging.Level.WARNING, "Unable to check fuel creation outcome.", lookupFailure)
                    _uiState.update { it.copy(isSaving = false, saveFailed = true, loadFailed = true) }
                    return
                }
            } else false
            if (!committed) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
                return
            }
        }
        runCatching { clearDraft() }.onFailure {
            java.util.logging.Logger.getLogger(FuelFormViewModel::class.java.name)
                .log(java.util.logging.Level.WARNING, "Committed fuel draft cleanup will retry when restored.", it)
        }
        _uiState.update { it.copy(isSaving = false, pendingSaveInput = null) }
        effectChannel.send(FuelFormEffect.Saved)
    }

    companion object {
        private const val DRAFT_KEY = "fuel.form.draft.v1"

        fun factory(
            fuelRepository: FuelRepository,
            fuelPriceRepository: FuelPriceRepository,
            vehicleRepository: VehicleRepository,
            fuelRecordId: Long? = null,
            initialVehicleId: Long? = null,
            draftRepository: FormDraftRepository? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FuelFormViewModel(
                    fuelRepository,
                    fuelPriceRepository,
                    vehicleRepository,
                    fuelRecordId,
                    initialVehicleId,
                    createSavedStateHandle(),
                    draftRepository,
                )
            }
        }
    }
}

private fun minuteOfDayToText(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
