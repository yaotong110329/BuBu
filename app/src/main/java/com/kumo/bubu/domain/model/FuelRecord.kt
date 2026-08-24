package com.kumo.bubu.domain.model

import java.time.LocalDate

data class FuelRecord(
    val id: Long,
    val publicId: String,
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val fuelVolumeMl: Long,
    val pricePerLiterMilli: Long?,
    val totalCostTwd: Long,
    val isFullTank: Boolean,
    val fuelProduct: FuelProduct?,
    val fuelingMode: FuelingMode = FuelingMode.FULL_SERVICE,
    val fuelEconomyStatisticsStatus: FuelEconomyStatisticsStatus = FuelEconomyStatisticsStatus.UNREVIEWED,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class FuelRecordInput(
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val odometerKm: Long,
    val fuelVolumeMl: Long,
    val pricePerLiterMilli: Long?,
    val totalCostTwd: Long,
    val isFullTank: Boolean,
    val fuelProduct: FuelProduct? = null,
    val fuelingMode: FuelingMode = FuelingMode.FULL_SERVICE,
    val fuelEconomyStatisticsStatus: FuelEconomyStatisticsStatus = FuelEconomyStatisticsStatus.UNREVIEWED,
    val note: String? = null,
)

fun FuelRecordInput.validated(today: LocalDate = LocalDate.now()): FuelRecordInput {
    require(dateEpochDay <= today.toEpochDay()) { "Fuel date cannot be in the future." }
    require(timeMinuteOfDay == null || timeMinuteOfDay in 0 until MINUTES_PER_DAY) { "Fuel time is invalid." }
    require(odometerKm >= 0) { "Fuel odometer cannot be negative." }
    require(fuelVolumeMl in 1..MAX_FUEL_VOLUME_ML) { "Fuel volume is invalid." }
    require(totalCostTwd >= 0) { "Fuel total cost cannot be negative." }
    require(pricePerLiterMilli == null || pricePerLiterMilli >= 0) { "Fuel price is invalid." }
    return copy(note = note?.trim()?.takeIf(String::isNotEmpty))
}

enum class FuelProduct {
    GASOLINE_92,
    GASOLINE_95,
    GASOLINE_98,
    DIESEL,
    OTHER,
}

/** How the station dispensed this record's fuel.  This is historical record data, not a price guess. */
enum class FuelingMode {
    FULL_SERVICE,
    SELF_SERVICE,
}

/** A user's decision about whether a full-tank segment belongs in fuel-economy statistics. */
enum class FuelEconomyStatisticsStatus {
    UNREVIEWED,
    INCLUDED,
    EXCLUDED,
}

/**
 * CPC's self-service discount is expressed in the existing milli-TWD per litre representation.
 * Keeping it here gives both the form and its tests one authoritative policy value.
 */
const val SELF_SERVICE_DISCOUNT_MILLI_TWD_PER_LITER = 800L

fun FuelingMode.applyToCpcListPrice(listPriceMilli: Long): Long = when (this) {
    FuelingMode.FULL_SERVICE -> listPriceMilli
    FuelingMode.SELF_SERVICE -> (listPriceMilli - SELF_SERVICE_DISCOUNT_MILLI_TWD_PER_LITER).coerceAtLeast(0L)
}

const val MAX_FUEL_VOLUME_ML = 999_999L
private const val MINUTES_PER_DAY = 24 * 60
