package com.kumo.bubu.domain.model

import java.time.LocalDate
import java.time.MonthDay

val taiwanStatutoryRuleVerifiedDate: LocalDate = LocalDate.of(2026, 8, 16)

enum class StatutoryReminderKind {
    LICENSE_TAX,
    ROAD_MAINTENANCE_FEE,
    PERIODIC_INSPECTION_PRIMARY,
    PERIODIC_INSPECTION_SECONDARY,
}

data class StatutoryVehicleProfile(
    val vehicleId: Long,
    val vehicleType: VehicleType,
    val motorcycleClass: MotorcycleClass?,
    val engineDisplacementCc: Int?,
    val manufactureYear: Int?,
    val primaryInspectionMonthDay: MonthDay?,
    val secondaryInspectionMonthDay: MonthDay?,
    val isArchived: Boolean,
)

data class StatutoryReminderPlan(
    val kind: StatutoryReminderKind,
    val automaticKey: String,
    val dueDateEpochDay: Long,
    val referenceDateEpochDay: Long? = null,
)

fun taiwanStatutoryReminderPlans(
    profile: StatutoryVehicleProfile,
    today: LocalDate,
): List<StatutoryReminderPlan> {
    if (profile.isArchived) return emptyList()
    val year = today.year
    return buildList {
        if (profile.isLicenseTaxEligible()) {
            add(plan(profile.vehicleId, StatutoryReminderKind.LICENSE_TAX, year, MonthDay.of(4, 30)))
        }
        add(plan(profile.vehicleId, StatutoryReminderKind.ROAD_MAINTENANCE_FEE, year, MonthDay.of(7, 31)))

        val inspectionCount = profile.inspectionsPerYear(year)
        if (inspectionCount >= 1) {
            profile.primaryInspectionMonthDay?.let { monthDay ->
                add(plan(profile.vehicleId, StatutoryReminderKind.PERIODIC_INSPECTION_PRIMARY, year, monthDay))
            }
        }
        if (inspectionCount >= 2) {
            profile.secondaryInspectionMonthDay?.let { monthDay ->
                add(plan(profile.vehicleId, StatutoryReminderKind.PERIODIC_INSPECTION_SECONDARY, year, monthDay))
            }
        }
    }
}

private fun StatutoryVehicleProfile.isLicenseTaxEligible(): Boolean = when (vehicleType) {
    VehicleType.CAR -> true
    VehicleType.MOTORCYCLE -> motorcycleClass == MotorcycleClass.LARGE_HEAVY ||
        (engineDisplacementCc ?: 0) >= 151
}

private fun StatutoryVehicleProfile.inspectionsPerYear(year: Int): Int {
    val eligibleType = vehicleType == VehicleType.CAR ||
        (vehicleType == VehicleType.MOTORCYCLE && motorcycleClass == MotorcycleClass.LARGE_HEAVY)
    val manufactured = manufactureYear ?: return 0
    if (!eligibleType || manufactured > year) return 0
    val age = year - manufactured
    return when {
        age < 5 -> 0
        age < 10 -> 1
        else -> 2
    }
}

private fun plan(
    vehicleId: Long,
    kind: StatutoryReminderKind,
    year: Int,
    monthDay: MonthDay,
): StatutoryReminderPlan {
    val referenceDate = monthDay.atYear(year)
    val dueDate = if (kind == StatutoryReminderKind.PERIODIC_INSPECTION_PRIMARY ||
        kind == StatutoryReminderKind.PERIODIC_INSPECTION_SECONDARY
    ) {
        referenceDate.plusMonths(1)
    } else {
        referenceDate
    }
    return StatutoryReminderPlan(
        kind = kind,
        automaticKey = "statutory:$vehicleId:${kind.name}:$year",
        dueDateEpochDay = dueDate.toEpochDay(),
        referenceDateEpochDay = referenceDate.toEpochDay().takeIf {
            kind == StatutoryReminderKind.PERIODIC_INSPECTION_PRIMARY ||
                kind == StatutoryReminderKind.PERIODIC_INSPECTION_SECONDARY
        },
    )
}
