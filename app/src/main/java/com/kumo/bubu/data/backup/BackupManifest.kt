package com.kumo.bubu.data.backup

import java.security.MessageDigest
import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val createdAtEpochMillis: Long,
    val recordCounts: Map<String, Int>,
    val attachmentCount: Int,
    val files: List<BackupManifestFile>,
)

@Serializable
data class BackupManifestFile(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class BackupFileContent(
    val relativePath: String,
    val bytes: ByteArray,
)

object BackupManifestBuilder {
    fun create(
        appVersion: String,
        createdAtEpochMillis: Long,
        recordCounts: Map<String, Int>,
        files: List<BackupFileContent>,
    ): BackupManifest {
        require(appVersion.isNotBlank()) { "Backup app version cannot be blank." }
        require(createdAtEpochMillis >= 0) { "Backup creation time cannot be negative." }
        require(recordCounts.values.all { it >= 0 }) { "Backup record counts cannot be negative." }
        require(files.map(BackupFileContent::relativePath).distinct().size == files.size) {
            "Backup contains duplicate file paths."
        }
        files.forEach { file -> requireSafeRelativePath(file.relativePath) }
        return BackupManifest(
            formatVersion = BACKUP_FORMAT_VERSION,
            appVersion = appVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            recordCounts = recordCounts.toSortedMap(),
            attachmentCount = files.count { it.relativePath.startsWith("attachments/") },
            files = files.sortedBy(BackupFileContent::relativePath).map { file ->
                BackupManifestFile(
                    relativePath = file.relativePath,
                    sizeBytes = file.bytes.size.toLong(),
                    sha256 = file.bytes.sha256(),
                )
            },
        )
    }

    private fun requireSafeRelativePath(path: String) {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path) {
            "Backup path must be a non-empty forward-slash relative path."
        }
        require(path.split('/').all { segment -> segment.isNotBlank() && segment != "." && segment != ".." }) {
            "Backup path is unsafe."
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
