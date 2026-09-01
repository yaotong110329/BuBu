package com.kumo.bubu.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class MaintenanceReminderType { REPLACE, INSPECT, ESTIMATE }

enum class MaintenanceReminderStatus { UNKNOWN, NORMAL, UPCOMING, DUE, OVERDUE }

data class MaintenanceBaseline(
    val lastServiceMileage: Long?,
    val lastServiceDate: LocalDate?,
)

data class MaintenanceIntervalPolicy(
    val intervalMileage: Long?,
    val intervalMonths: Long?,
    val reminderType: MaintenanceReminderType,
    val reminderEnabled: Boolean,
)

data class MaintenanceReminderCalculation(
    val status: MaintenanceReminderStatus,
    val nextMileage: Long?,
    val nextDate: LocalDate?,
    val remainingMileage: Long?,
    val remainingDays: Long?,
)

/** A read-only reminder derived from the latest matching service item; it has no separate completion state. */
data class DerivedMaintenanceReminder(
    val vehicleId: Long,
    val serviceTypeId: Long,
    val serviceTypeName: String,
    val sortOrder: Int,
    val reminderType: MaintenanceReminderType,
    val baseline: MaintenanceBaseline,
    val usesConfiguredMileageBaseline: Boolean = false,
    val intervalMileage: Long?,
    val intervalMonths: Long?,
    val calculation: MaintenanceReminderCalculation,
)

/** The single status rule for derived maintenance reminders; service history owns the baseline. */
object MaintenanceReminderCalculator {
    fun calculate(
        baseline: MaintenanceBaseline,
        policy: MaintenanceIntervalPolicy,
        currentMileage: Long,
        today: LocalDate,
    ): MaintenanceReminderCalculation {
        val nextMileage = baseline.lastServiceMileage?.let { last ->
            policy.intervalMileage?.let { interval -> Math.addExact(last, interval) }
        }
        val nextDate = baseline.lastServiceDate?.let { last ->
            policy.intervalMonths?.let(last::plusMonths)
        }
        val remainingMileage = nextMileage?.minus(currentMileage)
        val remainingDays = nextDate?.let { ChronoUnit.DAYS.between(today, it) }
        val status = when {
            !policy.reminderEnabled -> MaintenanceReminderStatus.NORMAL
            nextMileage == null && nextDate == null -> MaintenanceReminderStatus.UNKNOWN
            (remainingMileage ?: 1) < 0 || (remainingDays ?: 1) < 0 -> MaintenanceReminderStatus.OVERDUE
            remainingMileage == 0L || remainingDays == 0L -> MaintenanceReminderStatus.DUE
            isUpcoming(remainingMileage, policy.intervalMileage) || (remainingDays != null && remainingDays <= 30) ->
                MaintenanceReminderStatus.UPCOMING
            else -> MaintenanceReminderStatus.NORMAL
        }
        return MaintenanceReminderCalculation(status, nextMileage, nextDate, remainingMileage, remainingDays)
    }

    private fun isUpcoming(remainingMileage: Long?, intervalMileage: Long?): Boolean =
        remainingMileage != null && intervalMileage != null && remainingMileage <= intervalMileage / 5
}
