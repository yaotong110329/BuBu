package com.kumo.bubu.core.state

import androidx.lifecycle.SavedStateHandle
import com.kumo.bubu.domain.repository.FormDraftRepository
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Keeps small snapshots inline and serializes oversized snapshots into private storage. */
internal class FormDraftPersistence(
    private val handle: SavedStateHandle,
    private val key: String,
    private val repository: FormDraftRepository?,
    private val scope: CoroutineScope,
    private val onWriteState: (pending: Boolean, failed: Boolean) -> Unit,
) {
    private val identityKey = "$key.identity"
    private val fileKey = "$key.file"
    private val ownsFileKey = "$key.ownsFile"
    private val digestKey = "$key.sha256"
    private val revisionKey = "$key.revision"
    val isFileBacked = handle.get<Boolean>(fileKey) == true
    var restoreFailed = false
        private set
    private val logger = Logger.getLogger(FormDraftPersistence::class.java.name)
    val draftId: String = try {
        val savedId = handle.get<String>(identityKey)
            ?: handle.get<String>(key)?.let { Json.parseToJsonElement(it).jsonObject["draftId"]?.jsonPrimitive?.content }
        savedId?.also { require(UUID.fromString(it).toString() == it) } ?: UUID.randomUUID().toString()
    } catch (failure: Exception) {
        restoreFailed = true
        logger.log(Level.WARNING, "Unable to read form draft identity.", failure)
        UUID.randomUUID().toString()
    }
    private var ownsFile = handle.get<Boolean>(ownsFileKey) == true || isFileBacked
    private var closed = false
    private var writer: Job? = null
    private val mutex = Mutex()
    private var revision = handle.get<Long>(revisionKey) ?: 0L

    fun inlineContents(): String? = handle.get<String>(key)

    suspend fun readFile(): String {
        val stored = requireNotNull(repository).readDraft(draftId)
            ?: throw IOException("Private form draft is missing.")
        val snapshot = Json.decodeFromString<PrivateFormDraftSnapshot>(stored)
        val expectedDigest = handle.get<String>(digestKey)
        val expectedRevision = handle.get<Long>(revisionKey)
        // Background work may finish after Android saves the handle. A completely
        // published newer revision retains that work; an older file never replaces
        // a snapshot whose latest write was interrupted.
        if (snapshot.draftId != draftId || expectedDigest == null || expectedRevision == null ||
            expectedRevision <= 0 || snapshot.revision < expectedRevision ||
            snapshot.sha256 != digest(snapshot.contents) ||
            (snapshot.revision == expectedRevision && snapshot.sha256 != expectedDigest)) {
            throw IOException("Private form draft does not match the saved snapshot.")
        }
        revision = snapshot.revision
        handle[revisionKey] = revision
        handle[digestKey] = snapshot.sha256
        return snapshot.contents
    }

    fun recordRestoreFailure(failure: Exception) {
        restoreFailed = true
        logger.log(Level.WARNING, "Unable to restore form draft contents.", failure)
    }

    fun save(contents: String) {
        if (closed) return
        revision += 1
        val currentRevision = revision
        writer?.cancel()
        handle[identityKey] = draftId
        handle[revisionKey] = currentRevision
        if (contents.toByteArray(Charsets.UTF_8).size <= INLINE_LIMIT_BYTES) {
            handle[key] = contents
            handle.remove<Boolean>(fileKey)
            handle.remove<String>(digestKey)
            onWriteState(false, false)
            return
        }
        handle.remove<String>(key)
        handle[fileKey] = true
        val contentsDigest = digest(contents)
        handle[digestKey] = contentsDigest
        handle[ownsFileKey] = true
        ownsFile = true
        onWriteState(true, false)
        writer = scope.launch {
            try {
                mutex.withLock {
                    if (!closed) requireNotNull(repository) { "Oversized form draft requires private storage." }.writeDraft(
                        draftId,
                        Json.encodeToString(PrivateFormDraftSnapshot(draftId, currentRevision, contentsDigest, contents)),
                    )
                }
                if (revision == currentRevision && !closed) onWriteState(false, false)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                logger.log(Level.WARNING, "Unable to save a private form draft.", failure)
                if (revision == currentRevision && !closed) onWriteState(false, true)
            }
        }
    }

    suspend fun discard() {
        closed = true
        writer?.cancel()
        try {
            if (ownsFile) mutex.withLock { requireNotNull(repository).deleteDraft(draftId) }
            handle.remove<String>(key)
            handle.remove<String>(identityKey)
            handle.remove<Boolean>(fileKey)
            handle.remove<Boolean>(ownsFileKey)
            handle.remove<String>(digestKey)
            handle.remove<Long>(revisionKey)
            onWriteState(false, false)
        } catch (failure: Exception) {
            closed = false
            if (failure is CancellationException) throw failure
            logger.log(Level.WARNING, "Unable to discard a private form draft.", failure)
            throw failure
        }
    }

    private fun digest(contents: String): String = MessageDigest.getInstance("SHA-256")
        .digest(contents.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object { private const val INLINE_LIMIT_BYTES = 31 * 1024 }
}

@Serializable
private data class PrivateFormDraftSnapshot(
    val draftId: String,
    val revision: Long,
    val sha256: String,
    val contents: String,
)
