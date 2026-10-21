package com.kumo.bubu.data.backup

import java.nio.file.Files
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveSizeContractTest {
    @Test
    fun rejectsAttachmentOverRestoreLimitBeforeOpeningDestination() {
        val destination = Files.createTempFile("bubu-size-", ".bubu").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                BackupArchiveWriter.write(destination, "1.1.1", 1, emptyMap(), listOf(
                    BackupFileContent("data.json", byteArrayOf()),
                    BackupFileContent("attachments/receipt", ByteArray(20 * 1024 * 1024 + 1)),
                ))
            }
            assertEquals(0L, destination.length())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun attachmentAtTwentyMiBLimitRemainsRestorable() = withDestination { file ->
        write(file, contents(1, ByteArray(20 * 1024 * 1024)), attachmentCount = 1)
        assertEquals(BackupArchiveTestData.data(1), BackupArchiveReader.read(file).data)
        assertTrue(BackupArchiveVerifier.isValid(file))
    }

    @Test
    fun rejectsTwentySixTwentyMiBAttachmentsBeforeOpeningDestination() = withDestination { file ->
        assertThrows(IllegalArgumentException::class.java) {
            write(file, contents(26, ByteArray(20 * 1024 * 1024)), attachmentCount = 26)
        }
        assertEquals(0L, file.length())
    }

    @Test
    fun totalContentAt512MiBIsRestorableButOneMoreByteIsRejected() = withDestination { file ->
        val data = BackupArchiveTestData.json(26)
        val shared = ByteArray(20 * 1024 * 1024)
        val lastSize = 12 * 1024 * 1024 - data.size
        val atLimit = listOf(BackupFileContent("data.json", data)) +
            List(25) { BackupFileContent("attachments/receipt-${it + 1}", shared) } +
            BackupFileContent("attachments/receipt-26", ByteArray(lastSize))
        write(file, atLimit, attachmentCount = 26)
        assertEquals(536_870_912L, BackupArchiveReader.read(file).totalByteCount)
        val priorLength = file.length()
        assertThrows(IllegalArgumentException::class.java) {
            write(file, atLimit.dropLast(1) + BackupFileContent("attachments/receipt-26", ByteArray(lastSize + 1)), 26)
        }
        assertEquals(priorLength, file.length())
        assertTrue(BackupArchiveVerifier.isValid(file))
    }

    @Test
    fun dataAtFiftyMiBIsRestorableButOneMoreByteIsRejected() = withDestination { file ->
        val data = BackupArchiveTestData.json()
        val padded = ByteArray(50 * 1024 * 1024) { ' '.code.toByte() }
        data.copyInto(padded)
        write(file, listOf(BackupFileContent("data.json", padded)))
        assertEquals(52_428_800L, BackupArchiveReader.read(file).totalByteCount)
        val priorLength = file.length()
        assertThrows(IllegalArgumentException::class.java) {
            write(file, listOf(BackupFileContent("data.json", padded + ' '.code.toByte())))
        }
        assertEquals(priorLength, file.length())
    }

    @Test
    fun manifestAtOneMiBIsRestorableButOneMoreByteIsRejected() = withDestination { file ->
        val contents = listOf(BackupFileContent("data.json", BackupArchiveTestData.json()))
        val base = BackupManifestBuilder.create("v", 1, BackupArchiveTestData.counts(), contents)
        val overhead = MANIFEST_JSON.encodeToString(BackupManifest.serializer(), base).encodeToByteArray().size - 1
        val version = "v".repeat(1_048_576 - overhead)
        write(file, contents, version = version)
        ZipFile(file).use { assertEquals(1_048_576L, it.getEntry("manifest.json").size) }
        assertEquals(version, BackupArchiveReader.read(file).manifest.appVersion)
        val priorLength = file.length()
        assertThrows(IllegalArgumentException::class.java) { write(file, contents, version = version + "v") }
        assertEquals(priorLength, file.length())
    }

    @Test
    fun verifierRejectsArchiveThatRestoreReaderCannotPreview() = withDestination { file ->
        BackupArchiveWriter.write(file, "1.1.1", 1, emptyMap(), listOf(
            BackupFileContent("data.json", "{\"vehicles\":[]}".encodeToByteArray()),
        ))
        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(file) }
        assertFalse(BackupArchiveVerifier.isValid(file))
    }

    @Test
    fun actualArchiveAboveCandidateLimitIsRejectedWithoutReadingPayload() = withDestination { file ->
        RandomAccessFile(file, "rw").use { it.setLength(536_870_913L) }
        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(file) }
        assertFalse(BackupArchiveVerifier.isValid(file))
    }

    @Test
    fun actualArchiveAtCandidateLimitRemainsReadableWithSmallPayload() = withDestination { file ->
        write(file, listOf(BackupFileContent("data.json", BackupArchiveTestData.json())))
        val zip = file.readBytes()
        // Valid ZIP with a sparse self-extracting prefix exercises the compressed-file limit
        // independently from the uncompressed-content limit without allocating 512 MiB.
        RandomAccessFile(file, "rw").use {
            it.setLength(0)
            it.setLength(536_870_912L)
            it.seek(536_870_912L - zip.size)
            it.write(zip)
        }
        assertEquals(BackupArchiveTestData.data(), BackupArchiveReader.read(file).data)
        RandomAccessFile(file, "rw").use { it.setLength(536_870_913L) }
        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(file) }
    }

    private fun contents(count: Int, bytes: ByteArray) =
        listOf(BackupFileContent("data.json", BackupArchiveTestData.json(count))) +
            List(count) { BackupFileContent("attachments/receipt-${it + 1}", bytes) }

    private fun write(file: File, contents: List<BackupFileContent>, attachmentCount: Int = 0, version: String = "1.1.1") =
        BackupArchiveWriter.write(file, version, 1, BackupArchiveTestData.counts(attachmentCount), contents)

    private fun withDestination(block: (File) -> Unit) {
        val file = Files.createTempFile("bubu-size-", ".bubu").toFile()
        try { block(file) } finally { file.delete() }
    }

    private companion object {
        val MANIFEST_JSON = Json { prettyPrint = true; encodeDefaults = true }
    }
}
