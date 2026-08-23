package com.kumo.bubu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ServiceRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["publicId"], unique = true),
        Index(value = ["relativePath"], unique = true),
        Index(value = ["serviceRecordId", "sequenceInRecord"]),
    ],
)
data class ServiceAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val publicId: String,
    val serviceRecordId: Long,
    val sequenceInRecord: Int,
    val relativePath: String,
    val displayName: String,
    val mimeType: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
