package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.VehicleReminderEntity
import com.kumo.bubu.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleReminderDao {
    @Query("SELECT * FROM vehicle_reminders WHERE sourceServiceItemId = :serviceItemId")
    suspend fun getBySourceServiceItemId(serviceItemId: Long): VehicleReminderEntity?

    @Query("SELECT * FROM vehicle_reminders WHERE vehicleId = :vehicleId")
    suspend fun getForVehicle(vehicleId: Long): List<VehicleReminderEntity>

    @Query("SELECT * FROM vehicle_reminders ORDER BY completedAt IS NOT NULL ASC, dueDateEpochDay ASC, dueOdometerKm ASC, id ASC")
    fun observeAll(): Flow<List<VehicleReminderEntity>>

    @Query("SELECT * FROM vehicle_reminders WHERE id = :id")
    suspend fun getById(id: Long): VehicleReminderEntity?

    @Query("SELECT * FROM vehicle_reminders WHERE automaticKey = :automaticKey")
    suspend fun getByAutomaticKey(automaticKey: String): VehicleReminderEntity?

    @Query(
        "SELECT * FROM vehicle_reminders WHERE automaticKey GLOB :automaticKeyPrefix || '*' " +
            "ORDER BY automaticKey DESC LIMIT 1",
    )
    suspend fun getLatestByAutomaticKeyPrefix(automaticKeyPrefix: String): VehicleReminderEntity?

    @Query("SELECT * FROM vehicle_reminders")
    suspend fun getAll(): List<VehicleReminderEntity>

    @Insert
    suspend fun insert(reminder: VehicleReminderEntity): Long

    @Update
    suspend fun update(reminder: VehicleReminderEntity)

    @Query("DELETE FROM vehicle_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE vehicle_reminders SET lastNotifiedStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastNotifiedStatus(id: Long, status: ReminderStatus?, updatedAt: Long)

    @Query(
        "UPDATE vehicle_reminders SET lastNotifiedStatus = :status, lastNotifiedTrigger = :trigger, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateNotificationTracking(
        id: Long,
        status: ReminderStatus?,
        trigger: String?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE vehicle_reminders SET estimatedNotificationEpochDay = :estimatedEpochDay, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateMileageForecast(id: Long, estimatedEpochDay: Long?, updatedAt: Long)

    @Query(
        "UPDATE vehicle_reminders SET isEnabled = :enabled, updatedAt = :updatedAt " +
            "WHERE source IN ('LICENSE_TAX', 'ROAD_MAINTENANCE_FEE')",
    )
    suspend fun updateTaxAndFeeEnabled(enabled: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE vehicle_reminders
        SET completedByServiceRecordId = :serviceRecordId,
            completedAt = :completedAt,
            updatedAt = :completedAt
        WHERE id = :reminderId
        """,
    )
    suspend fun markCompleted(reminderId: Long, serviceRecordId: Long, completedAt: Long)

    @Query(
        """
        UPDATE vehicle_reminders
        SET completedByServiceRecordId = NULL,
            completedAt = NULL,
            updatedAt = :updatedAt
        WHERE id = :reminderId
        """,
    )
    suspend fun clearCompletion(reminderId: Long, updatedAt: Long)

    @Query("DELETE FROM vehicle_reminders WHERE sourceServiceItemId = :serviceItemId")
    suspend fun deleteBySourceServiceItemId(serviceItemId: Long)
}
