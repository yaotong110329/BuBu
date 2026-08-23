package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.ServiceItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceItemDao {
    @Query("SELECT * FROM service_items ORDER BY serviceRecordId ASC, sequenceInRecord ASC, id ASC")
    suspend fun getAllForExport(): List<ServiceItemEntity>

    @Query("SELECT * FROM service_items WHERE serviceRecordId = :serviceRecordId ORDER BY sequenceInRecord ASC, id ASC")
    fun observeForRecord(serviceRecordId: Long): Flow<List<ServiceItemEntity>>

    @Query(
        """
        SELECT item.*
        FROM service_items AS item
        INNER JOIN service_records AS record ON record.id = item.serviceRecordId
        WHERE record.vehicleId = :vehicleId
        ORDER BY record.dateEpochDay DESC,
                 record.timeMinuteOfDay DESC,
                 record.sequenceInDay DESC,
                 record.id DESC,
                 item.sequenceInRecord ASC,
                 item.id ASC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<ServiceItemEntity>>

    @Query("SELECT * FROM service_items WHERE serviceRecordId = :serviceRecordId ORDER BY sequenceInRecord ASC, id ASC")
    suspend fun getForRecord(serviceRecordId: Long): List<ServiceItemEntity>

    @Query(
        """
        SELECT item.*
        FROM service_items AS item
        INNER JOIN service_records AS record ON record.id = item.serviceRecordId
        WHERE record.vehicleId = :vehicleId
        ORDER BY record.dateEpochDay ASC,
                 COALESCE(record.timeMinuteOfDay, -1) ASC,
                 record.sequenceInDay ASC,
                 record.id ASC,
                 item.sequenceInRecord ASC,
                 item.id ASC
        """,
    )
    suspend fun getForVehicleInTimelineOrder(vehicleId: Long): List<ServiceItemEntity>

    @Insert
    suspend fun insertAll(items: List<ServiceItemEntity>): List<Long>

    @Update
    suspend fun update(item: ServiceItemEntity)

    @Query("DELETE FROM service_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM service_items WHERE serviceTypeId = :serviceTypeId")
    suspend fun countForServiceType(serviceTypeId: Long): Int
}
