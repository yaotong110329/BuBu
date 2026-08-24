package com.kumo.bubu.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.calculateRecentAverageFuelEconomyMilliKmPerLiter
import com.kumo.bubu.domain.model.estimateFuelInterval
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.repository.FuelRepository
import com.kumo.bubu.domain.repository.ReminderRepository
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VehicleDashboardItem(
    val vehicleId: Long,
    val name: String,
    val vehicleType: VehicleType,
    val licensePlate: String?,
    val latestOdometerKm: Long,
    val averageFuelEconomyMilliKmPerLiter: Long?,
    val maintenanceRemainingKm: Long? = null,
    val maintenanceEstimatedDays: Long? = null,
    val fuelPredictionDays: Long? = null,
)

data class DashboardUiState(
    val vehicles: List<VehicleDashboardItem> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

class DashboardViewModel(
    vehicleRepository: VehicleRepository,
    fuelRepository: FuelRepository,
    reminderRepository: ReminderRepository,
    serviceRepository: ServiceRepository,
) : ViewModel() {
    val uiState = combine(
        vehicleRepository.observeVehicles(),
        fuelRepository.observeRecentFuelRecords(),
        reminderRepository.observeReminders(),
        serviceRepository.observeServiceTypes(),
        serviceRepository.observeAllServiceReminderPreferences(),
    ) { vehicles, fuelRecords, reminders, serviceTypes, preferences ->
        val activeVehicles = vehicles.filterNot { it.isArchived }
        val todayEpochDay = java.time.LocalDate.now().toEpochDay()
        DashboardUiState(
            vehicles = activeVehicles
                .map { vehicle ->
                    val engineOilType = serviceTypes.firstOrNull { type ->
                        type.publicId == "builtin-${vehicle.vehicleType.name.lowercase()}-engine-oil"
                    }
                    val fixedMaintenancePreference = engineOilType?.let { type ->
                        preferences.firstOrNull { preference ->
                            preference.vehicleId == vehicle.id &&
                                preference.serviceTypeId == type.id && preference.isEnabled
                        }
                    }
                    val maintenanceReminder = reminders
                        .asSequence()
                        .filter { it.vehicleId == vehicle.id && it.source == ReminderSource.SERVICE_ITEM }
                        .filter { it.isEnabled && !it.isCompleted }
                        .filter { it.dueOdometerKm != null }
                        .filter { reminder -> reminder.title == engineOilType?.name }
                        .minByOrNull { it.dueOdometerKm!! }
                    val configuredDueOdometer = fixedMaintenancePreference
                        ?.takeIf { preference -> preference.baseOdometerKm != null && preference.intervalKm != null }
                        ?.let { preference -> preference.baseOdometerKm!! + preference.intervalKm!! }
                    val dueOdometer = maintenanceReminder?.dueOdometerKm ?: configuredDueOdometer
                    VehicleDashboardItem(
                        vehicleId = vehicle.id,
                        name = vehicle.name,
                        vehicleType = vehicle.vehicleType,
                        licensePlate = vehicle.licensePlate,
                        latestOdometerKm = vehicle.currentOdometerKm,
                        averageFuelEconomyMilliKmPerLiter = calculateRecentAverageFuelEconomyMilliKmPerLiter(
                            fuelRecords.filter { it.vehicleId == vehicle.id },
                        ),
                        maintenanceRemainingKm = dueOdometer?.minus(vehicle.currentOdometerKm),
                        maintenanceEstimatedDays = maintenanceReminder?.estimatedNotificationEpochDay
                            ?.minus(todayEpochDay)
                            ?.coerceAtLeast(0L),
                        fuelPredictionDays = estimateFuelInterval(
                            vehicle.id,
                            todayEpochDay,
                            fuelRecords,
                        )?.daysUntilNextFuel,
                    )
                },
            isLoading = false,
        )
    }.catch {
        emit(DashboardUiState(isLoading = false, loadFailed = true))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    init {
        viewModelScope.launch { serviceRepository.ensureDefaultServiceTypes() }
    }

    companion object {
        fun factory(
            vehicleRepository: VehicleRepository,
            fuelRepository: FuelRepository,
            reminderRepository: ReminderRepository,
            serviceRepository: ServiceRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(vehicleRepository, fuelRepository, reminderRepository, serviceRepository) }
        }
    }
}
