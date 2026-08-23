package com.kumo.bubu.feature.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.ServiceTypeInput
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.ServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceTypeManagementUiState(
    val types: List<ServiceType> = emptyList(),
    val vehicleType: VehicleType = VehicleType.CAR,
    val name: String = "",
    val editingId: Long? = null,
    val isEditorVisible: Boolean = false,
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

sealed interface ServiceTypeManagementEvent {
    data object AddCustomType : ServiceTypeManagementEvent
    data class EditCustomType(val type: ServiceType) : ServiceTypeManagementEvent
    data class NameChanged(val value: String) : ServiceTypeManagementEvent
    data class SelectVehicleType(val value: VehicleType) : ServiceTypeManagementEvent
    data class SetArchived(val id: Long, val archived: Boolean) : ServiceTypeManagementEvent
    data class DeleteCustomType(val id: Long) : ServiceTypeManagementEvent
    data class Move(val id: Long, val offset: Int) : ServiceTypeManagementEvent
    data object Save : ServiceTypeManagementEvent
    data object DismissEditor : ServiceTypeManagementEvent
    data object DismissError : ServiceTypeManagementEvent
}

class ServiceTypeManagementViewModel(private val repository: ServiceRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ServiceTypeManagementUiState())
    val uiState: StateFlow<ServiceTypeManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureDefaultServiceTypes() }
        viewModelScope.launch {
            repository.observeServiceTypes().collect { types ->
                _uiState.update { it.copy(types = types, isLoading = false) }
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
            is ServiceTypeManagementEvent.SelectVehicleType -> change { copy(vehicleType = event.value) }
            is ServiceTypeManagementEvent.SetArchived -> launch { repository.setServiceTypeArchived(event.id, event.archived) }
            is ServiceTypeManagementEvent.DeleteCustomType -> launch { repository.deleteCustomServiceType(event.id) }
            is ServiceTypeManagementEvent.Move -> move(event.id, event.offset)
            ServiceTypeManagementEvent.Save -> save()
            ServiceTypeManagementEvent.DismissEditor -> change { copy(isEditorVisible = false, editingId = null, name = "") }
            ServiceTypeManagementEvent.DismissError -> change { copy(error = false) }
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

    private fun move(id: Long, offset: Int) {
        val types = _uiState.value.types
            .filter { it.vehicleType == _uiState.value.vehicleType }
            .sortedBy(ServiceType::sortOrder)
            .toMutableList()
        val from = types.indexOfFirst { it.id == id }
        val to = (from + offset).coerceIn(0, types.lastIndex)
        if (from < 0 || from == to) return
        val moved = types.removeAt(from)
        types.add(to, moved)
        launch { repository.reorderServiceTypes(types.map(ServiceType::id)) }
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { change { copy(error = true) } }
    }

    private fun change(block: ServiceTypeManagementUiState.() -> ServiceTypeManagementUiState) {
        _uiState.update { it.block() }
    }

    companion object {
        fun factory(repository: ServiceRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ServiceTypeManagementViewModel(repository) }
        }
    }
}
