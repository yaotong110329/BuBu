package com.kumo.bubu.feature.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.applyToCpcListPrice
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
import kotlinx.coroutines.launch

class FuelFormViewModel(
    private val fuelRepository: FuelRepository,
    private val fuelPriceRepository: FuelPriceRepository,
    private val vehicleRepository: VehicleRepository,
    private val fuelRecordId: Long? = null,
    private val initialVehicleId: Long? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FuelFormUiState(fuelRecordId = fuelRecordId))
    val uiState: StateFlow<FuelFormUiState> = _uiState.asStateFlow()
    private val effectChannel = Channel<FuelFormEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        if (fuelRecordId != null) loadFuelRecord(fuelRecordId)
        viewModelScope.launch {
            vehicleRepository.observeGarage().collect { garage ->
                val previous = _uiState.value
                val selectedVehicleId = previous.selectedVehicleId?.takeIf { selected -> garage.vehicles.any { it.id == selected } }
                    ?: initialVehicleId?.takeIf { selected -> garage.vehicles.any { it.id == selected && !it.isArchived } }
                    ?: garage.currentVehiclePublicId?.let { current -> garage.vehicles.firstOrNull { it.publicId == current }?.id }
                    ?: garage.vehicles.firstOrNull { !it.isArchived }?.id
                val activeVehicles = garage.vehicles.filter { !it.isArchived || it.id == selectedVehicleId }
                _uiState.update { state -> state.copy(activeVehicles = activeVehicles, selectedVehicleId = selectedVehicleId, isLoading = false) }
                if (!previous.isEditing && previous.selectedVehicleId == null && selectedVehicleId != null) loadVehicleDefaults(selectedVehicleId)
            }
        }
    }

    fun onEvent(event: FuelFormEvent) = when (event) {
        is FuelFormEvent.VehicleChanged -> selectVehicle(event.vehicleId)
        is FuelFormEvent.DateChanged -> updateField { copy(date = event.value) }
        is FuelFormEvent.TimeChanged -> updateField { copy(time = event.value) }
        is FuelFormEvent.OdometerChanged -> updateField { copy(odometerKm = event.value) }
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
        FuelFormEvent.RefreshPrice -> refreshPriceIfNewRecord()
        is FuelFormEvent.NoteChanged -> updateField { copy(note = event.value) }
        is FuelFormEvent.OdometerOrderReasonChanged -> _uiState.update { it.copy(odometerOrderReason = event.value, odometerOrderReasonRequired = false) }
        FuelFormEvent.ConfirmOdometerOrder -> confirmOdometerOrder()
        FuelFormEvent.DismissOdometerOrder -> _uiState.update { it.copy(odometerOrderWarning = null, odometerOrderReason = "", odometerOrderReasonRequired = false, pendingSaveInput = null) }
        FuelFormEvent.Save -> save()
    }

    private fun updateField(transform: FuelFormUiState.() -> FuelFormUiState) = _uiState.update { it.transform().copy(errors = emptyMap(), saveFailed = false) }

    private fun selectVehicle(vehicleId: Long) {
        updateField { copy(selectedVehicleId = vehicleId, fuelProduct = if (isEditing) fuelProduct else null, priceSource = FuelPriceSource.NONE, priceEffectiveDateEpochDay = null) }
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
        refreshPriceIfNewRecord()
    }

    private fun refreshPriceIfNewRecord() {
        val state = _uiState.value
        if (state.isEditing) return
        val vehicleId = state.selectedVehicleId ?: return
        val product = state.fuelProduct ?: return
        val fuelingMode = state.fuelingMode
        val date = runCatching { LocalDate.parse(state.date) }.getOrNull() ?: return
        _uiState.update { it.copy(priceSource = FuelPriceSource.LOADING, priceEffectiveDateEpochDay = null) }
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
            }
        }
    }

    private fun isPriceRequestCurrent(vehicleId: Long, product: FuelProduct, fuelingMode: FuelingMode, date: LocalDate): Boolean =
        _uiState.value.let { state ->
            !state.isEditing &&
                state.selectedVehicleId == vehicleId &&
                state.fuelProduct == product &&
                state.fuelingMode == fuelingMode &&
                state.date == date.toString() &&
                state.priceSource == FuelPriceSource.LOADING
        }

    private fun applyAutomaticPrice(value: String, source: FuelPriceSource, effectiveDateEpochDay: Long?) = _uiState.update { state ->
        state.withUserValue(FuelCalculationField.PRICE, value).copy(priceSource = source, priceEffectiveDateEpochDay = effectiveDateEpochDay)
    }

    private fun loadFuelRecord(id: Long) = viewModelScope.launch {
        runCatching { fuelRepository.getFuelRecord(id) }.onSuccess { record ->
            if (record == null) _uiState.update { it.copy(isLoading = false, saveFailed = true) }
            else _uiState.update { state -> state.copy(selectedVehicleId = record.vehicleId, date = LocalDate.ofEpochDay(record.dateEpochDay).toString(), time = record.timeMinuteOfDay?.let(::minuteOfDayToText).orEmpty(), odometerKm = record.odometerKm.toString(), volumeLiters = record.fuelVolumeMl.toScaledDecimalText(3), pricePerLiter = record.pricePerLiterMilli?.toScaledDecimalText(3).orEmpty(), totalCostTwd = record.totalCostTwd.toString(), isFullTank = record.isFullTank, fuelProduct = record.fuelProduct, fuelingMode = record.fuelingMode, note = record.note.orEmpty(), calculationSources = emptyList(), calculatedField = null, isLoading = state.activeVehicles.isEmpty()) }
        }.onFailure { _uiState.update { it.copy(isLoading = false, saveFailed = true) } }
    }

    private fun save() {
        if (_uiState.value.isSaving) return
        val validation = _uiState.value.validate()
        val input = validation.input ?: run { _uiState.update { it.copy(errors = validation.errors) }; return }
        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch { runCatching { fuelRepository.getOdometerNeighbors(input, fuelRecordId) }.onSuccess { neighbors -> if (neighbors.breaksOrder(input.odometerKm)) _uiState.update { it.copy(isSaving = false, odometerOrderWarning = neighbors, odometerOrderReason = "", odometerOrderReasonRequired = false, pendingSaveInput = input) } else persist(input) }.onFailure { _uiState.update { it.copy(isSaving = false, saveFailed = true) } } }
    }

    private fun confirmOdometerOrder() {
        val state = _uiState.value
        if (state.isSaving) return
        val input = state.pendingSaveInput ?: return
        if (state.odometerOrderReason.isBlank()) { _uiState.update { it.copy(odometerOrderReasonRequired = true) }; return }
        _uiState.update { it.copy(isSaving = true, odometerOrderWarning = null) }
        viewModelScope.launch { persist(input) }
    }

    private suspend fun persist(input: com.kumo.bubu.domain.model.FuelRecordInput) = runCatching { fuelRecordId?.let { fuelRepository.updateFuelRecord(it, input) } ?: fuelRepository.createFuelRecord(input) }.onSuccess { _uiState.update { it.copy(isSaving = false, pendingSaveInput = null) }; effectChannel.send(FuelFormEffect.Saved) }.onFailure { _uiState.update { it.copy(isSaving = false, saveFailed = true) } }

    companion object {
        fun factory(
            fuelRepository: FuelRepository,
            fuelPriceRepository: FuelPriceRepository,
            vehicleRepository: VehicleRepository,
            fuelRecordId: Long? = null,
            initialVehicleId: Long? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FuelFormViewModel(
                    fuelRepository,
                    fuelPriceRepository,
                    vehicleRepository,
                    fuelRecordId,
                    initialVehicleId,
                )
            }
        }
    }
}

private fun minuteOfDayToText(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
