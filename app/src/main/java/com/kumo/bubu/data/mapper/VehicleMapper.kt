package com.kumo.bubu.data.mapper

import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.model.VehicleType

fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id,
    publicId = publicId,
    name = name,
    vehicleType = vehicleType,
    motorcycleClass = motorcycleClass.takeIf { vehicleType == VehicleType.MOTORCYCLE },
    brand = brand,
    model = model,
    manufactureYear = manufactureYear,
    engineDisplacementCc = engineDisplacementCc,
    licensePlate = licensePlate,
    powertrainType = powertrainType,
    trackingStartDateEpochDay = trackingStartDateEpochDay,
    trackingStartOdometerKm = trackingStartOdometerKm,
    currentOdometerKm = currentOdometerKm,
    note = note,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    primaryInspectionMonthDay = primaryInspectionMonthDay,
    secondaryInspectionMonthDay = secondaryInspectionMonthDay,
)

fun VehicleInput.toNewEntity(
    publicId: String,
    nowEpochMillis: Long,
): VehicleEntity = VehicleEntity(
    publicId = publicId,
    name = name.trim(),
    vehicleType = vehicleType,
    motorcycleClass = motorcycleClass.takeIf { vehicleType == VehicleType.MOTORCYCLE },
    brand = brand.normalizedOrNull(),
    model = model.normalizedOrNull(),
    manufactureYear = manufactureYear,
    engineDisplacementCc = engineDisplacementCc,
    licensePlate = licensePlate.normalizedOrNull(),
    powertrainType = powertrainType,
    trackingStartDateEpochDay = trackingStartDateEpochDay,
    trackingStartOdometerKm = trackingStartOdometerKm,
    currentOdometerKm = trackingStartOdometerKm,
    note = note.normalizedOrNull(),
    isArchived = false,
    createdAt = nowEpochMillis,
    updatedAt = nowEpochMillis,
    primaryInspectionMonthDay = primaryInspectionMonthDay,
    secondaryInspectionMonthDay = secondaryInspectionMonthDay,
)

fun VehicleInput.toUpdatedEntity(
    existing: VehicleEntity,
    nowEpochMillis: Long,
): VehicleEntity = existing.copy(
    name = name.trim(),
    vehicleType = vehicleType,
    motorcycleClass = motorcycleClass.takeIf { vehicleType == VehicleType.MOTORCYCLE },
    brand = brand.normalizedOrNull(),
    model = model.normalizedOrNull(),
    manufactureYear = manufactureYear,
    engineDisplacementCc = engineDisplacementCc,
    licensePlate = licensePlate.normalizedOrNull(),
    powertrainType = powertrainType,
    trackingStartDateEpochDay = trackingStartDateEpochDay,
    trackingStartOdometerKm = trackingStartOdometerKm,
    currentOdometerKm = trackingStartOdometerKm,
    note = note.normalizedOrNull(),
    primaryInspectionMonthDay = primaryInspectionMonthDay,
    secondaryInspectionMonthDay = secondaryInspectionMonthDay,
    updatedAt = nowEpochMillis,
)

private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
