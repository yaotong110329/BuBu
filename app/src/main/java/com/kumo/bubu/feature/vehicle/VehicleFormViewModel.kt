package com.kumo.bubu.feature.vehicle

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

class VehicleFormViewModel(
    private val repository: VehicleRepository,
    private val vehicleId: Long? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        VehicleFormUiState(vehicleId = vehicleId, isLoading = vehicleId != null),
    )
    val uiState: StateFlow<VehicleFormUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<VehicleFormEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        if (vehicleId != null) loadVehicle(vehicleId)
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
                _uiState.value = vehicle?.toFormUiState()
                    ?: VehicleFormUiState(vehicleId = id, loadFailed = true)
            }
            .onFailure { _uiState.value = VehicleFormUiState(vehicleId = id, loadFailed = true) }
    }

    private fun updateField(transform: VehicleFormUiState.() -> VehicleFormUiState) {
        _uiState.update { it.transform().copy(errors = emptyMap(), saveFailed = false) }
    }

    private fun save() {
        if (_uiState.value.isSaving) return
        val validation = _uiState.value.validate()
        val input = validation.input
        if (input == null) {
            _uiState.update { it.copy(errors = validation.errors) }
            return
        }

        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching {
                vehicleId?.let { repository.updateVehicle(it, input) }
                    ?: repository.createVehicle(input)
            }.onSuccess {
                _uiState.update { state -> state.copy(isSaving = false) }
                effectChannel.send(VehicleFormEffect.Saved)
            }.onFailure {
                _uiState.update { state -> state.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    companion object {
        fun factory(
            repository: VehicleRepository,
            vehicleId: Long? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { VehicleFormViewModel(repository, vehicleId) }
        }
    }
}
