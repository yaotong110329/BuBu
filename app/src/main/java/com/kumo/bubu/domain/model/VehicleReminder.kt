package com.kumo.bubu.domain.model

import java.time.LocalDate

enum class ReminderSource {
    SERVICE_ITEM,
    MANUAL,
    LICENSE_TAX,
    ROAD_MAINTENANCE_FEE,
    PERIODIC_INSPECTION,
}

enum class ReminderStatus {
    NORMAL,
    DUE_SOON,
    OVERDUE,
}

data class VehicleReminder(
    val id: Long,
    val publicId: String,
    val vehicleId: Long,
    val source: ReminderSource,
    val sourceServiceItemId: Long?,
    val title: String,
    val dueOdometerKm: Long?,
    val dueDateEpochDay: Long?,
    val completedByServiceRecordId: Long?,
    val completedAt: Long?,
    val snoozedUntilEpochDay: Long?,
    val lastNotifiedStatus: ReminderStatus? = null,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val automaticKey: String? = null,
    val ruleVersion: Int? = null,
    val ruleVerifiedEpochDay: Long? = null,
    val estimatedNotificationEpochDay: Long? = null,
    val lastNotifiedTrigger: String? = null,
    val referenceDateEpochDay: Long? = null,
    val completedByExpenseRecordId: Long? = null,
) {
    val isCompleted: Boolean get() = completedAt != null
}

data class ManualReminderInput(
    val vehicleId: Long,
    val title: String,
    val dueOdometerKm: Long?,
    val dueDateEpochDay: Long?,
)

fun ManualReminderInput.validated(): ManualReminderInput {
    require(vehicleId > 0) { "A vehicle is required." }
    require(title.isNotBlank()) { "A reminder title is required." }
    require(dueOdometerKm != null || dueDateEpochDay != null) { "A due date or odometer is required." }
    require(dueOdometerKm == null || dueOdometerKm >= 0) { "Due odometer cannot be negative." }
    return copy(title = title.trim())
}

fun VehicleReminder.status(
    currentOdometerKm: Long,
    today: LocalDate,
): ReminderStatus? {
    if (isCompleted) return null
    val kilometerStatus = dueOdometerKm?.let { due ->
        when {
            currentOdometerKm >= due -> ReminderStatus.OVERDUE
            due - currentOdometerKm <= DUE_SOON_KILOMETERS -> ReminderStatus.DUE_SOON
            else -> ReminderStatus.NORMAL
        }
    }
    val dateStatus = dueDateEpochDay?.let { due ->
        val remainingDays = due - today.toEpochDay()
        when {
            remainingDays < 0 -> ReminderStatus.OVERDUE
            remainingDays <= DUE_SOON_DAYS -> ReminderStatus.DUE_SOON
            else -> ReminderStatus.NORMAL
        }
    }
    return listOfNotNull(kilometerStatus, dateStatus).maxByOrNull(ReminderStatus::severity)
}

private const val DUE_SOON_KILOMETERS = 200L
private const val DUE_SOON_DAYS = 7L

private val ReminderStatus.severity: Int
    get() = when (this) {
        ReminderStatus.NORMAL -> 0
        ReminderStatus.DUE_SOON -> 1
        ReminderStatus.OVERDUE -> 2
    }
