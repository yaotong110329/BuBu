package com.kumo.bubu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_attachment_deletions",
    indices = [Index(value = ["relativePath"], unique = true)],
)
data class PendingAttachmentDeletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val relativePath: String,
    val createdAt: Long,
)
