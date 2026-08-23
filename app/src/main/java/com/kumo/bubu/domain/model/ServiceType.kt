package com.kumo.bubu.domain.model

data class ServiceType(
    val id: Long,
    val publicId: String,
    val name: String,
    val isBuiltIn: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val vehicleType: VehicleType = VehicleType.CAR,
)

data class ServiceTypeInput(val name: String, val vehicleType: VehicleType = VehicleType.CAR)

fun ServiceTypeInput.validated(): ServiceTypeInput {
    require(name.isNotBlank()) { "Service type name cannot be blank." }
    return copy(name = name.trim())
}
