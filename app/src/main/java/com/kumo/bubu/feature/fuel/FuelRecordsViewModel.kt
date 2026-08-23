package com.kumo.bubu.feature.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.repository.FuelRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FuelRecordRow(val record: FuelRecord, val vehicleName: String)

data class FuelRecordsUiState(
    val records: List<FuelRecordRow> = emptyList(),
    val currentVehicleId: Long? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val actionInProgressRecordId: Long? = null,
    val deleteConfirmationRecordId: Long? = null,
    val deleteFailed: Boolean = false,
)

sealed interface FuelRecordsEvent {
    data class RequestDelete(val recordId: Long) : FuelRecordsEvent
    data object ConfirmDelete : FuelRecordsEvent
    data object DismissDelete : FuelRecordsEvent
    data object DismissError : FuelRecordsEvent
}

private data class FuelRecordActionState(
    val inProgressRecordId: Long? = null,
    val deleteConfirmationRecordId: Long? = null,
    val deleteFailed: Boolean = false,
)

class FuelRecordsViewModel(
    private val fuelRepository: FuelRepository,
    vehicleRepository: VehicleRepository,
) : ViewModel() {
    private val actionState = MutableStateFlow(FuelRecordActionState())

    private val recordsState = combine(
        fuelRepository.observeRecentFuelRecords(),
        vehicleRepository.observeGarage(),
    ) { records, garage ->
        val names = garage.vehicles.associate { it.id to it.name }
        FuelRecordsUiState(
            records = records.map { FuelRecordRow(it, names[it.vehicleId].orEmpty()) },
            currentVehicleId = garage.currentVehiclePublicId
                ?.let { current -> garage.vehicles.firstOrNull { it.publicId == current }?.id },
            isLoading = false,
        )
    }.catch { emit(FuelRecordsUiState(isLoading = false, loadFailed = true)) }

    val uiState = combine(recordsState, actionState) { records, action ->
        records.copy(
            actionInProgressRecordId = action.inProgressRecordId,
            deleteConfirmationRecordId = action.deleteConfirmationRecordId,
            deleteFailed = action.deleteFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelRecordsUiState())

    fun onEvent(event: FuelRecordsEvent) {
        when (event) {
            is FuelRecordsEvent.RequestDelete -> actionState.update {
                it.copy(deleteConfirmationRecordId = event.recordId, deleteFailed = false)
            }
            FuelRecordsEvent.DismissDelete -> actionState.update { it.copy(deleteConfirmationRecordId = null) }
            FuelRecordsEvent.DismissError -> actionState.update { it.copy(deleteFailed = false) }
            FuelRecordsEvent.ConfirmDelete -> deleteConfirmedRecord()
        }
    }

    private fun deleteConfirmedRecord() {
        val id = actionState.value.deleteConfirmationRecordId ?: return
        actionState.value = FuelRecordActionState(inProgressRecordId = id)
        viewModelScope.launch {
            runCatching { fuelRepository.deleteFuelRecord(id) }
                .onSuccess { actionState.value = FuelRecordActionState() }
                .onFailure { actionState.value = FuelRecordActionState(deleteFailed = true) }
        }
    }

    companion object {
        fun factory(
            fuelRepository: FuelRepository,
            vehicleRepository: VehicleRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { FuelRecordsViewModel(fuelRepository, vehicleRepository) }
        }
    }
}
