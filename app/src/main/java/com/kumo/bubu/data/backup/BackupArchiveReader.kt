package com.kumo.bubu.data.backup

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json

data class BackupPreview(
    val manifest: BackupManifest,
    val data: BackupData,
    val totalByteCount: Long,
)

object BackupArchiveReader {
    fun read(archiveFile: File): BackupPreview {
        ZipFile(archiveFile).use { archive ->
            val entries = archive.entries().asSequence().toList()
            require(entries.none { it.isDirectory }) { "Backup cannot contain directory entries." }
            require(entries.map { it.name }.all(::isSafeRelativePath)) { "Backup contains an unsafe path." }
            require(entries.map { it.name }.distinct().size == entries.size) { "Backup contains duplicate paths." }
            val manifestEntry = entries.singleOrNull { it.name == MANIFEST_ENTRY }
                ?: throw IllegalArgumentException("Backup manifest is missing.")
            val manifest = archive.getInputStream(manifestEntry).use { input ->
                JSON.decodeFromString(
                    BackupManifest.serializer(),
                    input.readAtMost(MAX_MANIFEST_BYTES).decodeToString(),
                )
            }
            require(manifest.formatVersion in 1..BACKUP_FORMAT_VERSION) { "Backup format is unsupported." }
            val declaredFiles = manifest.files.associateBy(BackupManifestFile::relativePath)
            require(declaredFiles.size == manifest.files.size) { "Backup manifest contains duplicate paths." }
            require(declaredFiles.keys.all(::isSafeRelativePath)) { "Backup manifest contains an unsafe path." }
            require(manifest.files.all { it.sizeBytes >= 0L }) { "Backup manifest contains an invalid file size." }
            require(manifest.files.totalSizeBytes() <= MAX_TOTAL_CONTENT_BYTES) {
                "Backup is too large to restore safely."
            }
            require(entries.map { it.name }.toSet() == declaredFiles.keys + MANIFEST_ENTRY) {
                "Backup contents do not match its manifest."
            }
            declaredFiles.forEach { (path, metadata) ->
                val entry = entries.single { it.name == path }
                val bytes = archive.getInputStream(entry).use { input ->
                    input.readAtMost(maxBytesFor(path))
                }
                require(bytes.size.toLong() == metadata.sizeBytes) { "Backup file size does not match." }
                require(bytes.sha256() == metadata.sha256) { "Backup file hash does not match." }
            }
            val dataBytes = archive.getInputStream(entries.single { it.name == DATA_ENTRY }).use { input ->
                input.readAtMost(MAX_DATA_BYTES)
            }
            val data = JSON.decodeFromString(BackupData.serializer(), dataBytes.decodeToString())
            validateData(manifest, data)
            return BackupPreview(
                manifest = manifest,
                data = data,
                totalByteCount = manifest.files.sumOf(BackupManifestFile::sizeBytes),
            )
        }
    }

    private fun validateData(manifest: BackupManifest, data: BackupData) {
        require(manifest.recordCounts == recordCounts(data)) { "Backup record counts do not match." }
        require(manifest.attachmentCount == data.attachments.size) { "Backup attachment count does not match." }
        require(data.vehicles.uniquePublicIds(BackupVehicle::publicId)) { "Backup contains duplicate vehicle identifiers." }
        require(data.fuelRecords.uniquePublicIds(BackupFuelRecord::publicId)) { "Backup contains duplicate fuel identifiers." }
        require(data.serviceTypes.uniquePublicIds(BackupServiceType::publicId)) { "Backup contains duplicate service-type identifiers." }
        require(data.serviceRecords.uniquePublicIds(BackupServiceRecord::publicId)) { "Backup contains duplicate service identifiers." }
        require(data.serviceItems.uniquePublicIds(BackupServiceItem::publicId)) { "Backup contains duplicate service-item identifiers." }
        require(data.expenseRecords.uniquePublicIds(BackupExpenseRecord::publicId)) { "Backup contains duplicate expense identifiers." }
        require(data.reminders.uniquePublicIds(BackupReminder::publicId)) { "Backup contains duplicate reminder identifiers." }
        require(data.serviceReminderPreferences.uniquePublicIds(BackupServiceReminderPreference::publicId)) { "Backup contains duplicate service reminder preference identifiers." }
        require(data.attachments.uniquePublicIds(BackupAttachment::publicId)) { "Backup contains duplicate attachment identifiers." }
        val vehicleIds = data.vehicles.mapTo(mutableSetOf(), BackupVehicle::publicId)
        val typeIds = data.serviceTypes.mapTo(mutableSetOf(), BackupServiceType::publicId)
        val serviceIds = data.serviceRecords.mapTo(mutableSetOf(), BackupServiceRecord::publicId)
        val itemIds = data.serviceItems.mapTo(mutableSetOf(), BackupServiceItem::publicId)
        val expenseIds = data.expenseRecords.mapTo(mutableSetOf(), BackupExpenseRecord::publicId)
        val reminderIds = data.reminders.mapTo(mutableSetOf(), BackupReminder::publicId)
        require(data.fuelRecords.all { it.vehiclePublicId in vehicleIds })
        require(data.serviceRecords.all { it.vehiclePublicId in vehicleIds })
        require(data.expenseRecords.all { it.vehiclePublicId in vehicleIds && (it.completedReminderPublicId == null || it.completedReminderPublicId in reminderIds) })
        require(data.serviceItems.all { it.serviceRecordPublicId in serviceIds && (it.serviceTypePublicId == null || it.serviceTypePublicId in typeIds) })
        require(data.reminders.all { reminder ->
            reminder.vehiclePublicId in vehicleIds &&
                (reminder.sourceServiceItemPublicId == null || reminder.sourceServiceItemPublicId in itemIds) &&
                (reminder.completedByServiceRecordPublicId == null || reminder.completedByServiceRecordPublicId in serviceIds) &&
                (reminder.completedByExpenseRecordPublicId == null || reminder.completedByExpenseRecordPublicId in expenseIds)
        })
        require(data.serviceReminderPreferences.all { preference ->
            preference.vehiclePublicId in vehicleIds && preference.serviceTypePublicId in typeIds
        })
        require(data.attachments.all { it.serviceRecordPublicId in serviceIds })
        require(data.attachments.map(BackupAttachment::archivePath).toSet() == manifest.files
            .map(BackupManifestFile::relativePath)
            .filter { it.startsWith(ATTACHMENTS_DIRECTORY) }
            .toSet()) { "Backup attachment list does not match its files." }
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

    private fun <T> List<T>.uniquePublicIds(publicId: (T) -> String): Boolean =
        map(publicId).distinct().size == size

    private fun List<BackupManifestFile>.totalSizeBytes(): Long = fold(0L) { total, file ->
        Math.addExact(total, file.sizeBytes)
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
            path.split('/').all { segment -> segment.isNotBlank() && segment != "." && segment != ".." }

    private fun maxBytesFor(path: String): Int = when {
        path == DATA_ENTRY -> MAX_DATA_BYTES
        path.startsWith(ATTACHMENTS_DIRECTORY) -> MAX_ATTACHMENT_BYTES
        else -> throw IllegalArgumentException("Backup contains an unsupported file.")
    }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            require(total <= maxBytes) { "Backup file is too large to restore safely." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DATA_ENTRY = "data.json"
    private const val ATTACHMENTS_DIRECTORY = "attachments/"
    private const val MAX_MANIFEST_BYTES = 1 * 1024 * 1024
    private const val MAX_DATA_BYTES = 50 * 1024 * 1024
    private const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
    private const val MAX_TOTAL_CONTENT_BYTES = 512L * 1024 * 1024
    private val JSON = Json { ignoreUnknownKeys = false }
}
