package com.kumo.bubu.data.backup

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveWriterTest {
    @Test
    fun writesManifestDataAndAttachmentsWithVerifiedHashes() {
        val destination = Files.createTempFile("bubu-backup-", ".bubu").toFile()
        try {
            val manifest = BackupArchiveWriter.write(
                destination = destination,
                appVersion = "1.2.3",
                createdAtEpochMillis = 1_700_000_000_000,
                recordCounts = mapOf("vehicles" to 1, "fuelRecords" to 2),
                contents = listOf(
                    BackupFileContent("data.json", "{\"vehicles\":[]}".encodeToByteArray()),
                    BackupFileContent("attachments/receipt-1", byteArrayOf(1, 2, 3)),
                ),
            )

            assertEquals(1, manifest.formatVersion)
            assertTrue(BackupArchiveVerifier.isValid(destination))
            ZipFile(destination).use { archive ->
                assertEquals(
                    listOf("manifest.json", "attachments/receipt-1", "data.json"),
                    archive.entries().asSequence().map { it.name }.toList(),
                )
            }
        } finally {
            destination.delete()
        }
    }
}
