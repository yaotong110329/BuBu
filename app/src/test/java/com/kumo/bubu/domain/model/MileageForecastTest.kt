package com.kumo.bubu.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MileageForecastTest {
    private val today = LocalDate.of(2026, 8, 16)

    @Test
    fun estimatesDueSoonNotificationFromRecentNinetyDayTravel() {
        val result = estimateMileageNotification(
            vehicleId = 1,
            dueOdometerKm = 3_000,
            today = today,
            observations = listOf(
                MileageObservation(1, LocalDate.of(2026, 5, 18).toEpochDay(), 1_000),
                MileageObservation(1, today.toEpochDay(), 1_900),
            ),
        )

        assertEquals(LocalDate.of(2026, 11, 14).toEpochDay(), result?.notificationDateEpochDay)
        assertEquals(90, result?.windowDays)
    }

    @Test
    fun expandsToOneHundredEightyDaysWhenNinetyDayWindowHasOnlyOnePoint() {
        val result = estimateMileageNotification(
            vehicleId = 1,
            dueOdometerKm = 2_300,
            today = today,
            observations = listOf(
                MileageObservation(1, LocalDate.of(2026, 3, 19).toEpochDay(), 1_000),
                MileageObservation(1, today.toEpochDay(), 1_600),
            ),
        )

        assertEquals(LocalDate.of(2026, 12, 19).toEpochDay(), result?.notificationDateEpochDay)
        assertEquals(180, result?.windowDays)
    }

    @Test
    fun ignoresOtherVehiclesAndRejectsNonPositiveOrShortTimelines() {
        assertNull(
            estimateMileageNotification(
                vehicleId = 1,
                dueOdometerKm = 2_000,
                today = today,
                observations = listOf(
                    MileageObservation(1, today.minusDays(30).toEpochDay(), 1_000),
                    MileageObservation(2, today.toEpochDay(), 1_500),
                ),
            ),
        )
        assertNull(
            estimateMileageNotification(
                vehicleId = 1,
                dueOdometerKm = 2_000,
                today = today,
                observations = listOf(
                    MileageObservation(1, today.minusDays(30).toEpochDay(), 1_500),
                    MileageObservation(1, today.toEpochDay(), 1_500),
                ),
            ),
        )
        assertNull(
            estimateMileageNotification(
                vehicleId = 1,
                dueOdometerKm = 2_000,
                today = today,
                observations = listOf(
                    MileageObservation(1, today.minusDays(13).toEpochDay(), 1_000),
                    MileageObservation(1, today.toEpochDay(), 1_500),
                ),
            ),
        )
    }

    @Test
    fun invalidOrExtremeMileageCannotOverflowIntoAForecast() {
        assertNull(
            estimateMileageNotification(
                vehicleId = 1,
                dueOdometerKm = Long.MAX_VALUE,
                today = today,
                observations = listOf(
                    MileageObservation(1, today.minusDays(30).toEpochDay(), -1),
                    MileageObservation(1, today.minusDays(29).toEpochDay(), 0),
                    MileageObservation(1, today.toEpochDay(), 1),
                ),
            ),
        )
    }

    @Test
    fun ambiguousOrRegressingSameDaySequenceIsNotForecast() {
        assertNull(
            estimateMileageNotification(
                vehicleId = 1,
                dueOdometerKm = 3_000,
                today = today,
                observations = listOf(
                    MileageObservation(1, today.minusDays(30).toEpochDay(), 1_000),
                    MileageObservation(1, today.toEpochDay(), 2_000, timeMinuteOfDay = 600),
                    MileageObservation(1, today.toEpochDay(), 1_500, timeMinuteOfDay = 660),
                ),
            ),
        )
        assertNull(
            estimateMileageNotification(
                vehicleId = 1,
                dueOdometerKm = 3_000,
                today = today,
                observations = listOf(
                    MileageObservation(1, today.minusDays(30).toEpochDay(), 1_000),
                    MileageObservation(1, today.toEpochDay(), 1_500),
                    MileageObservation(1, today.toEpochDay(), 1_600),
                ),
            ),
        )
    }

    @Test
    fun confirmedSameSourceSequenceCanOrderUnknownSameDayTimes() {
        val result = estimateMileageNotification(
            vehicleId = 1,
            dueOdometerKm = 3_000,
            today = today,
            observations = listOf(
                MileageObservation(1, today.minusDays(30).toEpochDay(), 1_000),
                MileageObservation(
                    1,
                    today.toEpochDay(),
                    1_500,
                    source = MileageObservationSource.FUEL,
                    sequenceInDay = 0,
                ),
                MileageObservation(
                    1,
                    today.toEpochDay(),
                    1_600,
                    source = MileageObservationSource.FUEL,
                    sequenceInDay = 1,
                ),
            ),
        )

        assertEquals(LocalDate.of(2026, 10, 15).toEpochDay(), result?.notificationDateEpochDay)
    }
}
