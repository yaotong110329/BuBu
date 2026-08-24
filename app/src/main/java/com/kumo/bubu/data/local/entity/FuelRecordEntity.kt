package com.kumo.bubu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus

@Entity(
    tableName = "fuel_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["publicId"], unique = true),
        Index(value = ["vehicleId", "dateEpochDay", "timeMinuteOfDay", "sequenceInDay"]),
    ],
)
data class FuelRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val publicId: String,
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val fuelVolumeMl: Long,
    val pricePerLiterMilli: Long?,
    val totalCostTwd: Long,
    val isFullTank: Boolean,
    val fuelProduct: FuelProduct?,
    val fuelingMode: FuelingMode = FuelingMode.FULL_SERVICE,
    val fuelEconomyStatisticsStatus: FuelEconomyStatisticsStatus = FuelEconomyStatisticsStatus.UNREVIEWED,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
