package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.kumo.bubu.BuildConfig
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.backup.BackupArchiveWriter
import com.kumo.bubu.data.backup.BackupArchiveVerifier
import com.kumo.bubu.data.backup.BackupData
import com.kumo.bubu.data.backup.BackupDataBuilder
import com.kumo.bubu.data.backup.BackupDataSource
import com.kumo.bubu.data.backup.BackupFileContent
import com.kumo.bubu.domain.repository.BackupRepository
import com.kumo.bubu.domain.repository.BackupResult
import com.kumo.bubu.domain.repository.BackupReminderSettings
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class OfflineBackupRepository(
    context: Context,
    private val database: BuBuDatabase,
    private val attachmentStore: PrivateAttachmentStore,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val backupReminderSettings: BackupReminderSettings? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : BackupRepository {
    private val applicationContext = context.applicationContext
    private val cacheDirectory = File(applicationContext.cacheDir, CACHE_DIRECTORY)

    override suspend fun createBackup(destinationUriString: String): BackupResult = withContext(Dispatchers.IO) {
        val destinationUri = Uri.parse(destinationUriString)
        if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs()) {
            throw BackupException("Unable to create private backup storage.")
        }
        val partial = File(cacheDirectory, ".${UUID.randomUUID()}.part")
        try {
            val result = writePrivateBackup(partial)
            FileInputStream(partial).use { input ->
                requireNotNull(applicationContext.contentResolver.openOutputStream(destinationUri, "w")) {
                    "Selected backup destination cannot be opened."
                }.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            backupReminderSettings?.recordSuccessfulBackup(today().toEpochDay())
            return@withContext result
        } catch (error: BackupException) {
            deleteIncompleteDestination(destinationUri)
            throw error
        } catch (error: Throwable) {
            deleteIncompleteDestination(destinationUri)
            throw BackupException("Unable to create backup.", error)
        } finally {
            partial.delete()
        }
    }

    internal suspend fun writePrivateBackup(destination: File): BackupResult = withContext(Dispatchers.IO) {
        val parent = requireNotNull(destination.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw BackupException("Unable to create private backup storage.")
        }
        val snapshot = database.withTransaction {
            BackupDataBuilder.build(
                BackupDataSource(
                    vehicles = database.vehicleDao().getAll(),
                    fuelRecords = database.fuelRecordDao().getAllForExport(),
                    serviceTypes = database.serviceTypeDao().getAll(),
                    serviceRecords = database.serviceRecordDao().getAllForExport(),
                    serviceItems = database.serviceItemDao().getAllForExport(),
                    expenseRecords = database.expenseRecordDao().getAllForExport(),
                    reminders = database.vehicleReminderDao().getAll(),
                    attachments = database.serviceAttachmentDao().getAllForExport(),
                ),
            )
        }
        val contents = buildContents(snapshot.data, snapshot.attachmentSources)
        BackupArchiveWriter.write(
            destination = destination,
            appVersion = BuildConfig.VERSION_NAME,
            createdAtEpochMillis = nowEpochMillis(),
            recordCounts = recordCounts(snapshot.data),
            contents = contents,
        )
        if (!BackupArchiveVerifier.isValid(destination)) {
            throw BackupException("Backup validation failed.")
        }
        BackupResult(
            fileName = "bubu-backup-${now().format(FILE_NAME_TIME_FORMAT)}.bubu",
            byteCount = destination.length(),
        )
    }

    private suspend fun buildContents(
        data: BackupData,
        attachmentSources: List<com.kumo.bubu.data.backup.BackupAttachmentSource>,
    ): List<BackupFileContent> = buildList {
        add(BackupFileContent(DATA_ENTRY, JSON.encodeToString(BackupData.serializer(), data).encodeToByteArray()))
        attachmentSources.forEach { source ->
            val bytes = attachmentStore.readManagedBytes(source.relativePath)
                ?: throw BackupException("A referenced attachment is unavailable.")
            add(BackupFileContent(source.archivePath, bytes))
        }
    }

    private fun recordCounts(data: BackupData): Map<String, Int> = mapOf(
        "vehicles" to data.vehicles.size,
        "fuelRecords" to data.fuelRecords.size,
        "serviceTypes" to data.serviceTypes.size,
        "serviceRecords" to data.serviceRecords.size,
        "serviceItems" to data.serviceItems.size,
        "expenseRecords" to data.expenseRecords.size,
        "reminders" to data.reminders.size,
        "attachments" to data.attachments.size,
    )

    private fun deleteIncompleteDestination(destinationUri: Uri) {
        runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, destinationUri) }
    }

    private companion object {
        const val CACHE_DIRECTORY = "backup"
        const val DATA_ENTRY = "data.json"
        val FILE_NAME_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        val JSON = Json {
            encodeDefaults = true
            prettyPrint = true
        }
    }
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
