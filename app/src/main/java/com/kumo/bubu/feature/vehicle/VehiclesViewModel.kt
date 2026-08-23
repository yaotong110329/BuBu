package com.kumo.bubu.feature.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VehiclesUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val currentVehiclePublicId: String? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val actionInProgressVehicleId: Long? = null,
    val deleteConfirmationVehicleId: Long? = null,
    val actionError: VehicleActionError? = null,
)

enum class VehicleActionError {
    SELECT_FAILED,
    UPDATE_FAILED,
    DELETE_FAILED,
}

sealed interface VehiclesEvent {
    data class SelectCurrent(val publicId: String, val vehicleId: Long) : VehiclesEvent
    data class Archive(val vehicleId: Long) : VehiclesEvent
    data class Unarchive(val vehicleId: Long) : VehiclesEvent
    data class RequestDelete(val vehicleId: Long) : VehiclesEvent
    data object DismissDelete : VehiclesEvent
    data object ConfirmDelete : VehiclesEvent
    data object DismissError : VehiclesEvent
}

private data class VehicleActionState(
    val inProgressVehicleId: Long? = null,
    val deleteConfirmationVehicleId: Long? = null,
    val error: VehicleActionError? = null,
)

class VehiclesViewModel(private val repository: VehicleRepository) : ViewModel() {
    private val actionState = MutableStateFlow(VehicleActionState())

    private val garageState = repository.observeGarage()
        .map { garage ->
            VehiclesUiState(
                vehicles = garage.vehicles,
                currentVehiclePublicId = garage.currentVehiclePublicId,
                isLoading = false,
            )
        }
        .catch { emit(VehiclesUiState(isLoading = false, loadFailed = true)) }

    val uiState = combine(garageState, actionState) { garage, action ->
        garage.copy(
            actionInProgressVehicleId = action.inProgressVehicleId,
            deleteConfirmationVehicleId = action.deleteConfirmationVehicleId,
            actionError = action.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehiclesUiState())

    fun onEvent(event: VehiclesEvent) {
        when (event) {
            is VehiclesEvent.SelectCurrent -> perform(event.vehicleId, VehicleActionError.SELECT_FAILED) {
                repository.selectCurrentVehicle(event.publicId)
            }
            is VehiclesEvent.Archive -> setArchived(event.vehicleId, true)
            is VehiclesEvent.Unarchive -> setArchived(event.vehicleId, false)
            is VehiclesEvent.RequestDelete -> actionState.update {
                it.copy(deleteConfirmationVehicleId = event.vehicleId, error = null)
            }
            VehiclesEvent.DismissDelete -> actionState.update { it.copy(deleteConfirmationVehicleId = null) }
            VehiclesEvent.ConfirmDelete -> confirmDelete()
            VehiclesEvent.DismissError -> actionState.update { it.copy(error = null) }
        }
    }

    private fun setArchived(vehicleId: Long, archived: Boolean) = perform(
        vehicleId,
        VehicleActionError.UPDATE_FAILED,
    ) { repository.setVehicleArchived(vehicleId, archived) }

    private fun confirmDelete() {
        val vehicleId = actionState.value.deleteConfirmationVehicleId ?: return
        perform(vehicleId, VehicleActionError.DELETE_FAILED) {
            repository.deleteUnreferencedVehicle(vehicleId)
        }
    }

    private fun perform(
        vehicleId: Long,
        failure: VehicleActionError,
        action: suspend () -> Unit,
    ) = viewModelScope.launch {
        actionState.value = VehicleActionState(inProgressVehicleId = vehicleId)
        runCatching { action() }
            .onSuccess { actionState.value = VehicleActionState() }
            .onFailure { actionState.value = VehicleActionState(error = failure) }
    }

    companion object {
        fun factory(repository: VehicleRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { VehiclesViewModel(repository) }
        }
    }
}
