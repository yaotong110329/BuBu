package com.kumo.bubu.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.calculateRecentAverageFuelEconomyMilliKmPerLiter
import com.kumo.bubu.domain.repository.FuelRepository
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class VehicleDashboardItem(
    val vehicleId: Long,
    val name: String,
    val vehicleType: VehicleType,
    val licensePlate: String?,
    val latestOdometerKm: Long,
    val averageFuelEconomyMilliKmPerLiter: Long?,
)

data class DashboardUiState(
    val vehicles: List<VehicleDashboardItem> = emptyList(),
    val recentRecords: List<DashboardRecentRecord> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

sealed interface DashboardRecentRecord {
    val vehicleId: Long
    val vehicleName: String
    val dateEpochDay: Long
    val timeMinuteOfDay: Int?
    val sequenceInDay: Int
    val id: Long
    val totalCostTwd: Long

    data class Fuel(
        override val vehicleId: Long,
        override val vehicleName: String,
        override val dateEpochDay: Long,
        override val timeMinuteOfDay: Int?,
        override val sequenceInDay: Int,
        override val id: Long,
        override val totalCostTwd: Long,
    ) : DashboardRecentRecord

    data class Service(
        override val vehicleId: Long,
        override val vehicleName: String,
        override val dateEpochDay: Long,
        override val timeMinuteOfDay: Int?,
        override val sequenceInDay: Int,
        override val id: Long,
        val title: String,
        override val totalCostTwd: Long,
    ) : DashboardRecentRecord
}

class DashboardViewModel(
    vehicleRepository: VehicleRepository,
    fuelRepository: FuelRepository,
    serviceRepository: ServiceRepository,
) : ViewModel() {
    val uiState = combine(
        vehicleRepository.observeVehicles(),
        fuelRepository.observeRecentFuelRecords(),
        serviceRepository.observeRecentServiceRecords(),
    ) { vehicles, fuelRecords, serviceRecords ->
        val activeVehicles = vehicles.filterNot { it.isArchived }
        val vehicleNames = activeVehicles.associate { it.id to it.name }
        DashboardUiState(
            vehicles = activeVehicles
                .map { vehicle ->
                    VehicleDashboardItem(
                        vehicleId = vehicle.id,
                        name = vehicle.name,
                        vehicleType = vehicle.vehicleType,
                        licensePlate = vehicle.licensePlate,
                        latestOdometerKm = vehicle.currentOdometerKm,
                        averageFuelEconomyMilliKmPerLiter = calculateRecentAverageFuelEconomyMilliKmPerLiter(
                            fuelRecords.filter { it.vehicleId == vehicle.id },
                        ),
                    )
                },
            recentRecords = (
                fuelRecords.mapNotNull { record ->
                    vehicleNames[record.vehicleId]?.let { vehicleName ->
                        DashboardRecentRecord.Fuel(
                            vehicleId = record.vehicleId, vehicleName = vehicleName,
                            dateEpochDay = record.dateEpochDay, timeMinuteOfDay = record.timeMinuteOfDay,
                            sequenceInDay = record.sequenceInDay, id = record.id, totalCostTwd = record.totalCostTwd,
                        )
                    }
                } + serviceRecords.mapNotNull { record ->
                    vehicleNames[record.vehicleId]?.let { vehicleName ->
                        DashboardRecentRecord.Service(
                            vehicleId = record.vehicleId, vehicleName = vehicleName,
                            dateEpochDay = record.dateEpochDay, timeMinuteOfDay = record.timeMinuteOfDay,
                            sequenceInDay = record.sequenceInDay, id = record.id, title = record.title,
                            totalCostTwd = record.totalCostTwd,
                        )
                    }
                }
                ).sortedWith(
                compareByDescending<DashboardRecentRecord> { it.dateEpochDay }
                    .thenByDescending { it.timeMinuteOfDay ?: -1 }
                    .thenByDescending { it.sequenceInDay }
                    .thenByDescending { it.id },
            ).take(MAX_DASHBOARD_RECENT_RECORDS),
            isLoading = false,
        )
    }.catch {
        emit(DashboardUiState(isLoading = false, loadFailed = true))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    companion object {
        fun factory(
            vehicleRepository: VehicleRepository,
            fuelRepository: FuelRepository,
            serviceRepository: ServiceRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(vehicleRepository, fuelRepository, serviceRepository) }
        }
    }
}

private const val MAX_DASHBOARD_RECENT_RECORDS = 5
