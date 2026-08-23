package com.kumo.bubu.data.backup

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupArchiveReaderTest {
    @Test
    fun readsVerifiedPreviewWithoutChangingAnyDatabase() {
        val archive = Files.createTempFile("bubu-backup-reader-", ".bubu").toFile()
        try {
            val data = emptyBackupData()
            BackupArchiveWriter.write(
                destination = archive,
                appVersion = "1.0.0",
                createdAtEpochMillis = 10,
                recordCounts = counts(),
                contents = listOf(BackupFileContent("data.json", Json.encodeToString(BackupData.serializer(), data).encodeToByteArray())),
            )

            val preview = BackupArchiveReader.read(archive)

            assertEquals("1.0.0", preview.manifest.appVersion)
            assertEquals(0, preview.data.vehicles.size)
        } finally {
            archive.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeZipPathBeforeAnyRestoreCanStart() {
        val archive = Files.createTempFile("bubu-backup-unsafe-", ".bubu").toFile()
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../unsafe"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }

            BackupArchiveReader.read(archive)
        } finally {
            archive.delete()
        }
    }

    private fun emptyBackupData() = BackupData(
        vehicles = emptyList(),
        fuelRecords = emptyList(),
        serviceTypes = emptyList(),
        serviceRecords = emptyList(),
        serviceItems = emptyList(),
        expenseRecords = emptyList(),
        reminders = emptyList(),
        attachments = emptyList(),
    )

    private fun counts() = mapOf(
        "vehicles" to 0,
        "fuelRecords" to 0,
        "serviceTypes" to 0,
        "serviceRecords" to 0,
        "serviceItems" to 0,
        "expenseRecords" to 0,
        "reminders" to 0,
        "attachments" to 0,
    )
}
