package com.kumo.bubu.data.backup

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

object BackupArchiveWriter {
    fun write(
        destination: File,
        appVersion: String,
        createdAtEpochMillis: Long,
        recordCounts: Map<String, Int>,
        contents: List<BackupFileContent>,
    ): BackupManifest {
        val manifest = BackupManifestBuilder.create(
            appVersion = appVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            recordCounts = recordCounts,
            files = contents,
        )
        FileOutputStream(destination).use { output ->
            ZipOutputStream(output).use { archive ->
                archive.writeEntry(MANIFEST_ENTRY, JSON.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray())
                contents.sortedBy(BackupFileContent::relativePath).forEach { content ->
                    archive.writeEntry(content.relativePath, content.bytes)
                }
                archive.finish()
                output.fd.sync()
            }
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
    fun isValid(archiveFile: File): Boolean = runCatching {
        ZipFile(archiveFile).use { archive ->
            val manifestEntry = requireNotNull(archive.getEntry("manifest.json"))
            val manifest = archive.getInputStream(manifestEntry).use { input ->
                JSON.decodeFromString(BackupManifest.serializer(), input.readBytes().decodeToString())
            }
            require(manifest.formatVersion == BACKUP_FORMAT_VERSION)
            val expected = manifest.files.associateBy(BackupManifestFile::relativePath)
            require(expected.size == manifest.files.size)
            require(archive.entries().asSequence().map { it.name }.toSet() == expected.keys + "manifest.json")
            expected.forEach { (path, metadata) ->
                val entry = requireNotNull(archive.getEntry(path))
                val bytes = archive.getInputStream(entry).use { it.readBytes() }
                require(bytes.size.toLong() == metadata.sizeBytes)
                require(bytes.sha256() == metadata.sha256)
            }
        }
    }.isSuccess

    private val JSON = Json { ignoreUnknownKeys = false }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte) }
