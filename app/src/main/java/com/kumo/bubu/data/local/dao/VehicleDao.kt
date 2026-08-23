package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY isArchived ASC, name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getById(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE publicId = :publicId")
    suspend fun getByPublicId(publicId: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE name = :name COLLATE NOCASE ORDER BY id ASC LIMIT 1")
    suspend fun getByName(name: String): VehicleEntity?

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Delete
    suspend fun delete(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET currentOdometerKm = :odometerKm, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCurrentOdometer(id: Long, odometerKm: Long, updatedAt: Long)
}
