package com.kumo.bubu.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

private const val MAX_RECENT_FUEL_ECONOMY_CYCLES = 3
private const val FUEL_ECONOMY_SCALE = 1_000L

/** Returns the recent weighted km/L value scaled to three decimal places, or null when data is insufficient. */
fun calculateRecentAverageFuelEconomyMilliKmPerLiter(
    records: List<FuelRecord>,
): Long? {
    val cycles = mutableListOf<FuelEconomyCycle>()
    var anchor: FuelRecord? = null
    var accumulatedVolumeMl = 0L

    records.sortedWith(FUEL_RECORD_ORDER).forEach { record ->
        val previousAnchor = anchor
        if (previousAnchor != null) {
            accumulatedVolumeMl = runCatching { Math.addExact(accumulatedVolumeMl, record.fuelVolumeMl) }
                .getOrElse { return null }
        }
        if (!record.isFullTank) return@forEach

        if (previousAnchor != null) {
            val distanceKm = record.odometerKm - previousAnchor.odometerKm
            if (distanceKm > 0 && accumulatedVolumeMl > 0) {
                cycles += FuelEconomyCycle(distanceKm, accumulatedVolumeMl)
            }
        }
        anchor = record
        accumulatedVolumeMl = 0L
    }

    val recentCycles = cycles.takeLast(MAX_RECENT_FUEL_ECONOMY_CYCLES)
    if (recentCycles.isEmpty()) return null
    val totalDistanceKm = runCatching { recentCycles.sumOfExact(FuelEconomyCycle::distanceKm) }.getOrNull()
        ?: return null
    val totalVolumeMl = runCatching { recentCycles.sumOfExact(FuelEconomyCycle::volumeMl) }.getOrNull()
        ?: return null
    if (totalDistanceKm <= 0 || totalVolumeMl <= 0) return null

    return runCatching {
        BigDecimal.valueOf(totalDistanceKm)
            .multiply(BigDecimal.valueOf(1_000L * FUEL_ECONOMY_SCALE))
            .divide(BigDecimal.valueOf(totalVolumeMl), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()
}

/** Returns each full-tank record's valid km/L value, scaled to three decimal places. */
fun calculateFuelEconomyMilliKmPerLiterByRecord(records: List<FuelRecord>): Map<Long, Long> {
    val economyByRecordId = mutableMapOf<Long, Long>()
    var anchor: FuelRecord? = null
    var accumulatedVolumeMl = 0L

    records.sortedWith(FUEL_RECORD_ORDER).forEach { record ->
        val previousAnchor = anchor
        if (previousAnchor != null) {
            accumulatedVolumeMl = runCatching { Math.addExact(accumulatedVolumeMl, record.fuelVolumeMl) }
                .getOrElse { return emptyMap() }
        }
        if (!record.isFullTank) return@forEach

        if (previousAnchor != null) {
            val distanceKm = record.odometerKm - previousAnchor.odometerKm
            if (distanceKm > 0 && accumulatedVolumeMl > 0) {
                val economy = runCatching {
                    BigDecimal.valueOf(distanceKm)
                        .multiply(BigDecimal.valueOf(1_000L * FUEL_ECONOMY_SCALE))
                        .divide(BigDecimal.valueOf(accumulatedVolumeMl), 0, RoundingMode.HALF_UP)
                        .longValueExact()
                }.getOrNull()
                if (economy != null) economyByRecordId[record.id] = economy
            }
        }
        anchor = record
        accumulatedVolumeMl = 0L
    }
    return economyByRecordId
}

fun Long.toFuelEconomyDisplayText(): String = BigDecimal.valueOf(this, 3)
    .setScale(1, RoundingMode.HALF_UP)
    .toPlainString()

private data class FuelEconomyCycle(
    val distanceKm: Long,
    val volumeMl: Long,
)

private fun <T> Iterable<T>.sumOfExact(value: (T) -> Long): Long =
    fold(0L) { sum, element -> Math.addExact(sum, value(element)) }

private val FUEL_RECORD_ORDER = compareBy<FuelRecord>(FuelRecord::dateEpochDay)
    .thenBy { it.timeMinuteOfDay ?: Int.MAX_VALUE }
    .thenBy(FuelRecord::sequenceInDay)
    .thenBy(FuelRecord::id)
