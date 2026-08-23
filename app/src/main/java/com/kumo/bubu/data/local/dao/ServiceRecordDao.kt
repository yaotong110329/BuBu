package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceRecordDao {
    @Query(
        "SELECT * FROM service_records " +
            "ORDER BY vehicleId ASC, dateEpochDay ASC, timeMinuteOfDay ASC, sequenceInDay ASC, id ASC",
    )
    suspend fun getAllForExport(): List<ServiceRecordEntity>

    @Query("SELECT * FROM service_records ORDER BY dateEpochDay DESC, timeMinuteOfDay DESC, sequenceInDay DESC, id DESC")
    fun observeRecent(): Flow<List<ServiceRecordEntity>>

    @Query(
        "SELECT * FROM service_records WHERE vehicleId = :vehicleId " +
            "ORDER BY dateEpochDay DESC, timeMinuteOfDay DESC, sequenceInDay DESC, id DESC",
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<ServiceRecordEntity>>

    @Query("SELECT * FROM service_records WHERE id = :id")
    suspend fun getById(id: Long): ServiceRecordEntity?

    @Query("SELECT * FROM service_records WHERE publicId = :publicId")
    suspend fun getByPublicId(publicId: String): ServiceRecordEntity?

    @Query(
        "SELECT * FROM service_records WHERE vehicleId = :vehicleId " +
            "ORDER BY dateEpochDay ASC, timeMinuteOfDay ASC, sequenceInDay ASC, id ASC",
    )
    suspend fun getForVehicleInRecordOrder(vehicleId: Long): List<ServiceRecordEntity>

    @Query("SELECT COALESCE(MAX(sequenceInDay), -1) + 1 FROM service_records WHERE vehicleId = :vehicleId AND dateEpochDay = :dateEpochDay")
    suspend fun nextSequenceInDay(vehicleId: Long, dateEpochDay: Long): Int

    @Query("SELECT MAX(odometerKm) FROM service_records WHERE vehicleId = :vehicleId")
    suspend fun maxOdometerKm(vehicleId: Long): Long?

    @Query("SELECT COUNT(*) FROM service_records WHERE vehicleId = :vehicleId")
    suspend fun countForVehicle(vehicleId: Long): Int

    @Insert
    suspend fun insert(record: ServiceRecordEntity): Long

    @Update
    suspend fun update(record: ServiceRecordEntity)

    @Query("DELETE FROM service_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
