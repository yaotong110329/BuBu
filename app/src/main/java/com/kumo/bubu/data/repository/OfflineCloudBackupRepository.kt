package com.kumo.bubu.data.repository

import android.content.Context
import com.kumo.bubu.data.backup.BackupArchiveReader
import com.kumo.bubu.data.cloud.drive.DriveBackupFile
import com.kumo.bubu.data.cloud.drive.GoogleDriveBackupDataSource
import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import com.kumo.bubu.domain.model.CloudBackupRetention
import com.kumo.bubu.domain.model.CloudBackupOrdering
import com.kumo.bubu.domain.model.CloudBackupMetadataMapper
import com.kumo.bubu.domain.repository.CloudBackupDownload
import com.kumo.bubu.domain.repository.CloudBackupRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class OfflineCloudBackupRepository(
    context: Context,
    private val backupRepository: OfflineBackupRepository,
    private val driveDataSource: GoogleDriveBackupDataSource,
    private val connectionStore: DataStoreCloudBackupConnection,
) : CloudBackupRepository {
    private val cacheDirectory = File(context.applicationContext.cacheDir, CACHE_DIRECTORY)

    override fun observeConnection(): Flow<CloudBackupConnection> = connectionStore.observe()

    override suspend fun rememberConnection(account: CloudBackupAccount) = connectionStore.save(account)

    override suspend fun clearConnection() = connectionStore.clear()

    override suspend fun markNeedsAuthorization() = connectionStore.markNeedsAuthorization()

    override suspend fun uploadBackup(accessToken: String): CloudBackup = withContext(Dispatchers.IO) {
        ensureCacheDirectory()
        val temporary = File.createTempFile("cloud-upload-", BACKUP_EXTENSION, cacheDirectory)
        try {
            val backupResult = backupRepository.writePrivateBackup(temporary)
            val namedBackup = File(cacheDirectory, backupResult.fileName)
            if (!temporary.renameTo(namedBackup)) throw CloudBackupException("Unable to name cloud backup.")
            val preview = BackupArchiveReader.read(namedBackup)
            val uploaded = driveDataSource.upload(
                accessToken = accessToken,
                localFile = namedBackup,
                appProperties = metadataFrom(preview),
            ).toCloudBackup()
            connectionStore.updateLastBackupAt(uploaded.createdAtEpochMillis)
            retainNewestBackups(accessToken)
            uploaded
        } catch (error: CloudBackupException) {
            throw error
        } catch (error: Throwable) {
            throw CloudBackupException("Google Drive backup failed.", error)
        } finally {
            temporary.delete()
            cacheDirectory.listFiles()?.filter { it.name.startsWith("bubu-backup-") }?.forEach(File::delete)
        }
    }

    override suspend fun listBackups(accessToken: String): List<CloudBackup> = withContext(Dispatchers.IO) {
        try {
            driveDataSource.list(accessToken)
                .mapNotNull { file -> file.toCloudBackupOrNull() }
                .let(CloudBackupOrdering::newestFirst)
        } catch (error: Throwable) {
            throw CloudBackupException("Google Drive backup list could not be loaded.", error)
        }
    }

    override suspend fun downloadBackup(accessToken: String, backupId: String): CloudBackupDownload = withContext(Dispatchers.IO) {
        ensureCacheDirectory()
        val backup = listBackups(accessToken).firstOrNull { it.id == backupId }
            ?: throw CloudBackupException("The selected Google Drive backup no longer exists.")
        val destination = File(cacheDirectory, "download-${backup.id}$BACKUP_EXTENSION")
        try {
            driveDataSource.download(accessToken, backupId, destination)
            BackupArchiveReader.read(destination)
            CloudBackupDownload(backup, destination.absolutePath)
        } catch (error: CloudBackupException) {
            destination.delete()
            throw error
        } catch (error: Throwable) {
            destination.delete()
            throw CloudBackupException("Google Drive backup could not be downloaded or validated.", error)
        }
    }

    override suspend fun deleteBackup(accessToken: String, backupId: String) = withContext(Dispatchers.IO) {
        try {
            driveDataSource.delete(accessToken, backupId)
        } catch (error: Throwable) {
            throw CloudBackupException("Google Drive backup could not be deleted.", error)
        }
    }

    private fun retainNewestBackups(accessToken: String) {
        val backups = driveDataSource.list(accessToken).mapNotNull { file -> file.toCloudBackupOrNull() }
        CloudBackupRetention.backupsToDelete(backups).forEach { backup -> driveDataSource.delete(accessToken, backup.id) }
    }

    private fun ensureCacheDirectory() {
        if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs()) {
            throw CloudBackupException("Unable to create private cloud backup storage.")
        }
    }

    private fun metadataFrom(preview: com.kumo.bubu.data.backup.BackupPreview): Map<String, String> = mapOf(
        "app" to CloudBackupMetadataMapper.APP_PROPERTY_VALUE,
        "formatVersion" to preview.manifest.formatVersion.toString(),
        "appVersion" to preview.manifest.appVersion,
        "createdAt" to preview.manifest.createdAtEpochMillis.toString(),
        "vehicleCount" to preview.data.vehicles.size.toString(),
        "fuelRecordCount" to preview.data.fuelRecords.size.toString(),
        "maintenanceRecordCount" to preview.data.serviceRecords.size.toString(),
    )

    private fun DriveBackupFile.toCloudBackupOrNull(): CloudBackup? {
        return CloudBackupMetadataMapper.map(
            id = id,
            fileName = name,
            createdAtEpochMillis = createdAtEpochMillis,
            modifiedAtEpochMillis = modifiedAtEpochMillis,
            sizeBytes = sizeBytes,
            appProperties = appProperties,
        )
    }

    private fun DriveBackupFile.toCloudBackup(): CloudBackup =
        toCloudBackupOrNull() ?: throw CloudBackupException("Google Drive returned invalid backup metadata.")

    private companion object {
        const val CACHE_DIRECTORY = "cloud-backup"
        const val BACKUP_EXTENSION = ".bubu"
    }
}

class CloudBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
