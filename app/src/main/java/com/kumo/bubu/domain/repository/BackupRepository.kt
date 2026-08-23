package com.kumo.bubu.domain.repository

data class BackupResult(
    val fileName: String,
    val byteCount: Long,
)

interface BackupRepository {
    suspend fun createBackup(destinationUriString: String): BackupResult
}
