package com.kumo.bubu.data.local.model

/** A service item joined with its work order, used only to derive the latest maintenance fact. */
data class ServiceItemMaintenanceBaseline(
    val vehicleId: Long,
    val serviceTypeId: Long,
    val serviceDateEpochDay: Long,
    val serviceTimeMinuteOfDay: Int?,
    val serviceSequenceInDay: Int,
    val serviceRecordId: Long,
    val odometerKm: Long,
)
