package com.kumo.bubu.domain.model

import java.time.LocalDate
import java.time.MonthDay

data class Vehicle(
    val id: Long,
    val publicId: String,
    val name: String,
    val vehicleType: VehicleType,
    val motorcycleClass: MotorcycleClass?,
    val brand: String?,
    val model: String?,
    val manufactureYear: Int?,
    val engineDisplacementCc: Int?,
    val licensePlate: String?,
    val powertrainType: PowertrainType?,
    val trackingStartDateEpochDay: Long,
    val trackingStartOdometerKm: Long,
    val currentOdometerKm: Long,
    val note: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val primaryInspectionMonthDay: MonthDay? = null,
    val secondaryInspectionMonthDay: MonthDay? = null,
)

data class VehicleInput(
    val name: String,
    val vehicleType: VehicleType,
    val motorcycleClass: MotorcycleClass?,
    val brand: String? = null,
    val model: String? = null,
    val manufactureYear: Int? = null,
    val engineDisplacementCc: Int? = null,
    val licensePlate: String? = null,
    val powertrainType: PowertrainType? = null,
    val trackingStartDateEpochDay: Long,
    val trackingStartOdometerKm: Long,
    val note: String? = null,
    val primaryInspectionMonthDay: MonthDay? = null,
    val secondaryInspectionMonthDay: MonthDay? = null,
)

data class VehicleGarage(
    val vehicles: List<Vehicle>,
    val currentVehiclePublicId: String?,
)

fun VehicleInput.validated(today: LocalDate = LocalDate.now()): VehicleInput {
    require(name.isNotBlank()) { "Vehicle name cannot be blank." }
    require(trackingStartDateEpochDay <= today.toEpochDay()) { "Tracking start date cannot be in the future." }
    require(trackingStartOdometerKm >= 0) { "Tracking start odometer cannot be negative." }
    require(manufactureYear == null || manufactureYear in 1886..today.year) { "Manufacture year is invalid." }
    require(engineDisplacementCc == null || engineDisplacementCc > 0) { "Engine displacement must be positive." }
    require(vehicleType != VehicleType.MOTORCYCLE || motorcycleClass != null) { "Motorcycle class is required." }
    return copy(
        name = name.trim(),
        motorcycleClass = motorcycleClass.takeIf { vehicleType == VehicleType.MOTORCYCLE },
    )
}

enum class VehicleType {
    CAR,
    MOTORCYCLE,
}

enum class MotorcycleClass {
    LIGHT,
    ORDINARY_HEAVY,
    LARGE_HEAVY,
}

enum class PowertrainType {
    GASOLINE,
    DIESEL,
    HYBRID,
    OTHER_LIQUID_FUEL,
}
