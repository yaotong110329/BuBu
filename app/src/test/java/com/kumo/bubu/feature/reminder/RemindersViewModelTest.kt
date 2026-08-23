package com.kumo.bubu.feature.reminder

import com.kumo.bubu.domain.model.ManualReminderInput
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleReminder
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.ReminderRepository
import com.kumo.bubu.domain.repository.ReminderNotificationSettings
import com.kumo.bubu.core.notification.ReminderNotificationScheduler
import com.kumo.bubu.feature.vehicle.FakeVehicleRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun derivesDueSoonStatusFromTheVehicleCurrentOdometer() = runTest {
        val reminderRepository = FakeReminderRepository().apply {
            reminders.value = listOf(reminder(dueOdometerKm = 10_000))
        }
        val viewModel = RemindersViewModel(
            reminderRepository = reminderRepository,
            vehicleRepository = FakeVehicleRepository(listOf(vehicle(currentOdometerKm = 9_850))),
            notificationSettings = FakeReminderNotificationSettings(),
            notificationScheduler = FakeReminderNotificationScheduler(),
            todayProvider = { LocalDate.of(2026, 8, 16) },
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(ReminderStatus.DUE_SOON, viewModel.uiState.value.rows.single().status)
        assertEquals("RAV4", viewModel.uiState.value.rows.single().vehicleName)
    }

    @Test
    fun sendsManualReminderAndSnoozeEventsToRepository() = runTest {
        val reminderRepository = FakeReminderRepository().apply {
            reminders.value = listOf(reminder(id = 9))
        }
        val viewModel = RemindersViewModel(
            reminderRepository = reminderRepository,
            vehicleRepository = FakeVehicleRepository(listOf(vehicle())),
            notificationSettings = FakeReminderNotificationSettings(),
            notificationScheduler = FakeReminderNotificationScheduler(),
            todayProvider = { LocalDate.of(2026, 8, 16) },
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onEvent(ReminderEvent.CreateManual(1, "驗車", "", "2026-09-01"))
        viewModel.onEvent(ReminderEvent.Snooze(9, 3))
        viewModel.onEvent(ReminderEvent.SnoozeUntil(9, "2026-08-31"))
        advanceUntilIdle()

        assertEquals("驗車", reminderRepository.createdInput?.title)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), reminderRepository.createdInput?.dueDateEpochDay)
        assertEquals(LocalDate.of(2026, 8, 31).toEpochDay(), reminderRepository.snoozedUntilById[9])
    }

    @Test
    fun enablingAndDisablingNotificationsSchedulesOrCancelsOnlyNotificationWork() = runTest {
        val settings = FakeReminderNotificationSettings()
        val scheduler = FakeReminderNotificationScheduler()
        val viewModel = RemindersViewModel(
            reminderRepository = FakeReminderRepository(),
            vehicleRepository = FakeVehicleRepository(listOf(vehicle())),
            notificationSettings = settings,
            notificationScheduler = scheduler,
        )

        viewModel.onEvent(ReminderEvent.SetNotificationsEnabled(true))
        advanceUntilIdle()
        assertTrue(settings.enabled.value)
        assertTrue(scheduler.scheduled)

        viewModel.onEvent(ReminderEvent.SetNotificationsEnabled(false))
        advanceUntilIdle()
        assertFalse(settings.enabled.value)
        assertTrue(scheduler.cancelled)
        assertTrue(scheduler.cancelledAllNotifications)
    }

    private fun vehicle(currentOdometerKm: Long = 9_000) = Vehicle(
        id = 1,
        publicId = "vehicle-1",
        name = "RAV4",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 19_000,
        trackingStartOdometerKm = 1,
        currentOdometerKm = currentOdometerKm,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun reminder(id: Long = 1, dueOdometerKm: Long? = null) = VehicleReminder(
        id = id,
        publicId = "reminder-$id",
        vehicleId = 1,
        source = ReminderSource.MANUAL,
        sourceServiceItemId = null,
        title = "機油",
        dueOdometerKm = dueOdometerKm,
        dueDateEpochDay = null,
        completedByServiceRecordId = null,
        completedAt = null,
        snoozedUntilEpochDay = null,
        isEnabled = true,
        createdAt = 1,
        updatedAt = 1,
    )
}

private class FakeReminderNotificationSettings : ReminderNotificationSettings {
    val enabled = MutableStateFlow(false)

    override fun observeEnabled(): Flow<Boolean> = enabled

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}

private class FakeReminderNotificationScheduler : ReminderNotificationScheduler {
    var scheduled = false
    var cancelled = false
    var cancelledAllNotifications = false

    override fun scheduleDailyCheck() {
        scheduled = true
    }

    override fun cancelDailyCheck() {
        cancelled = true
    }

    override fun cancelReminderNotification(reminderId: Long) = Unit

    override fun cancelAllReminderNotifications() {
        cancelledAllNotifications = true
    }
}

private class FakeReminderRepository : ReminderRepository {
    val reminders = MutableStateFlow<List<VehicleReminder>>(emptyList())
    var createdInput: ManualReminderInput? = null
    val snoozedUntilById = mutableMapOf<Long, Long>()

    override fun observeReminders(): Flow<List<VehicleReminder>> = reminders

    override suspend fun createManualReminder(input: ManualReminderInput): Long {
        createdInput = input
        return 1
    }

    override suspend fun completeReminder(id: Long) = Unit

    override suspend fun snoozeReminder(id: Long, untilEpochDay: Long) {
        snoozedUntilById[id] = untilEpochDay
    }

    override suspend fun setReminderEnabled(id: Long, enabled: Boolean) = Unit

    override suspend fun setTaxAndFeeRemindersEnabled(enabled: Boolean) = Unit

    override suspend fun ensureStatutoryReminder(
        vehicleId: Long,
        cycleYear: Int,
        source: ReminderSource,
    ) = Unit

    override suspend fun refreshAutomaticReminders(today: LocalDate) = Unit
}
