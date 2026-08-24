package com.kumo.bubu.feature.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.ServiceTypeInput
import com.kumo.bubu.domain.model.ServiceReminderPreference
import com.kumo.bubu.domain.model.ServiceReminderPreferenceInput
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ServiceTypeManagementUiState(
    val types: List<ServiceType> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val selectedReminderVehicleId: Long? = null,
    val reminderPreferences: List<ServiceReminderPreference> = emptyList(),
    val vehicleType: VehicleType = VehicleType.CAR,
    val name: String = "",
    val editingId: Long? = null,
    val isEditorVisible: Boolean = false,
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val reminderEditingType: ServiceType? = null,
    val reminderIntervalKm: String = "",
    val reminderBaseOdometerKm: String = "",
    val reminderEnabled: Boolean = true,
)

sealed interface ServiceTypeManagementEvent {
    data object AddCustomType : ServiceTypeManagementEvent
    data class EditCustomType(val type: ServiceType) : ServiceTypeManagementEvent
    data class NameChanged(val value: String) : ServiceTypeManagementEvent
    data class SelectVehicleType(val value: VehicleType) : ServiceTypeManagementEvent
    data class SetArchived(val id: Long, val archived: Boolean) : ServiceTypeManagementEvent
    data class DeleteCustomType(val id: Long) : ServiceTypeManagementEvent
    data class Reorder(val orderedIds: List<Long>) : ServiceTypeManagementEvent
    data class SelectReminderVehicle(val vehicleId: Long) : ServiceTypeManagementEvent
    data class EditReminder(val type: ServiceType) : ServiceTypeManagementEvent
    data class ReminderIntervalChanged(val value: String) : ServiceTypeManagementEvent
    data class ReminderBaseOdometerChanged(val value: String) : ServiceTypeManagementEvent
    data class ReminderEnabledChanged(val enabled: Boolean) : ServiceTypeManagementEvent
    data object SaveReminder : ServiceTypeManagementEvent
    data object DismissReminderEditor : ServiceTypeManagementEvent
    data object Save : ServiceTypeManagementEvent
    data object DismissEditor : ServiceTypeManagementEvent
    data object DismissError : ServiceTypeManagementEvent
}

class ServiceTypeManagementViewModel(
    private val repository: ServiceRepository,
    private val vehicleRepository: VehicleRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServiceTypeManagementUiState())
    val uiState: StateFlow<ServiceTypeManagementUiState> = _uiState.asStateFlow()
    private var preferenceJob: Job? = null

    init {
        viewModelScope.launch { repository.ensureDefaultServiceTypes() }
        viewModelScope.launch {
            repository.observeServiceTypes().collect { types ->
                _uiState.update { it.copy(types = types, isLoading = false) }
            }
        }
        vehicleRepository?.let { vehicleRepository ->
            viewModelScope.launch {
                vehicleRepository.observeVehicles().collect { vehicles ->
                    val active = vehicles.filterNot(Vehicle::isArchived)
                    val current = _uiState.value
                    val selected = current.selectedReminderVehicleId
                        ?.takeIf { id -> active.any { it.id == id } }
                        ?: active.firstOrNull { it.vehicleType == current.vehicleType }?.id
                        ?: active.firstOrNull()?.id
                    _uiState.update { it.copy(vehicles = active, selectedReminderVehicleId = selected) }
                    observePreferences(selected)
                }
            }
        }
    }

    fun onEvent(event: ServiceTypeManagementEvent) {
        when (event) {
            ServiceTypeManagementEvent.AddCustomType -> change { copy(isEditorVisible = true, editingId = null, name = "") }
            is ServiceTypeManagementEvent.EditCustomType -> if (!event.type.isBuiltIn) {
                change { copy(isEditorVisible = true, editingId = event.type.id, name = event.type.name) }
            }
            is ServiceTypeManagementEvent.NameChanged -> change { copy(name = event.value) }
            is ServiceTypeManagementEvent.SelectVehicleType -> {
                val vehicleId = _uiState.value.vehicles.firstOrNull { it.vehicleType == event.value }?.id
                change { copy(vehicleType = event.value, selectedReminderVehicleId = vehicleId) }
                observePreferences(vehicleId)
            }
            is ServiceTypeManagementEvent.SetArchived -> launch { repository.setServiceTypeArchived(event.id, event.archived) }
            is ServiceTypeManagementEvent.DeleteCustomType -> launch { repository.deleteCustomServiceType(event.id) }
            is ServiceTypeManagementEvent.Reorder -> reorder(event.orderedIds)
            is ServiceTypeManagementEvent.SelectReminderVehicle -> {
                change { copy(selectedReminderVehicleId = event.vehicleId) }
                observePreferences(event.vehicleId)
            }
            is ServiceTypeManagementEvent.EditReminder -> openReminderEditor(event.type)
            is ServiceTypeManagementEvent.ReminderIntervalChanged -> change { copy(reminderIntervalKm = event.value.filter(Char::isDigit)) }
            is ServiceTypeManagementEvent.ReminderBaseOdometerChanged -> change { copy(reminderBaseOdometerKm = event.value.filter(Char::isDigit)) }
            is ServiceTypeManagementEvent.ReminderEnabledChanged -> change { copy(reminderEnabled = event.enabled) }
            ServiceTypeManagementEvent.SaveReminder -> saveReminder()
            ServiceTypeManagementEvent.DismissReminderEditor -> change { copy(reminderEditingType = null, reminderIntervalKm = "", reminderBaseOdometerKm = "") }
            ServiceTypeManagementEvent.Save -> save()
            ServiceTypeManagementEvent.DismissEditor -> change { copy(isEditorVisible = false, editingId = null, name = "") }
            ServiceTypeManagementEvent.DismissError -> change { copy(error = false) }
        }
    }

    private fun observePreferences(vehicleId: Long?) {
        preferenceJob?.cancel()
        if (vehicleId == null) {
            change { copy(reminderPreferences = emptyList()) }
            return
        }
        preferenceJob = viewModelScope.launch {
            repository.observeServiceReminderPreferences(vehicleId).collect { preferences ->
                change { copy(reminderPreferences = preferences) }
            }
        }
    }

    private fun openReminderEditor(type: ServiceType) {
        val preference = _uiState.value.reminderPreferences.firstOrNull { it.serviceTypeId == type.id }
        change {
            copy(
                reminderEditingType = type,
                reminderIntervalKm = preference?.intervalKm?.toString().orEmpty(),
                reminderBaseOdometerKm = preference?.baseOdometerKm?.toString().orEmpty(),
                reminderEnabled = preference?.isEnabled ?: true,
            )
        }
    }

    private fun saveReminder() {
        val state = _uiState.value
        val type = state.reminderEditingType ?: return
        val vehicleId = state.selectedReminderVehicleId ?: run { change { copy(error = true) }; return }
        val intervalKm = state.reminderIntervalKm.toLongOrNull()
        val baseOdometerKm = state.reminderBaseOdometerKm.toLongOrNull()
        if (
            (state.reminderIntervalKm.isNotBlank() && intervalKm == null) ||
            (state.reminderBaseOdometerKm.isNotBlank() && baseOdometerKm == null)
        ) {
            change { copy(error = true) }
            return
        }
        launch {
            repository.saveServiceReminderPreference(
                ServiceReminderPreferenceInput(
                    vehicleId = vehicleId,
                    serviceTypeId = type.id,
                    isEnabled = state.reminderEnabled,
                    intervalKm = intervalKm,
                    baseOdometerKm = baseOdometerKm,
                    sortOrder = state.reminderPreferences.firstOrNull { it.serviceTypeId == type.id }?.sortOrder
                        ?: state.types.filter { it.vehicleType == type.vehicleType }.indexOf(type),
                ),
            )
            change { copy(reminderEditingType = null, reminderIntervalKm = "", reminderBaseOdometerKm = "") }
        }
    }

    private fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        if (name.isEmpty()) {
            change { copy(error = true) }
            return
        }
        launch {
            if (state.editingId == null) repository.createServiceType(ServiceTypeInput(name, state.vehicleType))
            else repository.updateServiceType(state.editingId, ServiceTypeInput(name))
            change { copy(isEditorVisible = false, editingId = null, name = "") }
        }
    }

    private fun reorder(orderedIds: List<Long>) {
        val activeTypeIds = _uiState.value.types
            .filter { it.vehicleType == _uiState.value.vehicleType && !it.isArchived }
            .sortedBy(ServiceType::sortOrder)
            .map(ServiceType::id)
        if (orderedIds == activeTypeIds) return
        if (orderedIds.size != activeTypeIds.size || orderedIds.toSet() != activeTypeIds.toSet()) {
            change { copy(error = true) }
            return
        }
        launch { repository.reorderServiceTypes(orderedIds) }
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { change { copy(error = true) } }
    }

    private fun change(block: ServiceTypeManagementUiState.() -> ServiceTypeManagementUiState) {
        _uiState.update { it.block() }
    }

    companion object {
        fun factory(repository: ServiceRepository, vehicleRepository: VehicleRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ServiceTypeManagementViewModel(repository, vehicleRepository) }
        }
    }
}
