package com.kumo.bubu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus

@Entity(
    tableName = "vehicle_reminders",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ServiceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceServiceItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ServiceRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["completedByServiceRecordId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["publicId"], unique = true),
        Index(value = ["vehicleId"]),
        Index(value = ["sourceServiceItemId"], unique = true),
        Index(value = ["completedByServiceRecordId"]),
        Index(value = ["automaticKey"], unique = true),
    ],
)
data class VehicleReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val publicId: String,
    val vehicleId: Long,
    val source: ReminderSource,
    val sourceServiceItemId: Long?,
    val title: String,
    val dueOdometerKm: Long?,
    val dueDateEpochDay: Long?,
    val completedByServiceRecordId: Long? = null,
    val completedAt: Long? = null,
    val snoozedUntilEpochDay: Long? = null,
    val lastNotifiedStatus: ReminderStatus? = null,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val automaticKey: String? = null,
    val ruleVersion: Int? = null,
    val ruleVerifiedEpochDay: Long? = null,
    val estimatedNotificationEpochDay: Long? = null,
    val lastNotifiedTrigger: String? = null,
    val referenceDateEpochDay: Long? = null,
    val completedByExpenseRecordId: Long? = null,
)
