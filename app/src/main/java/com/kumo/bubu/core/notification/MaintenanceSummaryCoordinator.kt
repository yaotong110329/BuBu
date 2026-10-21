package com.kumo.bubu.core.notification

import com.kumo.bubu.domain.model.DerivedMaintenanceReminder
import com.kumo.bubu.domain.model.MaintenanceReminderStatus
import com.kumo.bubu.domain.repository.MaintenanceSummaryStateStore
import com.kumo.bubu.domain.repository.MaintenanceSummaryNotificationState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MaintenanceSummaryNotificationGateway {
    fun isActive(): Boolean
    fun post(count: Int, alert: Boolean)
    fun cancel()
}

/** Shared boundary for the daily worker and prompt updates from the live service data. */
class MaintenanceSummaryCoordinator(
    private val loadReminders: suspend () -> List<DerivedMaintenanceReminder>,
    private val notificationsEnabled: suspend () -> Boolean,
    private val canPost: () -> Boolean,
    private val stateStore: MaintenanceSummaryStateStore,
    private val gateway: MaintenanceSummaryNotificationGateway,
) {
    private val mutex = Mutex()

    suspend fun reconcile(allowAlert: Boolean) = mutex.withLock {
        if (!notificationsEnabled() || !canPost()) {
            clear()
            return@withLock
        }
        val initial = loadReminders().eligible()
        if (initial.isEmpty()) {
            clear()
            return@withLock
        }
        val previous = stateStore.read()
        val initialSignature = initial.signature()
        if (allowAlert && initialSignature == previous.alertedSignature) return@withLock
        if (!allowAlert && (!gateway.isActive() || initialSignature == previous.renderedSignature)) {
            return@withLock
        }

        // A preference or service write can finish after the first read and before notify().
        val latest = loadReminders().eligible()
        if (!notificationsEnabled() || !canPost() || latest.isEmpty()) {
            clear()
            return@withLock
        }
        val latestSignature = latest.signature()
        if (allowAlert) {
            if (latestSignature != previous.alertedSignature) {
                gateway.post(latest.size, alert = true)
                stateStore.write(MaintenanceSummaryNotificationState(latestSignature, latestSignature))
            }
        } else if (gateway.isActive() && latestSignature != previous.renderedSignature) {
            gateway.post(latest.size, alert = false)
            stateStore.write(previous.copy(renderedSignature = latestSignature))
        }
    }

    private suspend fun clear() {
        gateway.cancel()
        stateStore.write(MaintenanceSummaryNotificationState())
    }
}

private fun List<DerivedMaintenanceReminder>.eligible() = filter { reminder ->
    reminder.calculation.status == MaintenanceReminderStatus.UPCOMING ||
        reminder.calculation.status == MaintenanceReminderStatus.DUE ||
        reminder.calculation.status == MaintenanceReminderStatus.OVERDUE
}

private fun List<DerivedMaintenanceReminder>.signature(): String =
    sortedWith(compareBy<DerivedMaintenanceReminder> { it.vehicleId }.thenBy { it.serviceTypeId })
        .joinToString(";") { reminder ->
            listOf(
                reminder.vehicleId,
                reminder.serviceTypeId,
                reminder.calculation.status.name,
                reminder.calculation.nextMileage ?: "",
                reminder.calculation.nextDate ?: "",
            ).joinToString(":")
        }
