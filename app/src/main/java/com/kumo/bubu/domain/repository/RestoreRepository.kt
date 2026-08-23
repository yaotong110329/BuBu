package com.kumo.bubu.domain.repository

data class RestorePreview(
    val createdAtEpochMillis: Long,
    val appVersion: String,
    val vehicleCount: Int,
    val fuelRecordCount: Int,
    val serviceRecordCount: Int,
    val serviceItemCount: Int,
    val expenseRecordCount: Int,
    val reminderCount: Int,
    val attachmentCount: Int,
    val totalByteCount: Long,
)

data class RestoreResult(
    val recoveryBackupFileName: String,
)

data class RecoveryBackup(
    val fileName: String,
    val byteCount: Long,
    val createdAtEpochMillis: Long,
)

interface RestoreRepository {
    suspend fun preview(sourceUriString: String): RestorePreview

    suspend fun restore(sourceUriString: String): RestoreResult

    suspend fun getLatestRecoveryBackup(): RecoveryBackup?

    suspend fun exportLatestRecoveryBackup(destinationUriString: String): RecoveryBackup

    suspend fun deleteLatestRecoveryBackup(): Boolean
}
