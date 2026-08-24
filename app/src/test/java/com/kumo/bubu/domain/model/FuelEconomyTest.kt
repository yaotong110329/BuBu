package com.kumo.bubu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun fuelIntervalPredictionUsesMedianOfRecentDistinctDays() {
        val prediction = estimateFuelInterval(
            vehicleId = 1,
            todayEpochDay = 30,
            records = listOf(
                record(1, 1, 1_000, 1_000, true),
                record(2, 11, 1_100, 1_000, true),
                record(3, 20, 1_200, 1_000, true),
                record(4, 20, 1_201, 1_000, true),
                record(5, 28, 1_300, 1_000, true),
            ),
        )

        assertEquals(7L, prediction?.daysUntilNextFuel)
        assertEquals(3, prediction?.sampleCount)
    }

    @Test
    fun outlierRequiresAValidFullTankCycleAndEnoughVehicleHistory() {
        val history = listOf(
            record(1, 1, 1_000, 1_000, true),
            record(2, 2, 1_010, 1_000, true),
            record(3, 3, 1_020, 1_000, true),
            record(4, 4, 1_030, 1_000, true),
            record(5, 5, 1_040, 1_000, true),
        )

        assertNotNull(detectFuelEconomyOutlier(history, record(6, 6, 1_140, 1_000, true)))
        assertNull(detectFuelEconomyOutlier(history, record(7, 7, 1_150, 1_000, false)))
    }

    @Test
    fun excludedSegmentDoesNotAffectStatisticsButStillBecomesTheNextAnchor() {
        val records = listOf(
            record(1, 1, 1_000, 1_000, true),
            record(2, 2, 1_100, 10_000, true, FuelEconomyStatisticsStatus.EXCLUDED),
            record(3, 3, 1_200, 10_000, true),
        )

        assertEquals(mapOf(3L to 10_000L), calculateFuelEconomyMilliKmPerLiterByRecord(records))
        assertEquals(10_000L, calculateRecentAverageFuelEconomyMilliKmPerLiter(records))
    }

    @Test
    fun historicalOutliersRemainCandidatesUntilTheUserReviewsThem() {
        val records = listOf(
            record(1, 1, 1_000, 1_000, true),
            record(2, 2, 1_010, 1_000, true),
            record(3, 3, 1_020, 1_000, true),
            record(4, 4, 1_030, 1_000, true),
            record(5, 5, 1_040, 1_000, true),
            record(6, 6, 1_140, 1_000, true),
        )

        assertEquals(listOf(6L), detectFuelEconomyOutlierCandidates(records).map(FuelEconomyOutlierCandidate::fuelRecordId))
    }

    @Test
    fun normalTwentyFivePercentVariationIsNotAddedToTheReviewListButExtremeVariationIs() {
        val baseline = listOf(
            record(1, 1, 0, 10_000, true),
            record(2, 2, 100, 10_000, true),
            record(3, 3, 200, 10_000, true),
            record(4, 4, 300, 10_000, true),
            record(5, 5, 400, 10_000, true),
        )

        assertNull(detectFuelEconomyOutlier(baseline, record(6, 6, 525, 10_000, true)))
        assertNotNull(detectFuelEconomyOutlier(baseline, record(7, 7, 550, 10_000, true)))
    }

    @Test
    fun outlierSeverityUsesRelativeDeviationSoDifferentVehicleEconomiesCanBePrioritized() {
        assertEquals(500L, FuelEconomyOutlier(15_000L, 10_000L).deviationPermille())
        assertEquals(250L, FuelEconomyOutlier(25_000L, 20_000L).deviationPermille())
    }

    private fun record(
        id: Long,
        day: Long,
        odometer: Long,
        volumeMl: Long,
        isFullTank: Boolean,
        fuelEconomyStatisticsStatus: FuelEconomyStatisticsStatus = FuelEconomyStatisticsStatus.UNREVIEWED,
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
        fuelEconomyStatisticsStatus = fuelEconomyStatisticsStatus,
        note = null,
        createdAt = id,
        updatedAt = id,
    )
}
