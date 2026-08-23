package com.kumo.bubu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kumo.bubu.domain.model.ServiceQuantityUnit

@Entity(
    tableName = "service_items",
    foreignKeys = [ForeignKey(entity = ServiceRecordEntity::class, parentColumns = ["id"], childColumns = ["serviceRecordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["publicId"], unique = true), Index(value = ["serviceRecordId", "sequenceInRecord"])],
)
data class ServiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val publicId: String,
    val serviceRecordId: Long,
    val serviceTypeId: Long?,
    val sequenceInRecord: Int,
    val nameSnapshot: String,
    val quantityMilli: Long,
    val quantityUnit: ServiceQuantityUnit,
    val unitPriceTwd: Long,
    val subtotalTwd: Long,
    val nextDueOdometerKm: Long?,
    val nextDueDateEpochDay: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
