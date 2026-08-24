package com.kumo.bubu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {
    @Test
    fun fuelEconomyPointsAreCalculatedPerVehicleWithoutCrossVehicleAnchors() {
        val points = calculateReportFuelEconomyPoints(
            listOf(
                record(id = 1, vehicleId = 1, day = 1, odometerKm = 1_000, volumeMl = 5_000),
                record(id = 2, vehicleId = 2, day = 1, odometerKm = 5_000, volumeMl = 5_000),
                record(id = 3, vehicleId = 1, day = 2, odometerKm = 1_100, volumeMl = 10_000),
                record(id = 4, vehicleId = 2, day = 2, odometerKm = 5_200, volumeMl = 20_000),
            ),
        )

        assertEquals(2, points.size)
        assertEquals(10_000L, points.single { it.vehicleId == 1L }.milliKmPerLiter)
        assertEquals(10_000L, points.single { it.vehicleId == 2L }.milliKmPerLiter)
    }

    @Test
    fun weightedFuelEconomyUsesTotalDistanceAndVolumeInsteadOfAveragingPointValues() {
        val average = calculateWeightedFuelEconomyMilliKmPerLiter(
            listOf(
                ReportFuelEconomyPoint(1, 1, 1, 20_000, 100, 5_000),
                ReportFuelEconomyPoint(1, 2, 2, 10_000, 100, 10_000),
            ),
        )

        assertEquals(13_333L, average)
    }

    @Test
    fun nonPositiveDistanceDoesNotCreateAMisleadingFuelEconomyPoint() {
        val points = calculateReportFuelEconomyPoints(
            listOf(
                record(id = 1, vehicleId = 1, day = 1, odometerKm = 1_000, volumeMl = 5_000),
                record(id = 2, vehicleId = 1, day = 2, odometerKm = 1_000, volumeMl = 10_000),
            ),
        )

        assertTrue(points.isEmpty())
    }

    @Test
    fun excludedSegmentIsOmittedWithoutChangingTheFollowingSegment() {
        val points = calculateReportFuelEconomyPoints(
            listOf(
                record(id = 1, vehicleId = 1, day = 1, odometerKm = 1_000, volumeMl = 5_000),
                record(id = 2, vehicleId = 1, day = 2, odometerKm = 1_100, volumeMl = 10_000)
                    .copy(fuelEconomyStatisticsStatus = FuelEconomyStatisticsStatus.EXCLUDED),
                record(id = 3, vehicleId = 1, day = 3, odometerKm = 1_200, volumeMl = 10_000),
            ),
        )

        assertEquals(1, points.size)
        assertEquals(3L, points.single().fuelRecordId)
        assertEquals(10_000L, points.single().milliKmPerLiter)
    }

    private fun record(
        id: Long,
        vehicleId: Long,
        day: Long,
        odometerKm: Long,
        volumeMl: Long,
    ) = FuelRecord(
        id = id,
        publicId = "fuel-$id",
        vehicleId = vehicleId,
        dateEpochDay = day,
        timeMinuteOfDay = null,
        sequenceInDay = 0,
        odometerKm = odometerKm,
        fuelVolumeMl = volumeMl,
        pricePerLiterMilli = null,
        totalCostTwd = 0,
        isFullTank = true,
        fuelProduct = null,
        note = null,
        createdAt = id,
        updatedAt = id,
    )
}
