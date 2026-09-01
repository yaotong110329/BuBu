package com.kumo.bubu.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenanceReminderCalculatorTest {
    private val enabledReplace = MaintenanceIntervalPolicy(10_000, 6, MaintenanceReminderType.REPLACE, true)
    private val today = LocalDate.of(2026, 8, 24)

    @Test fun `unknown without a service baseline`() =
        assertEquals(MaintenanceReminderStatus.UNKNOWN, calculate(MaintenanceBaseline(null, null)).status)

    @Test fun `date threshold makes a reminder upcoming before mileage`() =
        assertEquals(
            MaintenanceReminderStatus.UPCOMING,
            calculate(MaintenanceBaseline(52_000, LocalDate.of(2026, 3, 13)), currentMileage = 55_000).status,
        )

    @Test fun `equal mileage is due and greater mileage is overdue`() {
        assertEquals(MaintenanceReminderStatus.DUE, calculate(MaintenanceBaseline(52_000, null), 62_000).status)
        assertEquals(MaintenanceReminderStatus.OVERDUE, calculate(MaintenanceBaseline(52_000, null), 62_001).status)
    }

    @Test fun `built in items use their inspection or estimate semantics`() {
        assertEquals(MaintenanceReminderType.INSPECT, serviceType("builtin-car-brake-pads").defaultMaintenanceReminderType())
        assertEquals(MaintenanceReminderType.ESTIMATE, serviceType("builtin-car-tires").defaultMaintenanceReminderType())
        assertEquals(MaintenanceReminderType.REPLACE, serviceType("builtin-car-engine-oil").defaultMaintenanceReminderType())
    }

    private fun calculate(baseline: MaintenanceBaseline, currentMileage: Long = 55_000) =
        MaintenanceReminderCalculator.calculate(baseline, enabledReplace, currentMileage, today)

    private fun serviceType(publicId: String) = ServiceType(
        id = 1L,
        publicId = publicId,
        name = publicId,
        isBuiltIn = true,
        isArchived = false,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
