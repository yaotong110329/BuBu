package com.kumo.bubu.data.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kumo.bubu.domain.model.StagedServiceAttachment
import com.kumo.bubu.domain.repository.ServiceAttachmentException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivateAttachmentStore(
    context: Context,
    private val fallbackDisplayNameStem: String,
) {
    private val applicationContext = context.applicationContext
    private val filesRoot = applicationContext.filesDir.canonicalFile
    private val attachmentDirectory = File(filesRoot, ATTACHMENT_DIRECTORY).canonicalFile

    suspend fun stage(
        sourceUri: Uri,
        displayName: String? = null,
        declaredMimeType: String? = null,
    ): StagedServiceAttachment = withContext(Dispatchers.IO) {
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
        var completed = false
        try {
            val input = requireNotNull(resolver.openInputStream(sourceUri)) {
                "Selected attachment cannot be opened."
            }
            input.use { source ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
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
            completed = true
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
        } finally {
            if (!completed) {
                partial.delete()
                destination.delete()
            }
        }
    }

    fun requireManagedFile(relativePath: String) {
        val file = resolveManagedFile(relativePath)
        require(file.isFile) { "Attachment file does not exist in app-private storage." }
        require(file.length() <= MAX_ATTACHMENT_BYTES) { "Attachment exceeds the 20 MB limit." }
    }

    suspend fun deleteManagedFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveManagedFile(relativePath)
        !file.exists() || file.delete()
    }

    suspend fun readManagedBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolveManagedFile(relativePath)
        if (!file.isFile || file.length() > MAX_ATTACHMENT_BYTES) null else file.readBytes()
    }

    suspend fun restoreManagedBytes(
        bytes: ByteArray,
        displayName: String,
        mimeType: String?,
    ): StagedServiceAttachment = withContext(Dispatchers.IO) {
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
        try {
            FileOutputStream(partial).use { output ->
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
            if (!destination.isFile) destination.delete()
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
        const val COPY_BUFFER_SIZE = 16 * 1024
        const val MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L
        val SUPPORTED_IMAGE_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }
}
