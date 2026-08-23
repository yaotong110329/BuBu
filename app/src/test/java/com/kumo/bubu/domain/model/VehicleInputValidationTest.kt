package com.kumo.bubu.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleInputValidationTest {
    private val today = LocalDate.of(2026, 8, 2)

    @Test(expected = IllegalArgumentException::class)
    fun blankNameIsRejectedAtDomainBoundary() {
        validInput().copy(name = "   ").validated(today)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeOdometerIsRejectedAtDomainBoundary() {
        validInput().copy(trackingStartOdometerKm = -1).validated(today)
    }

    @Test
    fun carIsNormalizedWithoutMotorcycleClass() {
        val validated = validInput().copy(motorcycleClass = MotorcycleClass.LIGHT).validated(today)

        assertEquals("家用車", validated.name)
        assertNull(validated.motorcycleClass)
    }

    private fun validInput() = VehicleInput(
        name = " 家用車 ",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        trackingStartDateEpochDay = today.toEpochDay(),
        trackingStartOdometerKm = 0,
    )
}
