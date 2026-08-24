package com.kumo.bubu.data.cloud.drive

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject

internal data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val sizeBytes: Long,
    val appProperties: Map<String, String>,
)

internal fun createDriveBackupMetadataJson(
    fileName: String,
    appProperties: Map<String, String>,
): String = buildJsonObject {
    put("name", fileName)
    put("parents", buildJsonArray { add(JsonPrimitive("appDataFolder")) })
    put("mimeType", "application/vnd.com.kumo.bubu.backup")
    put("appProperties", buildJsonObject { appProperties.forEach { (key, value) -> put(key, value) } })
}.toString()

internal class GoogleDriveHttpException(
    val statusCode: Int,
    message: String,
) : Exception(message)

internal interface GoogleDriveBackupDataSource {
    fun upload(accessToken: String, localFile: File, appProperties: Map<String, String>): DriveBackupFile

    fun list(accessToken: String): List<DriveBackupFile>

    fun download(accessToken: String, fileId: String, destination: File)

    fun delete(accessToken: String, fileId: String)
}

internal class HttpGoogleDriveBackupDataSource : GoogleDriveBackupDataSource {
    override fun upload(accessToken: String, localFile: File, appProperties: Map<String, String>): DriveBackupFile {
        val metadata = createDriveBackupMetadataJson(localFile.name, appProperties)
        val created = createMetadataFile(accessToken, metadata)
        return try {
            uploadFileContent(accessToken, created.id, localFile)
        } catch (error: Throwable) {
            runCatching { delete(accessToken, created.id) }
            throw error
        }
    }

    private fun createMetadataFile(accessToken: String, metadata: String): DriveBackupFile {
        val metadataBytes = metadata.encodeToByteArray()
        val connection = openConnection(
            url = "$FILES_URL?fields=$FILE_FIELDS",
            accessToken = accessToken,
            method = "POST",
        ).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setFixedLengthStreamingMode(metadataBytes.size)
        }
        connection.outputStream.use { output ->
            output.write(metadataBytes)
        }
        return connection.readFileResponse()
    }

    private fun uploadFileContent(accessToken: String, fileId: String, localFile: File): DriveBackupFile {
        val connection = openConnection(
            url = "$UPLOAD_URL/$fileId?uploadType=media&fields=$FILE_FIELDS",
            accessToken = accessToken,
            method = "PATCH",
        ).apply {
            doOutput = true
            setRequestProperty("Content-Type", BACKUP_MIME_TYPE)
            setFixedLengthStreamingMode(localFile.length())
        }
        connection.outputStream.use { output ->
            localFile.inputStream().use { input -> input.copyTo(output) }
        }
        return connection.readFileResponse()
    }

    override fun list(accessToken: String): List<DriveBackupFile> {
        val query = "appProperties has { key='app' and value='BuBu' }"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "$FILES_URL?spaces=appDataFolder&q=$encodedQuery&orderBy=createdTime%20desc&fields=files($FILE_FIELDS)&pageSize=100"
        val connection = openConnection(url, accessToken, "GET")
        val body = connection.readResponse()
        val files = JSONObject(body).optJSONArray("files") ?: return emptyList()
        return buildList {
            for (index in 0 until files.length()) add(files.getJSONObject(index).toDriveBackupFile())
        }
    }

    override fun download(accessToken: String, fileId: String, destination: File) {
        val connection = openConnection("$FILES_URL/$fileId?alt=media", accessToken, "GET")
        connection.requireSuccess()
        destination.parentFile?.mkdirs()
        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
    }

    override fun delete(accessToken: String, fileId: String) {
        openConnection("$FILES_URL/$fileId", accessToken, "DELETE").requireSuccess()
    }

    private fun openConnection(url: String, accessToken: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

    private fun HttpURLConnection.readFileResponse(): DriveBackupFile =
        readResponse().let { JSONObject(it).toDriveBackupFile() }

    private fun HttpURLConnection.readResponse(): String {
        requireSuccess()
        return inputStream.bufferedReader().use { it.readText() }
    }

    private fun HttpURLConnection.requireSuccess() {
        if (responseCode !in 200..299) {
            val message = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            disconnect()
            throw GoogleDriveHttpException(
                statusCode = responseCode,
                message = message.ifBlank { "Google Drive request failed ($responseCode)." },
            )
        }
    }

    private fun JSONObject.toDriveBackupFile() = DriveBackupFile(
        id = getString("id"),
        name = getString("name"),
        createdAtEpochMillis = Instant.parse(getString("createdTime")).toEpochMilli(),
        modifiedAtEpochMillis = Instant.parse(getString("modifiedTime")).toEpochMilli(),
        sizeBytes = optString("size", "0").toLongOrNull() ?: 0L,
        appProperties = optJSONObject("appProperties")?.let { properties ->
            properties.keys().asSequence().associateWith { key -> properties.getString(key) }
        }.orEmpty(),
    )

    private companion object {
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val FILE_FIELDS = "id,name,createdTime,modifiedTime,size,appProperties"
        const val BACKUP_MIME_TYPE = "application/vnd.com.kumo.bubu.backup"
        const val TIMEOUT_MILLIS = 30_000
    }
}
