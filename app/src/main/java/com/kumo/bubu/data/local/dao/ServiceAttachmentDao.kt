package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity

@Dao
interface ServiceAttachmentDao {
    @Query("SELECT * FROM service_attachments ORDER BY serviceRecordId ASC, sequenceInRecord ASC, id ASC")
    suspend fun getAllForExport(): List<ServiceAttachmentEntity>

    @Query(
        "SELECT * FROM service_attachments WHERE serviceRecordId = :serviceRecordId " +
            "ORDER BY sequenceInRecord ASC, id ASC",
    )
    suspend fun getForRecord(serviceRecordId: Long): List<ServiceAttachmentEntity>

    @Query("SELECT * FROM service_attachments WHERE id = :id")
    suspend fun getById(id: Long): ServiceAttachmentEntity?

    @Query("SELECT COUNT(*) FROM service_attachments WHERE relativePath = :relativePath")
    suspend fun countByRelativePath(relativePath: String): Int

    @Query("SELECT relativePath FROM service_attachments")
    suspend fun getAllRelativePaths(): List<String>

    @Insert
    suspend fun insert(attachment: ServiceAttachmentEntity): Long

    @Update
    suspend fun update(attachment: ServiceAttachmentEntity)

    @Query("DELETE FROM service_attachments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
