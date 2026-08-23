package com.kumo.bubu.data.local.entity
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kumo.bubu.domain.model.VehicleType

@Entity(
    tableName = "service_types",
    indices = [
        Index(value = ["publicId"], unique = true),
        Index(value = ["vehicleType"]),
        Index(value = ["vehicleType", "name"], unique = true),
    ],
)
data class ServiceTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val publicId: String,
    val name: String,
    val vehicleType: VehicleType = VehicleType.CAR,
    val isBuiltIn: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
