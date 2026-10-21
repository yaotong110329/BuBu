package com.kumo.bubu.data.backup

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

object BackupArchiveWriter {
    fun write(
        destination: File,
        appVersion: String,
        createdAtEpochMillis: Long,
        recordCounts: Map<String, Int>,
        contents: List<BackupFileContent>,
        openOutput: (File) -> FileOutputStream = ::FileOutputStream,
    ): BackupManifest {
        var totalBytes = 0L
        contents.forEach { content ->
            BackupArchiveLimits.requireSize(content.bytes.size.toLong(), BackupArchiveLimits.maxBytesFor(content.relativePath).toLong())
            totalBytes = BackupArchiveLimits.addContentSize(totalBytes, content.bytes.size.toLong())
        }
        val manifest = BackupManifestBuilder.create(
            appVersion = appVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            recordCounts = recordCounts,
            files = contents,
        )
        val manifestBytes = JSON.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray()
        BackupArchiveLimits.requireSize(manifestBytes.size.toLong(), BackupArchiveLimits.MAX_MANIFEST_BYTES.toLong())
        var completed = false
        try {
            openOutput(destination).use { output ->
                ZipOutputStream(output).use { archive ->
                    archive.writeEntry(MANIFEST_ENTRY, manifestBytes)
                    contents.sortedBy(BackupFileContent::relativePath).forEach { content ->
                        archive.writeEntry(content.relativePath, content.bytes)
                    }
                    archive.finish()
                    BackupArchiveLimits.requireSize(destination.length(), BackupArchiveLimits.MAX_ARCHIVE_BYTES)
                    output.fd.sync()
                }
            }
            completed = true
        } finally {
            if (!completed) destination.delete()
        }
        return manifest
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(java.util.zip.ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private const val MANIFEST_ENTRY = "manifest.json"
    private val JSON = Json {
        encodeDefaults = true
        prettyPrint = true
    }
}

object BackupArchiveVerifier {
    fun isValid(archiveFile: File): Boolean = runCatching { BackupArchiveReader.read(archiveFile) }.isSuccess
}
