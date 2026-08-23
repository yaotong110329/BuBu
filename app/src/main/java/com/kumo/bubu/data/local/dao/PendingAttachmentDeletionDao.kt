package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kumo.bubu.data.local.entity.PendingAttachmentDeletionEntity

@Dao
interface PendingAttachmentDeletionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(deletion: PendingAttachmentDeletionEntity): Long

    @Query("SELECT * FROM pending_attachment_deletions ORDER BY id ASC")
    suspend fun getAll(): List<PendingAttachmentDeletionEntity>

    @Query("DELETE FROM pending_attachment_deletions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
