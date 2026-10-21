package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import kotlinx.coroutines.flow.Flow

interface CloudBackupRepository {
    fun observeConnection(): Flow<CloudBackupConnection>

    suspend fun rememberConnection(account: CloudBackupAccount)

    suspend fun clearConnection()

    suspend fun markNeedsAuthorization()

    suspend fun uploadBackup(accessToken: String): CloudBackup

    suspend fun listBackups(accessToken: String): List<CloudBackup>

    suspend fun downloadBackup(accessToken: String, backupId: String): CloudBackupDownload

    /** Deletes only a private download created by this repository, never a selected source. */
    fun discardDownload(download: CloudBackupDownload)

    suspend fun deleteBackup(accessToken: String, backupId: String)
}

data class CloudBackupDownload(
    val backup: CloudBackup,
    val localFilePath: String,
)

/** Upload succeeded and was recorded, but the old-backup inventory or cleanup failed. */
class CloudBackupRetentionException(
    val uploadedBackup: CloudBackup,
    cause: Throwable,
) : Exception("Cloud backup uploaded, but retention did not complete.", cause)
