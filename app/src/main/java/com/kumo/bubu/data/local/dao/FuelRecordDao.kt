package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelRecordDao {
    @Query(
        "SELECT * FROM fuel_records " +
            "ORDER BY vehicleId ASC, dateEpochDay ASC, timeMinuteOfDay ASC, sequenceInDay ASC, id ASC",
    )
    suspend fun getAllForExport(): List<FuelRecordEntity>

    @Query(
        "SELECT * FROM fuel_records " +
            "ORDER BY dateEpochDay DESC, timeMinuteOfDay DESC, sequenceInDay DESC, id DESC",
    )
    fun observeRecent(): Flow<List<FuelRecordEntity>>

    @Query(
        "SELECT * FROM fuel_records WHERE vehicleId = :vehicleId " +
            "ORDER BY dateEpochDay DESC, timeMinuteOfDay DESC, sequenceInDay DESC, id DESC",
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<FuelRecordEntity>>

    @Query(
        "SELECT * FROM fuel_records WHERE vehicleId = :vehicleId " +
            "ORDER BY dateEpochDay ASC, timeMinuteOfDay ASC, sequenceInDay ASC, id ASC",
    )
    suspend fun getForVehicleInRecordOrder(vehicleId: Long): List<FuelRecordEntity>

    @Query("SELECT * FROM fuel_records WHERE id = :id")
    suspend fun getById(id: Long): FuelRecordEntity?

    @Query("SELECT * FROM fuel_records WHERE publicId = :publicId")
    suspend fun getByPublicId(publicId: String): FuelRecordEntity?

    @Query(
        "SELECT COALESCE(MAX(sequenceInDay), -1) + 1 FROM fuel_records " +
            "WHERE vehicleId = :vehicleId AND dateEpochDay = :dateEpochDay",
    )
    suspend fun nextSequenceInDay(vehicleId: Long, dateEpochDay: Long): Int

    @Query("SELECT MAX(odometerKm) FROM fuel_records WHERE vehicleId = :vehicleId")
    suspend fun maxOdometerKm(vehicleId: Long): Long?

    @Query("SELECT COUNT(*) FROM fuel_records WHERE vehicleId = :vehicleId")
    suspend fun countForVehicle(vehicleId: Long): Int

    @Query(
        "SELECT pricePerLiterMilli FROM fuel_records " +
            "WHERE vehicleId = :vehicleId AND fuelProduct = :fuelProduct " +
            "AND pricePerLiterMilli IS NOT NULL AND dateEpochDay <= :dateEpochDay " +
            "ORDER BY dateEpochDay DESC, timeMinuteOfDay DESC, sequenceInDay DESC, id DESC LIMIT 1",
    )
    suspend fun getLastPriceForProduct(
        vehicleId: Long,
        fuelProduct: com.kumo.bubu.domain.model.FuelProduct,
        dateEpochDay: Long,
    ): Long?

    @Insert
    suspend fun insert(record: FuelRecordEntity): Long

    @Update
    suspend fun update(record: FuelRecordEntity)

    @Query("UPDATE fuel_records SET fuelEconomyStatisticsStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFuelEconomyStatisticsStatus(
        id: Long,
        status: FuelEconomyStatisticsStatus,
        updatedAt: Long,
    )

    @Query("DELETE FROM fuel_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
