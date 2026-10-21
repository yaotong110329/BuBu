package com.kumo.bubu.data.repository

import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.repository.CloudBackupDownload
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns private downloads until they are handed to a preview, including dispatch cancellation. */
internal class PrivateCloudDownloadStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val ownedPaths = ConcurrentHashMap.newKeySet<String>()

    suspend fun create(writeAndValidate: suspend (File) -> CloudBackup): CloudBackupDownload {
        var destination: File? = null
        try {
            return withContext(dispatcher) {
                check(directory.isDirectory || directory.mkdirs()) { "Unable to create private cloud backup storage." }
                val file = File.createTempFile("download-", ".bubu", directory)
                destination = file
                ownedPaths.add(file.canonicalPath)
                CloudBackupDownload(writeAndValidate(file), file.absolutePath)
            }
        } catch (error: Throwable) {
            destination?.let { file ->
                runCatching { discard(file) }.onFailure { cleanupError ->
                    error.addSuppressed(cleanupError)
                    LOGGER.log(Level.WARNING, "Unable to clean an undelivered private download.", cleanupError)
                }
            }
            throw error
        }
    }

    fun discard(download: CloudBackupDownload) = discard(File(download.localFilePath))

    private fun discard(candidate: File) {
        val file = candidate.canonicalFile
        if (file.path !in ownedPaths) return
        require(file.parentFile == directory.canonicalFile && file.name.startsWith("download-") && file.extension == "bubu")
        check(!file.exists() || file.delete()) { "Private cloud download could not be removed." }
        ownedPaths.remove(file.path)
    }

    private companion object {
        val LOGGER: Logger = Logger.getLogger(PrivateCloudDownloadStore::class.java.name)
    }
}
