package com.kumo.bubu.feature.dashboard

import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.DerivedMaintenanceReminder
import com.kumo.bubu.domain.model.MaintenanceBaseline
import com.kumo.bubu.domain.model.MaintenanceIntervalPolicy
import com.kumo.bubu.domain.model.MaintenanceReminderCalculator
import com.kumo.bubu.domain.model.MaintenanceReminderType
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.feature.fuel.FakeFuelRepository
import com.kumo.bubu.feature.reminder.FakeReminderRepository
import com.kumo.bubu.feature.service.FakeServiceRepository
import com.kumo.bubu.feature.vehicle.FakeVehicleRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun retainedDashboardRecalculatesDateAndMixedMaintenanceAfterBackgroundWithoutDataChanges() = runTest {
        var today = LocalDate.of(2026, 8, 15)
        val services = object : ServiceRepository by FakeServiceRepository() {
            override fun observeMaintenanceReminders(today: LocalDate): Flow<List<DerivedMaintenanceReminder>> =
                MutableStateFlow(listOf(dateReminder(today, null), dateReminder(today, 8_000)))
        }
        val viewModel = DashboardViewModel(FakeVehicleRepository(listOf(vehicle())),
            FakeFuelRepository(), FakeReminderRepository(), services, todayProvider = { today })
        var collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.vehicles.single().maintenanceUpcomingCount)
        assertEquals(0, viewModel.uiState.value.vehicles.single().maintenanceDueCount)
        collection.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        today = LocalDate.of(2026, 8, 16)
        collection = backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        assertEquals(2, viewModel.uiState.value.vehicles.single().maintenanceDueCount)
        assertEquals(0, viewModel.uiState.value.vehicles.single().maintenanceUpcomingCount)
        collection.cancel()
        viewModel.viewModelScope.cancel()
    }

    private fun dateReminder(today: LocalDate, mileage: Long?): DerivedMaintenanceReminder {
        val baseline = MaintenanceBaseline(mileage, LocalDate.of(2026, 2, 16))
        val policy = MaintenanceIntervalPolicy(if (mileage == null) null else 10_000, 6,
            MaintenanceReminderType.REPLACE, reminderEnabled = true)
        return DerivedMaintenanceReminder(1, if (mileage == null) 8 else 9,
            if (mileage == null) "日期保養" else "混合保養", if (mileage == null) 0 else 1,
            MaintenanceReminderType.REPLACE, baseline, intervalMileage = policy.intervalMileage,
            intervalMonths = 6, calculation = MaintenanceReminderCalculator.calculate(baseline, policy, 9_000, today))
    }

    private fun vehicle() = Vehicle(1, "date-refresh-vehicle", "日期測試車", VehicleType.CAR,
        motorcycleClass = null, brand = null, model = null, manufactureYear = null,
        engineDisplacementCc = null, licensePlate = null, powertrainType = null,
        trackingStartDateEpochDay = 19_000, trackingStartOdometerKm = 1, currentOdometerKm = 9_000,
        note = null, isArchived = false, createdAt = 1, updatedAt = 1)
}
