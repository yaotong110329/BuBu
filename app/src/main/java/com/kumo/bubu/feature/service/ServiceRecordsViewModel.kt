package com.kumo.bubu.feature.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.ServiceRecord
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServiceRecordRow(val record: ServiceRecord, val vehicleName: String)
data class ServiceRecordsUiState(val records: List<ServiceRecordRow> = emptyList(), val currentVehicleId: Long? = null, val isLoading: Boolean = true, val failed: Boolean = false, val deleteFailed: Boolean = false)
sealed interface ServiceRecordsEvent { data class Delete(val id: Long) : ServiceRecordsEvent }
class ServiceRecordsViewModel(private val serviceRepository: ServiceRepository, vehicleRepository: VehicleRepository) : ViewModel() {
    private val deletingIds = mutableSetOf<Long>()
    private val deleteFailed = kotlinx.coroutines.flow.MutableStateFlow(false)
    val uiState = combine(serviceRepository.observeRecentServiceRecords(), vehicleRepository.observeGarage(), deleteFailed) { records, garage, deleteError ->
        val names = garage.vehicles.associate { it.id to it.name }
        ServiceRecordsUiState(records.map { ServiceRecordRow(it, names[it.vehicleId].orEmpty()) }, garage.currentVehiclePublicId?.let { id -> garage.vehicles.firstOrNull { it.publicId == id }?.id }, false, deleteFailed = deleteError)
    }.catch { emit(ServiceRecordsUiState(isLoading = false, failed = true)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceRecordsUiState())
    fun onEvent(event: ServiceRecordsEvent) { if (event is ServiceRecordsEvent.Delete && deletingIds.add(event.id)) viewModelScope.launch { try { serviceRepository.deleteServiceRecord(event.id); deleteFailed.value = false } catch (_: Throwable) { deleteFailed.value = true } finally { deletingIds.remove(event.id) } } }
    companion object { fun factory(serviceRepository: ServiceRepository, vehicleRepository: VehicleRepository): ViewModelProvider.Factory = viewModelFactory { initializer { ServiceRecordsViewModel(serviceRepository, vehicleRepository) } } }
}
