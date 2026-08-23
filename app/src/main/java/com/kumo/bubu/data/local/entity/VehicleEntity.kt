package com.kumo.bubu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.VehicleType
import java.time.MonthDay

@Entity(
    tableName = "vehicles",
    indices = [Index(value = ["publicId"], unique = true)],
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
