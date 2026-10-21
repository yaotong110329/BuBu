package com.kumo.bubu.data.backup

import com.kumo.bubu.domain.repository.BackupTooLargeException

/** Existing format-1 restore limits, shared by archive creation and consumption. */
object BackupArchiveLimits {
    const val MAX_MANIFEST_BYTES = 1 * 1024 * 1024
    const val MAX_DATA_BYTES = 50 * 1024 * 1024
    const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
    const val MAX_TOTAL_CONTENT_BYTES = 512L * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024

    fun maxBytesFor(path: String): Int = when {
        path == "data.json" -> MAX_DATA_BYTES
        path.startsWith("attachments/") -> MAX_ATTACHMENT_BYTES
        else -> throw IllegalArgumentException("Backup contains an unsupported file.")
    }

    fun requireSize(sizeBytes: Long, maxBytes: Long) {
        require(sizeBytes >= 0L) { "Backup contains an invalid file size." }
        if (sizeBytes > maxBytes) throw BackupTooLargeException()
    }

    fun addContentSize(total: Long, sizeBytes: Long): Long {
        requireSize(sizeBytes, MAX_TOTAL_CONTENT_BYTES)
        // Subtraction avoids overflow even for hostile manifest values.
        if (total > MAX_TOTAL_CONTENT_BYTES - sizeBytes) throw BackupTooLargeException()
        return total + sizeBytes
    }
}
