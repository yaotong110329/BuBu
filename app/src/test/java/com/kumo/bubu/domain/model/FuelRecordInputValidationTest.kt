package com.kumo.bubu.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelRecordInputValidationTest {
    private val today = LocalDate.of(2026, 8, 2)

    @Test
    fun validInputKeepsIntegerBasedFuelAndMoneyValues() {
        val input = input().validated(today)

        assertEquals(4_270L, input.fuelVolumeMl)
        assertEquals(31_700L, input.pricePerLiterMilli)
        assertEquals(135L, input.totalCostTwd)
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureFuelDateIsRejected() {
        input(dateEpochDay = today.plusDays(1).toEpochDay()).validated(today)
    }

    @Test(expected = IllegalArgumentException::class)
    fun volumeGreaterThan999Point999LitersIsRejected() {
        input(fuelVolumeMl = 1_000_000L).validated(today)
    }

    private fun input(
        dateEpochDay: Long = today.toEpochDay(),
        fuelVolumeMl: Long = 4_270L,
    ) = FuelRecordInput(
        vehicleId = 3,
        dateEpochDay = dateEpochDay,
        timeMinuteOfDay = null,
        odometerKm = 1_234,
        fuelVolumeMl = fuelVolumeMl,
        pricePerLiterMilli = 31_700L,
        totalCostTwd = 135L,
        isFullTank = true,
    )
}
