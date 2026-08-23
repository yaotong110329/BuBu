package com.kumo.bubu.domain.model

import java.time.LocalDate
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaiwanStatutoryReminderRulesTest {
    private val today = LocalDate.of(2026, 8, 16)

    @Test
    fun createsAnnualTaxFeeAndOneInspectionForEligibleSixYearOldCar() {
        val plans = taiwanStatutoryReminderPlans(
            profile = StatutoryVehicleProfile(
                vehicleId = 1,
                vehicleType = VehicleType.CAR,
                motorcycleClass = null,
                engineDisplacementCc = 1_800,
                manufactureYear = 2020,
                primaryInspectionMonthDay = MonthDay.of(10, 20),
                secondaryInspectionMonthDay = null,
                isArchived = false,
            ),
            today = today,
        )

        assertEquals(
            listOf(
                StatutoryReminderKind.LICENSE_TAX,
                StatutoryReminderKind.ROAD_MAINTENANCE_FEE,
                StatutoryReminderKind.PERIODIC_INSPECTION_PRIMARY,
            ),
            plans.map(StatutoryReminderPlan::kind),
        )
        assertEquals(LocalDate.of(2026, 4, 30).toEpochDay(), plans[0].dueDateEpochDay)
        assertEquals(LocalDate.of(2026, 7, 31).toEpochDay(), plans[1].dueDateEpochDay)
        assertEquals(LocalDate.of(2026, 10, 20).toEpochDay(), plans[2].referenceDateEpochDay)
        assertEquals(LocalDate.of(2026, 11, 20).toEpochDay(), plans[2].dueDateEpochDay)
    }

    @Test
    fun requiresTwoSeparatelyConfirmedDatesForTenYearOldLargeMotorcycle() {
        val plans = taiwanStatutoryReminderPlans(
            profile = StatutoryVehicleProfile(
                vehicleId = 2,
                vehicleType = VehicleType.MOTORCYCLE,
                motorcycleClass = MotorcycleClass.LARGE_HEAVY,
                engineDisplacementCc = 550,
                manufactureYear = 2016,
                primaryInspectionMonthDay = MonthDay.of(3, 8),
                secondaryInspectionMonthDay = MonthDay.of(9, 12),
                isArchived = false,
            ),
            today = today,
        )

        assertEquals(4, plans.size)
        assertEquals(
            listOf(LocalDate.of(2026, 3, 8).toEpochDay(), LocalDate.of(2026, 9, 12).toEpochDay()),
            plans.filter { it.kind.name.startsWith("PERIODIC_INSPECTION") }.map { it.referenceDateEpochDay },
        )
    }

    @Test
    fun doesNotCreateInspectionForYoungMissingOrIneligibleVehicles() {
        val profiles = listOf(
            profile(vehicleType = VehicleType.CAR, manufactureYear = 2023),
            profile(vehicleType = VehicleType.CAR, manufactureYear = null),
            profile(
                vehicleType = VehicleType.MOTORCYCLE,
                motorcycleClass = MotorcycleClass.ORDINARY_HEAVY,
                manufactureYear = 2010,
            ),
            profile(vehicleType = VehicleType.CAR, manufactureYear = 2010, isArchived = true),
        )

        assertTrue(
            profiles.all { profile ->
                taiwanStatutoryReminderPlans(profile, today)
                    .none { it.kind.name.startsWith("PERIODIC_INSPECTION") }
            },
        )
        assertTrue(taiwanStatutoryReminderPlans(profiles.last(), today).isEmpty())
    }

    private fun profile(
        vehicleType: VehicleType,
        motorcycleClass: MotorcycleClass? = null,
        manufactureYear: Int?,
        isArchived: Boolean = false,
    ) = StatutoryVehicleProfile(
        vehicleId = 1,
        vehicleType = vehicleType,
        motorcycleClass = motorcycleClass,
        engineDisplacementCc = 125,
        manufactureYear = manufactureYear,
        primaryInspectionMonthDay = MonthDay.of(5, 20),
        secondaryInspectionMonthDay = MonthDay.of(11, 20),
        isArchived = isArchived,
    )
}
