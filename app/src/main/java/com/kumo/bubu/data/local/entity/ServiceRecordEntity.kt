package com.kumo.bubu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kumo.bubu.domain.model.PaymentMethod
import com.kumo.bubu.domain.model.ServiceRecordType

@Entity(
    tableName = "service_records",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["publicId"], unique = true), Index(value = ["vehicleId", "dateEpochDay", "timeMinuteOfDay", "sequenceInDay"])],
)
data class ServiceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val publicId: String,
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val recordType: ServiceRecordType,
    val title: String,
    val paymentMethod: PaymentMethod?,
    val totalCostTwd: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
