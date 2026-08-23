package com.kumo.bubu.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

data class FuelCalculation(
    val fuelVolumeMl: Long,
    val pricePerLiterMilli: Long,
    val totalCostTwd: Long,
)

fun calculateFuelTotalCost(fuelVolumeMl: Long, pricePerLiterMilli: Long): Long =
    BigDecimal.valueOf(fuelVolumeMl)
        .multiply(BigDecimal.valueOf(pricePerLiterMilli))
        .divide(MICRO_TWD_PER_ML, 0, RoundingMode.HALF_UP)
        .longValueExact()

fun calculateFuelPricePerLiter(fuelVolumeMl: Long, totalCostTwd: Long): Long? {
    if (fuelVolumeMl <= 0) return null
    return BigDecimal.valueOf(totalCostTwd)
        .multiply(MICRO_TWD_PER_ML)
        .divide(BigDecimal.valueOf(fuelVolumeMl), 0, RoundingMode.HALF_UP)
        .longValueExact()
}

fun calculateFuelVolumeMl(pricePerLiterMilli: Long, totalCostTwd: Long): Long? {
    if (pricePerLiterMilli <= 0) return null
    return BigDecimal.valueOf(totalCostTwd)
        .multiply(MICRO_TWD_PER_ML)
        .divide(BigDecimal.valueOf(pricePerLiterMilli), 0, RoundingMode.HALF_UP)
        .longValueExact()
}

fun Long.toScaledDecimalText(scale: Int): String = BigDecimal.valueOf(this)
    .movePointLeft(scale)
    .stripTrailingZeros()
    .toPlainString()

private val MICRO_TWD_PER_ML = BigDecimal.valueOf(1_000_000L)
