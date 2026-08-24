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
                if (record.fuelEconomyStatisticsStatus != FuelEconomyStatisticsStatus.EXCLUDED) {
                    cycles += FuelEconomyCycle(distanceKm, accumulatedVolumeMl)
                }
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
                if (economy != null && record.fuelEconomyStatisticsStatus != FuelEconomyStatisticsStatus.EXCLUDED) {
                    economyByRecordId[record.id] = economy
                }
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

/** A conservative, vehicle-local estimate based on recent distinct fueling days. */
data class FuelIntervalPrediction(
    val daysUntilNextFuel: Long,
    val sampleCount: Int,
)

fun estimateFuelInterval(
    vehicleId: Long,
    todayEpochDay: Long,
    records: List<FuelRecord>,
): FuelIntervalPrediction? {
    val recentDays = records
        .asSequence()
        .filter { it.vehicleId == vehicleId && it.dateEpochDay <= todayEpochDay }
        .map(FuelRecord::dateEpochDay)
        .distinct()
        .sorted()
        .toList()
        .takeLast(MAX_FUEL_INTERVAL_DAYS)
    if (recentDays.size < MINIMUM_FUEL_INTERVAL_SAMPLES) return null
    val gaps = recentDays.zipWithNext { earlier, later -> later - earlier }
        .filter { it > 0 }
        .takeLast(MAX_FUEL_INTERVAL_GAPS)
    if (gaps.size < MINIMUM_FUEL_INTERVAL_GAPS) return null
    val medianGap = gaps.sorted().let { sorted -> sorted[sorted.size / 2] }
    val elapsedDays = (todayEpochDay - recentDays.last()).coerceAtLeast(0L)
    return FuelIntervalPrediction(
        daysUntilNextFuel = (medianGap - elapsedDays).coerceAtLeast(0L),
        sampleCount = gaps.size,
    )
}

data class FuelEconomyOutlier(
    val candidateMilliKmPerLiter: Long,
    val baselineMedianMilliKmPerLiter: Long,
)

/** Relative distance from the vehicle-local baseline, used to prioritise review. */
fun FuelEconomyOutlier.deviationPermille(): Long = runCatching {
    BigDecimal.valueOf(kotlin.math.abs(candidateMilliKmPerLiter - baselineMedianMilliKmPerLiter))
        .multiply(BigDecimal.valueOf(1_000L))
        .divide(BigDecimal.valueOf(baselineMedianMilliKmPerLiter), 0, RoundingMode.HALF_UP)
        .longValueExact()
}.getOrDefault(0L)

data class FuelEconomyOutlierCandidate(
    val fuelRecordId: Long,
    val outlier: FuelEconomyOutlier,
)

/**
 * Finds historical, structurally valid full-tank segments that deserve review.
 * Candidates remain included until the user explicitly excludes them.
 */
fun detectFuelEconomyOutlierCandidates(records: List<FuelRecord>): List<FuelEconomyOutlierCandidate> =
    records.groupBy(FuelRecord::vehicleId).values.flatMap { vehicleRecords ->
        val orderedRecords = vehicleRecords.sortedWith(FUEL_RECORD_ORDER)
        orderedRecords.mapIndexedNotNull { index, candidate ->
            if (candidate.fuelEconomyStatisticsStatus != FuelEconomyStatisticsStatus.UNREVIEWED) return@mapIndexedNotNull null
            detectFuelEconomyOutlier(orderedRecords.take(index), candidate)?.let { outlier ->
                FuelEconomyOutlierCandidate(candidate.id, outlier)
            }
        }
    }

/**
 * Flags only an otherwise valid full-tank segment.  The check deliberately leaves the
 * record untouched: an unusual but real consumption cycle remains valid domain data.
 */
fun detectFuelEconomyOutlier(
    existingRecords: List<FuelRecord>,
    candidate: FuelRecord,
): FuelEconomyOutlier? {
    if (!candidate.isFullTank) return null
    val candidateEconomy = calculateFuelEconomyMilliKmPerLiterByRecord(existingRecords + candidate)[candidate.id]
        ?: return null
    val baseline = calculateFuelEconomyMilliKmPerLiterByRecord(existingRecords)
        .values
        .sorted()
        .takeLast(MAX_OUTLIER_BASELINE_SEGMENTS)
    if (baseline.size < MINIMUM_OUTLIER_BASELINE_SEGMENTS) return null
    val median = baseline[baseline.size / 2]
    val deviations = baseline.map { kotlin.math.abs(it - median) }.sorted()
    val mad = deviations[deviations.size / 2]
    val deviation = kotlin.math.abs(candidateEconomy - median)
    val robustThreshold = if (mad == 0L) 0L else mad * OUTLIER_MAD_MULTIPLIER
    val extremeVariationThreshold = median * MINIMUM_OUTLIER_DEVIATION_PERCENT / 100L
    val threshold = maxOf(robustThreshold, extremeVariationThreshold)
    return if (deviation > threshold) FuelEconomyOutlier(candidateEconomy, median) else null
}

private const val MAX_FUEL_INTERVAL_DAYS = 8
private const val MAX_FUEL_INTERVAL_GAPS = 6
private const val MINIMUM_FUEL_INTERVAL_SAMPLES = 3
private const val MINIMUM_FUEL_INTERVAL_GAPS = 2
private const val MAX_OUTLIER_BASELINE_SEGMENTS = 8
private const val MINIMUM_OUTLIER_BASELINE_SEGMENTS = 4
private const val OUTLIER_MAD_MULTIPLIER = 3L
private const val MINIMUM_OUTLIER_DEVIATION_PERCENT = 40L
