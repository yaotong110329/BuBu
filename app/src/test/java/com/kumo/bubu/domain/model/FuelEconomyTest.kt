package com.kumo.bubu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelEconomyTest {
    @Test
    fun recentAverageUsesTheLastThreeValidFullTankCycles() {
        val average = calculateRecentAverageFuelEconomyMilliKmPerLiter(
            listOf(
                record(id = 1, day = 1, odometer = 1_000, volumeMl = 3_000, isFullTank = true),
                record(id = 2, day = 2, odometer = 1_100, volumeMl = 10_000, isFullTank = true),
                record(id = 3, day = 3, odometer = 1_200, volumeMl = 10_000, isFullTank = true),
                record(id = 4, day = 4, odometer = 1_300, volumeMl = 10_000, isFullTank = true),
                record(id = 5, day = 5, odometer = 1_400, volumeMl = 10_000, isFullTank = true),
            ),
        )

        assertEquals(10_000L, average)
    }

    @Test
    fun recentAverageIsUnavailableWithoutAValidFullTankCycle() {
        val average = calculateRecentAverageFuelEconomyMilliKmPerLiter(
            listOf(record(id = 1, day = 1, odometer = 1_000, volumeMl = 10_000, isFullTank = true)),
        )

        assertNull(average)
    }

    private fun record(
        id: Long,
        day: Long,
        odometer: Long,
        volumeMl: Long,
        isFullTank: Boolean,
    ) = FuelRecord(
        id = id,
        publicId = "fuel-$id",
        vehicleId = 1,
        dateEpochDay = day,
        timeMinuteOfDay = null,
        sequenceInDay = 0,
        odometerKm = odometer,
        fuelVolumeMl = volumeMl,
        pricePerLiterMilli = null,
        totalCostTwd = 0,
        isFullTank = isFullTank,
        fuelProduct = null,
        note = null,
        createdAt = id,
        updatedAt = id,
    )
}
