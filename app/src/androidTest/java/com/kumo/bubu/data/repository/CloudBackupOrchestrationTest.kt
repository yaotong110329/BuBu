package com.kumo.bubu.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.backup.BackupArchiveReader
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import kotlinx.coroutines.flow.first
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.cloud.drive.HttpGoogleDriveBackupDataSource
import com.kumo.bubu.domain.repository.CloudBackupRetentionException
import org.junit.Assert.assertTrue
import com.kumo.bubu.domain.repository.CloudBackupRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real repository/Room/DataStore/archive/HTTP flow; only remote HTTP responses are scripted. */
@RunWith(AndroidJUnit4::class)
class CloudBackupOrchestrationTest {
    private lateinit var database: BuBuDatabase
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var backup: OfflineBackupRepository
    private lateinit var connection: DataStoreCloudBackupConnection
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java).build()
        directory = File(context.cacheDir, "cloud-orchestration-${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(directory, "connection.preferences_pb") })
        connection = DataStoreCloudBackupConnection(preferences)
        backup = OfflineBackupRepository(context, database, PrivateAttachmentStore(context, "attachment"), nowEpochMillis = { 6_000 })
    }

    @After fun tearDown() = runBlocking<Unit> {
        scope.coroutineContext[Job]?.cancelAndJoin()
        database.close()
        directory.deleteRecursively()
    }

    @Test fun listsAndSortsEveryBackupAcrossShortAndEmptyPages() = runBlocking<Unit> {
        val drive = HttpGoogleDriveBackupDataSource { url ->
            ScriptedConnection(url) { _, _ ->
                val query = url.queryParameters()
                assertEquals("appDataFolder", query["spaces"])
                assertEquals("appProperties has { key='app' and value='BuBu' }", query["q"])
                assertEquals("100", query["pageSize"])
                val requestsTokens = query.getValue("fields").contains("nextPageToken")
                val body = when (query["pageToken"]) {
                    null -> page(listOf(file(1)), if (requestsTokens) "cursor /+1" else null)
                    "cursor /+1" -> page(emptyList(), "cursor /+2")
                    "cursor /+2" -> page(listOf(file(3), file(2)))
                    else -> error("Unexpected fixture page token")
                }
                HttpResponse(200, body)
            }
        }
        val repository: CloudBackupRepository = OfflineCloudBackupRepository(context, backup, drive, connection)

        assertEquals(listOf("backup-3", "backup-2", "backup-1"), repository.listBackups("fixture-token").map { it.id })
    }

    @Test fun successfulSixthUploadRetainsFiveFromTheCompleteInventoryAndCanBeRestored() = runBlocking<Unit> {
        val remote = MemoryDriveHttp()
        (1..5).forEach { remote.seed(it) }
        val repository = repository(remote)
        repository.rememberConnection(CloudBackupAccount("fixture@example.com", 42))

        val uploaded = repository.uploadBackup("fixture-token")

        assertEquals(listOf("uploaded-6", "backup-5", "backup-4", "backup-3", "backup-2"), repository.listBackups("fixture-token").map { it.id })
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("fixture@example.com", 6_000)), repository.observeConnection().first())
        val downloaded = repository.downloadBackup("fixture-token", uploaded.id)
        try {
            val preview = BackupArchiveReader.read(File(downloaded.localFilePath))
            assertEquals(6_000L, preview.manifest.createdAtEpochMillis)
            assertEquals(0, preview.data.vehicles.size)
        } finally {
            repository.discardDownload(downloaded)
        }
    }

    @Test fun failedOldBackupDeletionReportsTheSuccessfulUploadAndPreservesItsBytes() = runBlocking<Unit> {
        val remote = MemoryDriveHttp()
        (1..5).forEach { remote.seed(it) }
        remote.refusedDeletionId = "backup-1"
        val repository = repository(remote)
        repository.rememberConnection(CloudBackupAccount("fixture@example.com", 42))

        val failure = runCatching { repository.uploadBackup("fixture-token") }.exceptionOrNull()

        assertTrue("Successful upload must be distinguished from retention failure", failure is CloudBackupRetentionException)
        val uploaded = (failure as CloudBackupRetentionException).uploadedBackup
        assertEquals("uploaded-6", uploaded.id)
        assertEquals(listOf("uploaded-6", "backup-5", "backup-4", "backup-3", "backup-2", "backup-1"), repository.listBackups("fixture-token").map { it.id })
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("fixture@example.com", 6_000)), repository.observeConnection().first())
        val download = repository.downloadBackup("fixture-token", uploaded.id)
        try {
            assertEquals(6_000L, BackupArchiveReader.read(File(download.localFilePath)).manifest.createdAtEpochMillis)
        } finally {
            repository.discardDownload(download)
        }
    }

    @Test fun failedMediaUploadPreservesOldBackupsAndTimestampThenAllowsRetry() = runBlocking<Unit> {
        val remote = MemoryDriveHttp()
        (1..5).forEach { remote.seed(it) }
        remote.rejectMedia = true
        val repository = repository(remote)
        repository.rememberConnection(CloudBackupAccount("fixture@example.com", 42))

        val failure = runCatching { repository.uploadBackup("fixture-token") }.exceptionOrNull()

        assertTrue(failure is CloudBackupException)
        assertEquals(listOf("backup-5", "backup-4", "backup-3", "backup-2", "backup-1"), repository.listBackups("fixture-token").map { it.id })
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("fixture@example.com", 42)), repository.observeConnection().first())
        remote.rejectMedia = false
        val uploaded = repository.uploadBackup("fixture-token")
        assertEquals("uploaded-7", uploaded.id)
        assertEquals(listOf("uploaded-7", "backup-5", "backup-4", "backup-3", "backup-2"), repository.listBackups("fixture-token").map { it.id })
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("fixture@example.com", 6_000)), repository.observeConnection().first())
    }

    @Test fun failedMetadataCreationNeverDeletesAnExistingBackupOrRecordsSuccess() = runBlocking<Unit> {
        val remote = MemoryDriveHttp()
        (1..5).forEach { remote.seed(it) }
        remote.rejectMetadata = true
        val repository = repository(remote)
        repository.rememberConnection(CloudBackupAccount("fixture@example.com", 42))

        val failure = runCatching { repository.uploadBackup("fixture-token") }.exceptionOrNull()

        assertTrue(failure is CloudBackupException)
        assertEquals(listOf("backup-5", "backup-4", "backup-3", "backup-2", "backup-1"), repository.listBackups("fixture-token").map { it.id })
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("fixture@example.com", 42)), repository.observeConnection().first())
    }

    @Test fun failedMiddlePagePreservesEveryOldBackupAndRetryUsesACompleteInventory() = runBlocking<Unit> {
        val remote = MemoryDriveHttp()
        (1..8).forEach { remote.seed(it, createdAt = it * 100L) }
        remote.refusedListOffset = 2
        val repository = repository(remote)
        repository.rememberConnection(CloudBackupAccount("fixture@example.com", 42))

        val failure = runCatching { repository.uploadBackup("fixture-token") }.exceptionOrNull()

        assertTrue(failure is CloudBackupRetentionException)
        assertEquals("uploaded-6", (failure as CloudBackupRetentionException).uploadedBackup.id)
        assertTrue(runCatching { repository.listBackups("fixture-token") }.exceptionOrNull() is CloudBackupException)
        remote.refusedListOffset = null
        assertEquals(listOf("uploaded-6", "backup-8", "backup-7", "backup-6", "backup-5", "backup-4", "backup-3", "backup-2", "backup-1"), repository.listBackups("fixture-token").map { it.id })
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("fixture@example.com", 6_000)), repository.observeConnection().first())

        val uploaded = repository.uploadBackup("fixture-token")
        assertEquals("uploaded-7", uploaded.id)
        assertEquals(listOf("uploaded-6", "uploaded-7", "backup-8", "backup-7", "backup-6"), repository.listBackups("fixture-token").map { it.id })
    }

    @Test fun explicitlyIncompleteInventoryNeverDeletesOldBackupsOrReportsRetentionSuccess() = runBlocking<Unit> {
        val remote = MemoryDriveHttp()
        (1..8).forEach { remote.seed(it, createdAt = it * 100L) }
        remote.incompleteInventory = true
        val repository = repository(remote)
        repository.rememberConnection(CloudBackupAccount("fixture@example.com", 42))

        val failure = runCatching { repository.uploadBackup("fixture-token") }.exceptionOrNull()

        remote.incompleteInventory = false
        assertEquals(listOf("uploaded-6", "backup-8", "backup-7", "backup-6", "backup-5", "backup-4", "backup-3", "backup-2", "backup-1"), repository.listBackups("fixture-token").map { it.id })
        assertTrue(failure is CloudBackupRetentionException)
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("fixture@example.com", 6_000)), repository.observeConnection().first())
    }

    private fun repository(remote: MemoryDriveHttp): CloudBackupRepository =
        OfflineCloudBackupRepository(context, backup, HttpGoogleDriveBackupDataSource(remote::open), connection)

    private fun file(index: Int): JSONObject = JSONObject()
        .put("id", "backup-$index")
        .put("name", "backup-$index.bubu")
        .put("createdTime", "2026-01-01T00:00:00Z")
        .put("modifiedTime", "2026-01-01T00:00:00Z")
        .put("size", "100")
        .put("appProperties", JSONObject().put("app", "BuBu").put("formatVersion", "1").put("createdAt", index.toString()))

    private fun page(files: List<JSONObject>, next: String? = null): String = JSONObject()
        .put("files", JSONArray(files))
        .apply { if (next != null) put("nextPageToken", next) }
        .toString()
}

private data class HttpResponse(val status: Int, val body: ByteArray) {
    constructor(status: Int, body: String) : this(status, body.toByteArray())
}

/** External HTTP response fixture, never a substitute for BuBu orchestration. */
private class ScriptedConnection(url: URL, respond: (String, ByteArray) -> HttpResponse) : HttpURLConnection(url) {
    private val output = ByteArrayOutputStream()
    private val response by lazy { respond(requestMethod, output.toByteArray()) }
    override fun setRequestMethod(value: String) { method = value }
    override fun getResponseCode(): Int = response.status
    override fun getInputStream() = ByteArrayInputStream(response.body)
    override fun getErrorStream() = ByteArrayInputStream(response.body)
    override fun getOutputStream() = output
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
}

private fun URL.queryParameters(): Map<String, String> = query.orEmpty().split('&')
    .filter { it.isNotEmpty() }
    .associate { part ->
        val components = part.split('=', limit = 2)
        URLDecoder.decode(components[0], "UTF-8") to URLDecoder.decode(components.getOrElse(1) { "" }, "UTF-8")
    }

/** Simulates remote Drive HTTP behavior; storage is accessed only through the real repository. */
private class MemoryDriveHttp {
    private data class Remote(val metadata: JSONObject, var bytes: ByteArray = byteArrayOf())
    private val files = linkedMapOf<String, Remote>()
    var refusedDeletionId: String? = null
    var incompleteInventory = false
    var refusedListOffset: Int? = null
    var rejectMetadata = false
    var rejectMedia = false
    private var nextId = 6

    fun seed(index: Int, createdAt: Long = index * 1000L) {
        val metadata = JSONObject().put("id", "backup-$index").put("name", "backup-$index.bubu")
            .put("createdTime", "2026-01-01T00:00:00Z").put("modifiedTime", "2026-01-01T00:00:00Z").put("size", "100")
            .put("appProperties", JSONObject().put("app", "BuBu").put("formatVersion", "1").put("createdAt", createdAt.toString()))
        files["backup-$index"] = Remote(metadata)
    }

    fun open(url: URL): HttpURLConnection = ScriptedConnection(url) { method, bytes -> respond(url, method, bytes) }

    private fun respond(url: URL, method: String, bytes: ByteArray): HttpResponse {
        val id = url.path.substringAfterLast('/')
        val query = url.queryParameters()
        return when {
            method == "POST" && rejectMetadata -> HttpResponse(503, "Fixture metadata unavailable")
            method == "POST" -> {
                val createdId = "uploaded-${nextId++}"
                val metadata = JSONObject(bytes.toString(Charsets.UTF_8))
                    .put("id", createdId).put("createdTime", "2026-01-01T00:00:00Z")
                    .put("modifiedTime", "2026-01-01T00:00:00Z").put("size", "0")
                files[createdId] = Remote(metadata)
                HttpResponse(200, metadata.toString())
            }
            method == "PATCH" -> {
                if (rejectMedia) HttpResponse(503, "Fixture media unavailable")
                else {
                    val file = files.getValue(id)
                    file.bytes = bytes
                    file.metadata.put("size", bytes.size.toString())
                    HttpResponse(200, file.metadata.toString())
                }
            }
            method == "DELETE" -> {
                if (id == refusedDeletionId) HttpResponse(503, "Fixture deletion unavailable")
                else {
                    files.remove(id)
                    HttpResponse(204, "")
                }
            }
            method == "GET" && query["alt"] == "media" -> HttpResponse(200, files.getValue(id).bytes)
            method == "GET" -> {
                val inventory = files.values.toList()
                val offset = query["pageToken"]?.removePrefix("offset /")?.toInt() ?: 0
                if (offset == refusedListOffset) return HttpResponse(503, "Fixture page unavailable")
                val page = inventory.drop(offset).take(2)
                val body = JSONObject().put("files", JSONArray(page.map { it.metadata }))
                if (query.getValue("fields").contains("incompleteSearch")) body.put("incompleteSearch", incompleteInventory)
                if (offset + page.size < inventory.size && query.getValue("fields").contains("nextPageToken")) {
                    body.put("nextPageToken", "offset /${offset + page.size}")
                }
                HttpResponse(200, body.toString())
            }
            else -> error("Unexpected fixture HTTP request")
        }
    }
}
