package com.kumo.bubu.domain.model

import java.math.BigInteger
import java.time.LocalDate

enum class MileageObservationSource {
    TRACKING_START,
    FUEL,
    SERVICE,
    UNKNOWN,
}

data class MileageObservation(
    val vehicleId: Long,
    val dateEpochDay: Long,
    val odometerKm: Long,
    val timeMinuteOfDay: Int? = null,
    val source: MileageObservationSource = MileageObservationSource.UNKNOWN,
    val sequenceInDay: Int = 0,
)

data class MileageForecast(
    val notificationDateEpochDay: Long,
    val windowDays: Int,
)

fun estimateMileageNotification(
    vehicleId: Long,
    dueOdometerKm: Long,
    today: LocalDate,
    observations: List<MileageObservation>,
): MileageForecast? = listOf(90, 180).firstNotNullOfOrNull { windowDays ->
    estimateWithinWindow(vehicleId, dueOdometerKm, today, observations, windowDays)
}

private fun estimateWithinWindow(
    vehicleId: Long,
    dueOdometerKm: Long,
    today: LocalDate,
    observations: List<MileageObservation>,
    windowDays: Int,
): MileageForecast? {
    val todayEpochDay = today.toEpochDay()
    val startEpochDay = today.minusDays(windowDays.toLong()).toEpochDay()
    val candidatePoints = observations
        .asSequence()
        .filter { it.vehicleId == vehicleId }
        .filter { it.odometerKm >= 0 }
        .filter { it.dateEpochDay in startEpochDay..todayEpochDay }
        .toList()
    val points = mutableListOf<MileageObservation>()
    val observationsByDay = candidatePoints
        .groupBy(MileageObservation::dateEpochDay)
        .toSortedMap()
    for (sameDay in observationsByDay.values) {
        val orderedSameDay = orderedSameDayOrNull(sameDay) ?: return null
        points.addAll(orderedSameDay)
    }
    if (points.size < 2) return null
    if (points.zipWithNext().any { (previous, next) -> next.odometerKm <= previous.odometerKm }) return null

    val first = points.first()
    val latest = points.last()
    val spanDays = latest.dateEpochDay - first.dateEpochDay
    if (spanDays < MINIMUM_SPAN_DAYS) return null
    val traveledKm = runCatching { Math.subtractExact(latest.odometerKm, first.odometerKm) }.getOrNull()
        ?: return null
    val dueSoonOdometerKm = runCatching { Math.subtractExact(dueOdometerKm, DUE_SOON_KILOMETERS) }
        .getOrNull() ?: return null
    val remainingKm = runCatching { Math.subtractExact(dueSoonOdometerKm, latest.odometerKm) }.getOrNull()
        ?: return null
    if (traveledKm <= 0 || remainingKm <= 0) return null

    val numerator = BigInteger.valueOf(remainingKm).multiply(BigInteger.valueOf(spanDays))
    val denominator = BigInteger.valueOf(traveledKm)
    val daysUntilDueSoonBig = numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator)
    if (daysUntilDueSoonBig > BigInteger.valueOf(Long.MAX_VALUE)) return null
    val daysUntilDueSoon = daysUntilDueSoonBig.toLong()
    val estimatedEpochDay = runCatching { Math.addExact(latest.dateEpochDay, daysUntilDueSoon) }.getOrNull()
        ?: return null
    return MileageForecast(
        notificationDateEpochDay = maxOf(todayEpochDay, estimatedEpochDay),
        windowDays = windowDays,
    )
}

private fun orderedSameDayOrNull(
    sameDay: List<MileageObservation>,
): List<MileageObservation>? {
    if (sameDay.size <= 1) return sameDay
    val allTimesKnown = sameDay.all { it.timeMinuteOfDay != null }
    if (allTimesKnown) {
        val ambiguousTie = sameDay.groupBy(MileageObservation::timeMinuteOfDay).values.any { sameMinute ->
            sameMinute.size > 1 && (
                sameMinute.map(MileageObservation::source).distinct().size != 1 ||
                    sameMinute.map(MileageObservation::sequenceInDay).distinct().size != sameMinute.size
                )
        }
        if (ambiguousTie) return null
        return sameDay.sortedWith(
            compareBy<MileageObservation> { it.timeMinuteOfDay }
                .thenBy(MileageObservation::sequenceInDay),
        )
    }
    val singleConfirmedSource = sameDay.map(MileageObservation::source).distinct().singleOrNull()
    val confirmedSequences = sameDay.map(MileageObservation::sequenceInDay)
    if (sameDay.any { it.timeMinuteOfDay != null } ||
        singleConfirmedSource == null || singleConfirmedSource == MileageObservationSource.UNKNOWN ||
        confirmedSequences.distinct().size != sameDay.size
    ) {
        return null
    }
    return sameDay.sortedBy(MileageObservation::sequenceInDay)
}

private const val MINIMUM_SPAN_DAYS = 14L
private const val DUE_SOON_KILOMETERS = 200L
