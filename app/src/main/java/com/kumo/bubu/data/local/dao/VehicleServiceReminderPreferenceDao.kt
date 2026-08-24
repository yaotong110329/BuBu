package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.VehicleServiceReminderPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleServiceReminderPreferenceDao {
    @Query("SELECT * FROM vehicle_service_reminder_preferences")
    suspend fun getAll(): List<VehicleServiceReminderPreferenceEntity>

    @Query("SELECT * FROM vehicle_service_reminder_preferences")
    fun observeAll(): Flow<List<VehicleServiceReminderPreferenceEntity>>

    @Query("SELECT * FROM vehicle_service_reminder_preferences WHERE vehicleId = :vehicleId ORDER BY sortOrder, id")
    fun observeForVehicle(vehicleId: Long): Flow<List<VehicleServiceReminderPreferenceEntity>>

    @Query("SELECT * FROM vehicle_service_reminder_preferences WHERE vehicleId = :vehicleId AND serviceTypeId = :serviceTypeId")
    suspend fun get(vehicleId: Long, serviceTypeId: Long): VehicleServiceReminderPreferenceEntity?

    @Insert
    suspend fun insert(preference: VehicleServiceReminderPreferenceEntity): Long

    @Update
    suspend fun update(preference: VehicleServiceReminderPreferenceEntity)
}
