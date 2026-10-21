package com.kumo.bubu.core.notification

import com.kumo.bubu.domain.model.DerivedMaintenanceReminder
import com.kumo.bubu.domain.model.MaintenanceBaseline
import com.kumo.bubu.domain.model.MaintenanceReminderCalculation
import com.kumo.bubu.domain.model.MaintenanceReminderStatus
import com.kumo.bubu.domain.model.MaintenanceReminderType
import com.kumo.bubu.domain.repository.MaintenanceSummaryStateStore
import com.kumo.bubu.domain.repository.MaintenanceSummaryNotificationState
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaintenanceSummaryCoordinatorTest {
    @Test
    fun repeatedWorkerAndReconstructedCoordinatorPostOnceUntilStateEscalates() = runTest {
        val state = MemoryStateStore()
        val gateway = RecordingGateway()
        var reminders = listOf(reminder(1, MaintenanceReminderStatus.UPCOMING))
        fun coordinator() = MaintenanceSummaryCoordinator(
            loadReminders = { reminders },
            notificationsEnabled = { true },
            canPost = { true },
            stateStore = state,
            gateway = gateway,
        )

        coordinator().reconcile(allowAlert = true)
        coordinator().reconcile(allowAlert = true)
        assertEquals(listOf(1 to true), gateway.posts)

        reminders = listOf(reminder(1, MaintenanceReminderStatus.DUE))
        coordinator().reconcile(allowAlert = true)
        assertEquals(listOf(1 to true, 1 to true), gateway.posts)
    }

    @Test
    fun vehicleArchiveRuleDisableAndServiceChangesReconcileExistingSummary() = runTest {
        val state = MemoryStateStore()
        val gateway = RecordingGateway()
        var reminders = listOf(
            reminder(1, MaintenanceReminderStatus.DUE),
            reminder(2, MaintenanceReminderStatus.OVERDUE),
        )
        val coordinator = MaintenanceSummaryCoordinator(
            loadReminders = { reminders },
            notificationsEnabled = { true },
            canPost = { true },
            stateStore = state,
            gateway = gateway,
        )
        coordinator.reconcile(allowAlert = true)

        // Archiving one vehicle removes its derived reminder.
        reminders = reminders.drop(1)
        coordinator.reconcile(allowAlert = false)
        assertEquals(listOf(2 to true, 1 to false), gateway.posts)

        // A new service item resets the remaining reminder to normal.
        reminders = listOf(reminder(1, MaintenanceReminderStatus.NORMAL))
        coordinator.reconcile(allowAlert = false)
        assertEquals(1, gateway.cancellations)
        assertNull(state.signature)

        // Editing/deleting that work order makes the reminder due again.
        reminders = listOf(reminder(1, MaintenanceReminderStatus.DUE))
        coordinator.reconcile(allowAlert = true)
        assertEquals(2 to true, gateway.posts.first())
        assertEquals(1 to true, gateway.posts.last())

        // Disabling the last rule removes the derived reminder.
        reminders = emptyList()
        coordinator.reconcile(allowAlert = false)
        assertEquals(2, gateway.cancellations)
    }

    @Test
    fun settingDisabledBetweenSnapshotAndPostCancelsInsteadOfPosting() = runTest {
        val state = MemoryStateStore()
        val gateway = RecordingGateway()
        var enabled = true
        var reads = 0
        val coordinator = MaintenanceSummaryCoordinator(
            loadReminders = {
                reads++
                if (reads == 1) enabled = false
                listOf(reminder(1, MaintenanceReminderStatus.DUE))
            },
            notificationsEnabled = { enabled },
            canPost = { true },
            stateStore = state,
            gateway = gateway,
        )

        coordinator.reconcile(allowAlert = true)
        assertEquals(emptyList<Pair<Int, Boolean>>(), gateway.posts)
        assertEquals(1, gateway.cancellations)
        assertNull(state.signature)
    }

    @Test
    fun silentReconcileDoesNotCreateANewNotificationOrConsumeNextWorkerAlert() = runTest {
        val state = MemoryStateStore()
        val gateway = RecordingGateway()
        var reminders = listOf(reminder(1, MaintenanceReminderStatus.DUE))
        val coordinator = MaintenanceSummaryCoordinator(
            loadReminders = { reminders },
            notificationsEnabled = { true },
            canPost = { true },
            stateStore = state,
            gateway = gateway,
        )

        coordinator.reconcile(allowAlert = false)
        assertEquals(emptyList<Pair<Int, Boolean>>(), gateway.posts)
        reminders = listOf(reminder(1, MaintenanceReminderStatus.OVERDUE))
        coordinator.reconcile(allowAlert = true)
        assertEquals(listOf(1 to true), gateway.posts)
    }

    @Test
    fun reconstructedObserverDoesNotPostAnUnchangedActiveSummary() = runTest {
        val state = MemoryStateStore()
        val gateway = RecordingGateway()
        val reminders = listOf(reminder(1, MaintenanceReminderStatus.DUE))
        fun coordinator() = MaintenanceSummaryCoordinator(
            loadReminders = { reminders },
            notificationsEnabled = { true },
            canPost = { true },
            stateStore = state,
            gateway = gateway,
        )

        coordinator().reconcile(allowAlert = true)
        coordinator().reconcile(allowAlert = false)
        assertEquals(listOf(1 to true), gateway.posts)
    }

    private fun reminder(id: Long, status: MaintenanceReminderStatus) = DerivedMaintenanceReminder(
        vehicleId = id,
        serviceTypeId = 10,
        serviceTypeName = "Oil",
        sortOrder = 0,
        reminderType = MaintenanceReminderType.REPLACE,
        baseline = MaintenanceBaseline(1_000, LocalDate.of(2026, 1, 1)),
        intervalMileage = 5_000,
        intervalMonths = 6,
        calculation = MaintenanceReminderCalculation(status, 6_000, LocalDate.of(2026, 7, 1), 0, 0),
    )

    private class MemoryStateStore : MaintenanceSummaryStateStore {
        var state = MaintenanceSummaryNotificationState()
        val signature get() = state.alertedSignature
        override suspend fun read(): MaintenanceSummaryNotificationState = state
        override suspend fun write(state: MaintenanceSummaryNotificationState) { this.state = state }
    }

    private class RecordingGateway : MaintenanceSummaryNotificationGateway {
        val posts = mutableListOf<Pair<Int, Boolean>>()
        var cancellations = 0
        private var active = false
        override fun isActive(): Boolean = active
        override fun post(count: Int, alert: Boolean) {
            posts += count to alert
            active = true
        }
        override fun cancel() {
            cancellations++
            active = false
        }
    }
}
