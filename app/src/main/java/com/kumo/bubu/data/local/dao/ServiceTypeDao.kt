package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceTypeDao {
    @Query("SELECT * FROM service_types ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<ServiceTypeEntity>>
    @Query("SELECT * FROM service_types WHERE vehicleType = :vehicleType ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeForVehicleType(vehicleType: com.kumo.bubu.domain.model.VehicleType): Flow<List<ServiceTypeEntity>>

    @Query("SELECT * FROM service_types WHERE id = :id")
    suspend fun getById(id: Long): ServiceTypeEntity?

    @Query("SELECT * FROM service_types ORDER BY vehicleType ASC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<ServiceTypeEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM service_types WHERE vehicleType = :vehicleType")
    suspend fun maxSortOrder(vehicleType: com.kumo.bubu.domain.model.VehicleType): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(types: List<ServiceTypeEntity>)

    @Insert
    suspend fun insert(type: ServiceTypeEntity): Long

    @Update
    suspend fun update(type: ServiceTypeEntity)

    @Query("DELETE FROM service_types WHERE id = :id")
    suspend fun deleteById(id: Long)
}
