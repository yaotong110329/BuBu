package com.kumo.bubu.core.notification

import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import java.time.LocalDate

internal data class ReminderNotificationTrigger(
    val key: String,
    val notificationStatus: ReminderStatus,
)

internal fun shouldPostReminderNotification(
    status: ReminderStatus?,
    lastNotifiedStatus: ReminderStatus?,
    isArchived: Boolean,
    isSnoozed: Boolean,
): Boolean =
    !isArchived && !isSnoozed && status != null && status != ReminderStatus.NORMAL &&
        status != lastNotifiedStatus

internal fun selectReminderNotificationTrigger(
    source: ReminderSource,
    status: ReminderStatus?,
    dueDateEpochDay: Long?,
    referenceDateEpochDay: Long?,
    estimatedNotificationEpochDay: Long?,
    lastNotifiedTrigger: String?,
    today: LocalDate,
    isEnabled: Boolean,
    isCompleted: Boolean,
    isArchived: Boolean,
    isSnoozed: Boolean,
): ReminderNotificationTrigger? {
    if (!isEnabled || isCompleted || isArchived || isSnoozed) return null
    val todayEpochDay = today.toEpochDay()
    if (status == ReminderStatus.OVERDUE) {
        return ReminderNotificationTrigger("status:${ReminderStatus.OVERDUE.name}", ReminderStatus.OVERDUE)
            .takeIf { it.key != lastNotifiedTrigger }
    }
    val statutoryTrigger = when (source) {
        ReminderSource.LICENSE_TAX -> dueDateEpochDay?.let { licenseTaxTrigger(LocalDate.ofEpochDay(it), today) }
        ReminderSource.ROAD_MAINTENANCE_FEE -> dueDateEpochDay?.let {
            roadMaintenanceFeeTrigger(LocalDate.ofEpochDay(it), today)
        }
        ReminderSource.PERIODIC_INSPECTION -> referenceDateEpochDay?.let {
            inspectionTrigger(LocalDate.ofEpochDay(it), today)
        }
        ReminderSource.SERVICE_ITEM,
        ReminderSource.MANUAL,
        -> null
    }
    if (statutoryTrigger != null) {
        return statutoryTrigger.takeIf { it.key != lastNotifiedTrigger }
    }
    if (status == ReminderStatus.NORMAL && estimatedNotificationEpochDay != null &&
        todayEpochDay >= estimatedNotificationEpochDay
    ) {
        return ReminderNotificationTrigger("mileage-forecast", ReminderStatus.DUE_SOON)
            .takeIf { it.key != lastNotifiedTrigger }
    }
    if (status == ReminderStatus.DUE_SOON || status == ReminderStatus.OVERDUE) {
        if (status == ReminderStatus.DUE_SOON && lastNotifiedTrigger == MILEAGE_FORECAST_TRIGGER) {
            return null
        }
        val trigger = ReminderNotificationTrigger("status:${status.name}", status)
        return trigger.takeIf { it.key != lastNotifiedTrigger }
    }
    return null
}

private const val MILEAGE_FORECAST_TRIGGER = "mileage-forecast"

private fun licenseTaxTrigger(dueDate: LocalDate, today: LocalDate): ReminderNotificationTrigger? =
    latestReachedTrigger(
        today,
        listOf(
            LocalDate.of(dueDate.year, 3, 25) to "statutory:license-tax:preview",
            LocalDate.of(dueDate.year, 4, 1) to "statutory:license-tax:open",
            LocalDate.of(dueDate.year, 4, 23) to "statutory:license-tax:follow-up",
        ),
    )

private fun roadMaintenanceFeeTrigger(dueDate: LocalDate, today: LocalDate): ReminderNotificationTrigger? =
    latestReachedTrigger(
        today,
        listOf(
            LocalDate.of(dueDate.year, 6, 24) to "statutory:road-fee:preview",
            LocalDate.of(dueDate.year, 7, 1) to "statutory:road-fee:open",
            LocalDate.of(dueDate.year, 7, 24) to "statutory:road-fee:follow-up",
        ),
    )

private fun inspectionTrigger(referenceDate: LocalDate, today: LocalDate): ReminderNotificationTrigger? =
    latestReachedTrigger(
        today,
        listOf(
            referenceDate.minusMonths(1) to "statutory:inspection:window-open",
            referenceDate.minusDays(7) to "statutory:inspection:seven-days",
            referenceDate.plusMonths(1).minusDays(7) to "statutory:inspection:urgent",
        ),
    )

private fun latestReachedTrigger(
    today: LocalDate,
    triggers: List<Pair<LocalDate, String>>,
): ReminderNotificationTrigger? = triggers
    .lastOrNull { (date, _) -> !date.isAfter(today) }
    ?.let { (_, key) -> ReminderNotificationTrigger(key, ReminderStatus.DUE_SOON) }
