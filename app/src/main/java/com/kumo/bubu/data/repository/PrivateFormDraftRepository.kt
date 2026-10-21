package com.kumo.bubu.data.repository

import com.kumo.bubu.domain.repository.FormDraftRepository
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PrivateFormDraftRepository(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FormDraftRepository {
    private val mutex = Mutex()

    override suspend fun readDraft(draftId: String): String? = mutex.withLock {
        withContext(dispatcher) {
            val file = draftFile(draftId)
            if (file.isFile) file.readText(Charsets.UTF_8) else null
        }
    }

    override suspend fun writeDraft(draftId: String, contents: String) = mutex.withLock {
        withContext(dispatcher) {
            val destination = draftFile(draftId)
            check(directory.isDirectory || directory.mkdirs()) { "Unable to create private form draft storage." }
            val partial = File.createTempFile("$draftId-", ".part", directory)
            var writeFailure: Throwable? = null
            try {
                partial.outputStream().use { output ->
                    output.write(contents.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                Unit
            } catch (failure: Throwable) {
                writeFailure = failure
                throw failure
            } finally {
                if (partial.exists() && !partial.delete()) {
                    val cleanupFailure = IOException("Unable to clean an incomplete private form draft.")
                    if (writeFailure != null) writeFailure.addSuppressed(cleanupFailure) else throw cleanupFailure
                }
            }
        }
    }

    override suspend fun deleteDraft(draftId: String) = mutex.withLock {
        withContext(dispatcher) {
            val file = draftFile(draftId)
            check(!file.exists() || file.delete()) { "Unable to delete private form draft." }
            if (directory.exists()) {
                val interruptedWrites = requireNotNull(directory.listFiles()) { "Unable to inspect private form drafts." }
                    .filter { it.name.startsWith("$draftId-") && it.name.endsWith(".part") }
                interruptedWrites.forEach { partial ->
                    require(partial.canonicalFile.parentFile == directory.canonicalFile) { "Incomplete draft escaped private storage." }
                    check(!partial.exists() || partial.delete()) { "Unable to clean an interrupted private form draft." }
                }
            }
        }
    }

    private fun draftFile(draftId: String): File {
        require(UUID.fromString(draftId).toString() == draftId) { "Invalid form draft identity." }
        val file = File(directory, "$draftId.json").canonicalFile
        require(file.parentFile == directory.canonicalFile) { "Form draft escaped private storage." }
        return file
    }
}
