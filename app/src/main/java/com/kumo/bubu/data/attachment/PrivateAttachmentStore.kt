package com.kumo.bubu.data.attachment

import android.content.Context
import android.util.Log
import android.net.Uri
import android.provider.OpenableColumns
import com.kumo.bubu.domain.model.StagedServiceAttachment
import com.kumo.bubu.domain.repository.ServiceAttachmentException
import com.kumo.bubu.data.backup.BackupArchiveLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PrivateAttachmentStore(
    context: Context,
    private val fallbackDisplayNameStem: String,
    private val openRestoreOutput: (File) -> FileOutputStream = ::FileOutputStream,
    private val deleteFile: (File) -> Boolean = File::delete,
) {
    private val applicationContext = context.applicationContext
    private val filesRoot = applicationContext.filesDir.canonicalFile
    private val attachmentDirectory = File(filesRoot, ATTACHMENT_DIRECTORY).canonicalFile
    private val draftDirectory = File(filesRoot, "attachment-drafts/service").canonicalFile

    suspend fun stage(
        sourceUri: Uri,
        displayName: String? = null,
        declaredMimeType: String? = null,
    ): StagedServiceAttachment {
        var copiedFile: File? = null
        var partialFile: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val resolver = applicationContext.contentResolver
                val mimeType = (declaredMimeType ?: resolver.getType(sourceUri))
                    ?.lowercase()
                    ?: throw ServiceAttachmentException.Unsupported()
                val extension = SUPPORTED_IMAGE_TYPES[mimeType]
                    ?: throw ServiceAttachmentException.Unsupported()
                val safeDisplayName = displayName?.trim()?.takeIf(String::isNotEmpty)
                    ?: queryDisplayName(sourceUri)?.trim()?.takeIf(String::isNotEmpty)
                    ?: "$fallbackDisplayNameStem.$extension"
                if (!attachmentDirectory.isDirectory && !attachmentDirectory.mkdirs()) {
                    throw ServiceAttachmentException.CopyFailed()
                }

                val fileName = "${UUID.randomUUID()}.$extension"
                val destination = File(attachmentDirectory, fileName)
                val partial = File(attachmentDirectory, "$fileName.part")
                copiedFile = destination
                partialFile = partial
                try {
                    val input = requireNotNull(resolver.openInputStream(sourceUri)) {
                        "Selected attachment cannot be opened."
                    }
                    input.use { source ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(COPY_BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                copied += count
                                if (copied > MAX_ATTACHMENT_BYTES) {
                                    throw ServiceAttachmentException.TooLarge()
                                }
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    if (!partial.renameTo(destination)) {
                        throw IOException("Attachment could not be moved into private storage.")
                    }
                    StagedServiceAttachment(
                        relativePath = toRelativePath(destination),
                        displayName = safeDisplayName,
                        mimeType = mimeType,
                    )
                } catch (error: ServiceAttachmentException) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    throw ServiceAttachmentException.CopyFailed(error)
                }
            }
        } catch (failure: Throwable) {
            cleanupUndeliveredFiles(failure, partialFile, copiedFile)
            throw failure
        }
    }

    fun requireManagedFile(relativePath: String) {
        val file = resolveManagedFile(relativePath)
        require(file.isFile) { "Attachment file does not exist in app-private storage." }
        require(file.length() <= MAX_ATTACHMENT_BYTES) { "Attachment exceeds the 20 MB limit." }
    }

    suspend fun deleteManagedFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveManagedFile(relativePath)
        !file.exists() || deleteFile(file)
    }

    suspend fun readManagedBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolveManagedFile(relativePath)
        if (!file.isFile || file.length() > MAX_ATTACHMENT_BYTES) return@withContext null
        file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                // A file can grow between the metadata check and the read.
                if (total > MAX_ATTACHMENT_BYTES) return@withContext null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    suspend fun managedByteCount(relativePath: String): Long? = withContext(Dispatchers.IO) {
        val file = resolveManagedFile(relativePath)
        if (file.isFile) file.length() else null
    }

    suspend fun restoreManagedBytes(
        bytes: ByteArray,
        displayName: String,
        mimeType: String?,
    ): StagedServiceAttachment {
        var restoredFile: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val normalizedMimeType = mimeType?.lowercase()
                    ?: throw ServiceAttachmentException.Unsupported()
                val extension = SUPPORTED_IMAGE_TYPES[normalizedMimeType]
                    ?: throw ServiceAttachmentException.Unsupported()
                require(bytes.size <= MAX_ATTACHMENT_BYTES) { "Attachment exceeds the 20 MB limit." }
                if (!attachmentDirectory.isDirectory && !attachmentDirectory.mkdirs()) {
                    throw ServiceAttachmentException.CopyFailed()
                }
                val safeDisplayName = displayName.trim().takeIf(String::isNotEmpty)
                    ?: "$fallbackDisplayNameStem.$extension"
                val fileName = "${UUID.randomUUID()}.$extension"
                val destination = File(attachmentDirectory, fileName)
                val partial = File(attachmentDirectory, "$fileName.part")
                restoredFile = destination
                try {
                    openRestoreOutput(partial).use { output ->
                        output.write(bytes)
                        output.fd.sync()
                    }
                    if (!partial.renameTo(destination)) {
                        throw IOException("Restored attachment could not be moved into private storage.")
                    }
                    StagedServiceAttachment(
                        relativePath = toRelativePath(destination),
                        displayName = safeDisplayName,
                        mimeType = normalizedMimeType,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: ServiceAttachmentException) {
                    throw error
                } catch (error: Throwable) {
                    throw ServiceAttachmentException.CopyFailed(error)
                } finally {
                    partial.delete()
                }
            }
        } catch (failure: Throwable) {
            cleanupUndeliveredFiles(failure, restoredFile)
            throw failure
        }
    }

    private suspend fun cleanupUndeliveredFiles(failure: Throwable, vararg files: File?) =
        withContext(NonCancellable + Dispatchers.IO) {
            files.filterNotNull().forEach { file ->
                runCatching {
                    check(!file.exists() || file.delete()) { "Unable to clean an undelivered attachment." }
                }.onFailure { cleanupFailure ->
                    failure.addSuppressed(cleanupFailure)
                    Log.w("PrivateAttachmentStore", "Undelivered attachment cleanup failed.", cleanupFailure)
                }
            }
        }

    suspend fun listManagedRelativePaths(
        lastModifiedAtOrBefore: Long = Long.MAX_VALUE,
    ): List<String> = withContext(Dispatchers.IO) {
        if (!attachmentDirectory.isDirectory) return@withContext emptyList()
        attachmentDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isFile)
            .filter { file -> file.lastModified() <= lastModifiedAtOrBefore }
            .map(::toRelativePath)
            .toList()
    }

    suspend fun draftPaths(draftId: String): Set<String> = withContext(Dispatchers.IO) {
        val file = draftOwnerFile(draftId)
        if (!file.exists()) return@withContext emptySet()
        require(file.isFile && file.length() <= MAX_DRAFT_OWNER_BYTES) { "Invalid attachment draft owner file." }
        Json.decodeFromString<Set<String>>(file.readText()).also { paths ->
            require(paths.size <= 10) { "Attachment draft is too large." }
            paths.forEach(::resolveManagedFile)
        }
    }

    suspend fun retainDraft(draftId: String, relativePaths: Set<String>) = withContext(Dispatchers.IO) {
        val destination = draftOwnerFile(draftId)
        require(relativePaths.size <= 10) { "Attachment draft is too large." }
        relativePaths.forEach(::requireManagedFile)
        if (relativePaths.isEmpty()) {
            releaseDraft(draftId)
            return@withContext
        }
        check(draftDirectory.isDirectory || draftDirectory.mkdirs()) { "Cannot create attachment draft storage." }
        val partial = File(draftDirectory, "$draftId.json.part")
        try {
            FileOutputStream(partial).use { output ->
                output.write(Json.encodeToString(relativePaths).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            Unit
        } finally {
            partial.delete()
        }
    }

    suspend fun releaseDraft(draftId: String) = withContext(Dispatchers.IO) {
        val file = draftOwnerFile(draftId)
        check(!file.exists() || file.delete()) { "Attachment draft ownership could not be cleared." }
    }

    suspend fun attachmentDrafts(): Map<String, Set<String>> = withContext(Dispatchers.IO) {
        draftDirectory.listFiles().orEmpty().filter { it.isFile && it.extension == "json" }
            .mapNotNull { file ->
                runCatching { file.nameWithoutExtension to draftPaths(file.nameWithoutExtension) }
                    .onFailure { Log.w("BuBuAttachments", "Invalid attachment draft owner will not authorize file access.", it) }
                    .getOrNull()
            }.toMap()
    }

    private fun draftOwnerFile(draftId: String): File {
        require(UUID.fromString(draftId).toString() == draftId) { "Invalid attachment draft identity." }
        val file = File(draftDirectory, "$draftId.json").canonicalFile
        require(file.parentFile == draftDirectory) { "Attachment draft owner is outside private storage." }
        return file
    }

    private fun resolveManagedFile(relativePath: String): File {
        require(relativePath.isNotBlank()) { "Attachment path cannot be blank." }
        require(!File(relativePath).isAbsolute) { "Attachment path must be relative." }
        require('\\' !in relativePath) { "Attachment path must use forward slashes." }
        val resolved = File(filesRoot, relativePath).canonicalFile
        val allowedRoot = attachmentDirectory.path + File.separator
        require(resolved.path.startsWith(allowedRoot)) {
            "Attachment path is outside app-private attachment storage."
        }
        return resolved
    }

    private fun toRelativePath(file: File): String =
        file.relativeTo(filesRoot).invariantSeparatorsPath

    private fun queryDisplayName(sourceUri: Uri): String? =
        applicationContext.contentResolver.query(
            sourceUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }

    private companion object {
        const val ATTACHMENT_DIRECTORY = "attachments/service"
        const val MAX_DRAFT_OWNER_BYTES = 16 * 1024L
        const val COPY_BUFFER_SIZE = 16 * 1024
        const val MAX_ATTACHMENT_BYTES = BackupArchiveLimits.MAX_ATTACHMENT_BYTES
        val SUPPORTED_IMAGE_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }
}
