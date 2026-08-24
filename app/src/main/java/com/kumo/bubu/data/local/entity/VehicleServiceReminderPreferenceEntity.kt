package com.kumo.bubu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-vehicle policy for the next reminder derived from a service type's history. */
@Entity(
    tableName = "vehicle_service_reminder_preferences",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ServiceTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["publicId"], unique = true),
        Index(value = ["vehicleId", "serviceTypeId"], unique = true),
        Index(value = ["serviceTypeId"]),
    ],
)
data class VehicleServiceReminderPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val publicId: String,
    val vehicleId: Long,
    val serviceTypeId: Long,
    val isEnabled: Boolean = true,
    val intervalKm: Long? = null,
    val baseOdometerKm: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
