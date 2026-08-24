package com.kumo.bubu.data.mapper

import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.FuelRecordInput

fun FuelRecordEntity.toDomain(): FuelRecord = FuelRecord(
    id = id,
    publicId = publicId,
    vehicleId = vehicleId,
    dateEpochDay = dateEpochDay,
    timeMinuteOfDay = timeMinuteOfDay,
    sequenceInDay = sequenceInDay,
    odometerKm = odometerKm,
    fuelVolumeMl = fuelVolumeMl,
    pricePerLiterMilli = pricePerLiterMilli,
    totalCostTwd = totalCostTwd,
    isFullTank = isFullTank,
    fuelProduct = fuelProduct,
    fuelingMode = fuelingMode,
    fuelEconomyStatisticsStatus = fuelEconomyStatisticsStatus,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FuelRecordInput.toNewEntity(
    publicId: String,
    sequenceInDay: Int,
    nowEpochMillis: Long,
): FuelRecordEntity = FuelRecordEntity(
    publicId = publicId,
    vehicleId = vehicleId,
    dateEpochDay = dateEpochDay,
    timeMinuteOfDay = timeMinuteOfDay,
    sequenceInDay = sequenceInDay,
    odometerKm = odometerKm,
    fuelVolumeMl = fuelVolumeMl,
    pricePerLiterMilli = pricePerLiterMilli,
    totalCostTwd = totalCostTwd,
    isFullTank = isFullTank,
    fuelProduct = fuelProduct,
    fuelingMode = fuelingMode,
    fuelEconomyStatisticsStatus = fuelEconomyStatisticsStatus,
    note = note,
    createdAt = nowEpochMillis,
    updatedAt = nowEpochMillis,
)

fun FuelRecordInput.toUpdatedEntity(
    existing: FuelRecordEntity,
    sequenceInDay: Int,
    nowEpochMillis: Long,
): FuelRecordEntity = existing.copy(
    vehicleId = vehicleId,
    dateEpochDay = dateEpochDay,
    timeMinuteOfDay = timeMinuteOfDay,
    sequenceInDay = sequenceInDay,
    odometerKm = odometerKm,
    fuelVolumeMl = fuelVolumeMl,
    pricePerLiterMilli = pricePerLiterMilli,
    totalCostTwd = totalCostTwd,
    isFullTank = isFullTank,
    fuelProduct = fuelProduct,
    fuelingMode = fuelingMode,
    fuelEconomyStatisticsStatus = fuelEconomyStatisticsStatus,
    note = note,
    updatedAt = nowEpochMillis,
)
