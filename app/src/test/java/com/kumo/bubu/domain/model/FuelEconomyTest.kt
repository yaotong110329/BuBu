package com.kumo.bubu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FuelEconomyTest {
    @Test
    fun outlierUsesRecentEightSegmentsInsteadOfTheEightHighestEconomies() {
        val history = (0..16).map { index ->
            val distance = if (index <= 8) index * 300L else 2_400L + (index - 8) * 100L
            record(index + 1L, index + 1L, distance, 10_000, true)
        }

        assertNull(detectFuelEconomyOutlier(history, record(18, 18, 3_300, 10_000, true)))
    }

    @Test
    fun recentWindowUsesStableSameDayTimeSequenceAndIdOrdering() {
        val history = (0..16).map { index ->
            val distance = if (index <= 8) index * 300L else 2_400L + (index - 8) * 100L
            record(index + 1L, 1, distance, 10_000, true)
        }
        val byTime = history.mapIndexed { index, fuel ->
            fuel.copy(timeMinuteOfDay = if (index <= 8) index * 10 else null, sequenceInDay = index)
        }
        val bySequence = history.mapIndexed { index, fuel ->
            fuel.copy(id = 100L - index, timeMinuteOfDay = 0, sequenceInDay = index)
        }
        val byId = history.map { it.copy(timeMinuteOfDay = 0) }

        listOf(byTime, bySequence, byId).forEachIndexed { index, ordered ->
            val candidate = record(1_000, 1, 3_500, 10_000, true).copy(
                timeMinuteOfDay = if (index == 0) null else 0,
                sequenceInDay = Int.MAX_VALUE,
            )
            assertEquals(FuelEconomyOutlier(30_000, 10_000), detectFuelEconomyOutlier(ordered.reversed(), candidate))
        }
    }

    @Test
    fun shortHistoryNeedsFourPriorSegmentsWithoutCountingTheCandidate() {
        val history = (0..4).map { index -> record(index + 1L, index + 1L, index * 100L, 10_000, true) }

        assertNull(detectFuelEconomyOutlier(history.take(4), record(9, 9, 700, 10_000, true)))
        assertEquals(FuelEconomyOutlier(40_000, 10_000),
            detectFuelEconomyOutlier(history, record(9, 9, 800, 10_000, true)))
    }

    @Test
    fun excludedRecentSegmentsLeaveTheValidBaselineAndNextFullTankAnchorIntact() {
        val history = (0..12).map { index ->
            val excluded = index > 4
            val distance = if (excluded) 400L + (index - 4) * 300L else index * 100L
            record(index + 1L, index + 1L, distance, 10_000, true,
                if (excluded) FuelEconomyStatisticsStatus.EXCLUDED else FuelEconomyStatisticsStatus.INCLUDED)
        }
        val candidate = record(14, 14, 2_900, 10_000, true)

        assertNull(detectFuelEconomyOutlier(history, candidate))
        assertEquals(10_000L, calculateFuelEconomyMilliKmPerLiterByRecord(history + candidate)[candidate.id])
        assertEquals(emptyList<FuelEconomyOutlierCandidate>(), detectFuelEconomyOutlierCandidates(history + candidate))
    }

    @Test
    fun reviewCandidatesKeepTwoVehiclesWithDifferentEconomiesIsolated() {
        val firstVehicle = (0..5).map { index ->
            record(index + 1L, index + 1L, if (index == 5) 700 else index * 100L, 10_000, true)
        }
        val secondVehicle = (0..8).map { index ->
            record(index + 100L, index + 1L, index * 300L, 10_000, true).copy(vehicleId = 2)
        }

        assertEquals(listOf(FuelEconomyOutlierCandidate(6, FuelEconomyOutlier(30_000, 10_000))),
            detectFuelEconomyOutlierCandidates((firstVehicle + secondVehicle).reversed()))
    }

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
