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

    suspend fun deleteBackup(accessToken: String, backupId: String)
}

data class CloudBackupDownload(
    val backup: CloudBackup,
    val localFilePath: String,
)
