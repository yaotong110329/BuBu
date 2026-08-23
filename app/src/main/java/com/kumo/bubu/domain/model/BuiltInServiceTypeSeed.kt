package com.kumo.bubu.domain.model

data class BuiltInServiceTypeSeed(
    val key: String,
    val displayName: String,
    val vehicleType: VehicleType = VehicleType.CAR,
    val isQuickPick: Boolean = false,
)
