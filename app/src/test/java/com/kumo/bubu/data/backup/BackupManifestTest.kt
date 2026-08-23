package com.kumo.bubu.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManifestTest {
    @Test
    fun recordsDeterministicSizesAndSha256ForDeclaredFiles() {
        val manifest = BackupManifestBuilder.create(
            appVersion = "0.1.0",
            createdAtEpochMillis = 1_000,
            recordCounts = mapOf("vehicles" to 1, "attachments" to 1),
            files = listOf(
                BackupFileContent("data.json", "abc".encodeToByteArray()),
                BackupFileContent("attachments/receipt.jpg", byteArrayOf(1, 2)),
            ),
        )

        assertEquals(BACKUP_FORMAT_VERSION, manifest.formatVersion)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            manifest.files.single { it.relativePath == "data.json" }.sha256,
        )
        assertEquals(2, manifest.files.single { it.relativePath == "attachments/receipt.jpg" }.sizeBytes)
    }

    @Test
    fun rejectsUnsafeOrDuplicateArchivePaths() {
        val unsafe = runCatching {
            BackupManifestBuilder.create("0.1.0", 1, emptyMap(), listOf(BackupFileContent("../data.json", byteArrayOf())))
        }
        val duplicate = runCatching {
            BackupManifestBuilder.create(
                "0.1.0",
                1,
                emptyMap(),
                listOf(BackupFileContent("data.json", byteArrayOf()), BackupFileContent("data.json", byteArrayOf())),
            )
        }

        assertTrue(unsafe.isFailure)
        assertTrue(duplicate.isFailure)
    }
}
