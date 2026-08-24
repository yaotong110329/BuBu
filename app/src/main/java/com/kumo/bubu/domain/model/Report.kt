package com.kumo.bubu.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

data class ReportQuery(
    val vehicleIds: List<Long>,
    val startEpochDay: Long,
    val endEpochDay: Long,
) {
    init {
        require(vehicleIds.isNotEmpty())
        require(startEpochDay <= endEpochDay)
    }
}

enum class ReportCostCategory {
    FUEL,
    MAINTENANCE,
    REPAIR,
    LICENSE_TAX,
    ROAD_MAINTENANCE_FEE,
    INSURANCE,
    OTHER,
}

data class ReportCategoryTotal(
    val category: ReportCostCategory,
    val totalCostTwd: Long,
)

data class ReportVehicleCategoryTotal(
    val vehicleId: Long,
    val category: ReportCostCategory,
    val totalCostTwd: Long,
)

data class ReportMonthTotal(
    val monthKey: String,
    val totalCostTwd: Long,
)

data class ReportMonthCategoryTotal(
    val monthKey: String,
    val category: ReportCostCategory,
    val totalCostTwd: Long,
)

data class ReportServiceMonthTotal(
    val monthKey: String,
    val recordType: ServiceRecordType,
    val totalCostTwd: Long,
)

data class ReportOdometerRecord(
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val source: ReportSource,
)

sealed interface ReportSource {
    data class Fuel(val recordId: Long) : ReportSource
    data class Service(val recordId: Long) : ReportSource
}

data class ReportData(
    val categoryTotals: List<ReportCategoryTotal> = emptyList(),
    val vehicleCategoryTotals: List<ReportVehicleCategoryTotal> = emptyList(),
    val monthlyTotals: List<ReportMonthTotal> = emptyList(),
    val monthlyCategoryTotals: List<ReportMonthCategoryTotal> = emptyList(),
    val serviceMonthlyTotals: List<ReportServiceMonthTotal> = emptyList(),
    val fuelRecords: List<FuelRecord> = emptyList(),
    val odometerRecords: List<ReportOdometerRecord> = emptyList(),
)

data class ReportFuelEconomyPoint(
    val vehicleId: Long,
    val dateEpochDay: Long,
    val fuelRecordId: Long,
    val milliKmPerLiter: Long,
    val distanceKm: Long,
    val volumeMl: Long,
)

fun calculateReportFuelEconomyPoints(records: List<FuelRecord>): List<ReportFuelEconomyPoint> {
    val points = mutableListOf<ReportFuelEconomyPoint>()
    records.groupBy(FuelRecord::vehicleId).forEach { (vehicleId, vehicleRecords) ->
        var anchor: FuelRecord? = null
        var accumulatedVolumeMl = 0L
        vehicleRecords.sortedWith(REPORT_FUEL_RECORD_ORDER).forEach { record ->
            val previousAnchor = anchor
            if (previousAnchor != null) {
                accumulatedVolumeMl = runCatching { Math.addExact(accumulatedVolumeMl, record.fuelVolumeMl) }
                    .getOrElse { return@forEach }
            }
            if (!record.isFullTank) return@forEach
            if (previousAnchor != null) {
                val distanceKm = record.odometerKm - previousAnchor.odometerKm
                val economy = calculateMilliKmPerLiter(distanceKm, accumulatedVolumeMl)
                if (economy != null && record.fuelEconomyStatisticsStatus != FuelEconomyStatisticsStatus.EXCLUDED) {
                    points += ReportFuelEconomyPoint(
                        vehicleId = vehicleId,
                        dateEpochDay = record.dateEpochDay,
                        fuelRecordId = record.id,
                        milliKmPerLiter = economy,
                        distanceKm = distanceKm,
                        volumeMl = accumulatedVolumeMl,
                    )
                }
            }
            anchor = record
            accumulatedVolumeMl = 0L
        }
    }
    return points
}

fun calculateWeightedFuelEconomyMilliKmPerLiter(points: List<ReportFuelEconomyPoint>): Long? {
    val distanceKm = runCatching { points.fold(0L) { total, point -> Math.addExact(total, point.distanceKm) } }.getOrNull()
        ?: return null
    val volumeMl = runCatching { points.fold(0L) { total, point -> Math.addExact(total, point.volumeMl) } }.getOrNull()
        ?: return null
    return calculateMilliKmPerLiter(distanceKm, volumeMl)
}

private fun calculateMilliKmPerLiter(distanceKm: Long, volumeMl: Long): Long? {
    if (distanceKm <= 0 || volumeMl <= 0) return null
    return runCatching {
        BigDecimal.valueOf(distanceKm)
            .multiply(BigDecimal.valueOf(1_000_000L))
            .divide(BigDecimal.valueOf(volumeMl), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()
}

private val REPORT_FUEL_RECORD_ORDER = compareBy<FuelRecord>(FuelRecord::dateEpochDay)
    .thenBy { it.timeMinuteOfDay ?: Int.MAX_VALUE }
    .thenBy(FuelRecord::sequenceInDay)
    .thenBy(FuelRecord::id)
