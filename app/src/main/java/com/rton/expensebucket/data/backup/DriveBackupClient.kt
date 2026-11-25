package com.rton.expensebucket.data.backup

import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.inject.Inject

internal data class DriveAccount(val id: String, val email: String)
internal data class DriveBackupFile(val id: String, val time: Long)

internal class DriveBackupClient @Inject constructor() {
    fun account(token: String): DriveAccount {
        val user = json(token, "$DRIVE/about?fields=user(permissionId,emailAddress)").getJSONObject("user")
        return DriveAccount(user.getString("permissionId"), user.getString("emailAddress"))
    }

    fun files(token: String, installation: String? = null, limit: Int = 30): List<DriveBackupFile> {
        val query = buildString {
            append("name = '").append(FILE_NAME).append("'")
            installation?.let { append(" and appProperties has { key='installation' and value='$it' }") }
        }
        val files = mutableListOf<DriveBackupFile>()
        var pageToken: String? = null
        do {
            val url = Uri.parse("$DRIVE/files").buildUpon()
                .appendQueryParameter("spaces", "appDataFolder")
                .appendQueryParameter("q", query)
                .appendQueryParameter("orderBy", "createdTime desc")
                .appendQueryParameter("pageSize", "100")
                .appendQueryParameter("fields", "nextPageToken,files(id,createdTime)")
            pageToken?.let { url.appendQueryParameter("pageToken", it) }
            val page = json(token, url.build().toString())
            val records = page.getJSONArray("files")
            for (index in 0 until minOf(records.length(), limit - files.size)) {
                val item = records.getJSONObject(index)
                files += DriveBackupFile(
                    item.getString("id"),
                    Instant.parse(item.getString("createdTime")).toEpochMilli()
                )
            }
            pageToken = page.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null && files.size < limit)
        return files
    }

    fun download(token: String, file: DriveBackupFile): ByteArray =
        request(token, "$DRIVE/files/${Uri.encode(file.id)}?alt=media") { connection ->
            readBounded(connection, BackupEncryption.maxFileBytes)
        }

    fun upload(token: String, installation: String, encrypted: ByteArray): DriveBackupFile {
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("mimeType", "application/octet-stream")
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("appProperties", JSONObject().put("installation", installation))
            .toString().toByteArray(Charsets.UTF_8)
        val session = request(
            token,
            "$UPLOAD/files?uploadType=resumable&fields=id,createdTime",
            method = "POST",
            type = "application/json; charset=UTF-8",
            body = metadata,
            headers = mapOf(
                "X-Upload-Content-Type" to "application/octet-stream",
                "X-Upload-Content-Length" to encrypted.size.toString()
            )
        ) { connection -> URL(checkNotNull(connection.getHeaderField("Location"))) }
        check(session.protocol == "https" && session.host == "www.googleapis.com")
        val uploaded = request(
            token, session.toString(), method = "PUT", type = "application/octet-stream", body = encrypted
        ) { connection ->
            JSONObject(readBounded(connection, MAX_METADATA_BYTES).toString(Charsets.UTF_8))
        }
        return DriveBackupFile(uploaded.getString("id"), Instant.parse(uploaded.getString("createdTime")).toEpochMilli())
    }

    fun delete(token: String, file: DriveBackupFile) {
        request(token, "$DRIVE/files/${Uri.encode(file.id)}", method = "DELETE") { }
    }

    private fun json(token: String, url: String): JSONObject =
        request(token, url) { connection ->
            JSONObject(readBounded(connection, MAX_METADATA_BYTES).toString(Charsets.UTF_8))
        }

    private fun readBounded(connection: HttpURLConnection, limit: Int): ByteArray {
        if (connection.contentLengthLong > limit) throw BackupTooLargeException()
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size() + count > limit) throw BackupTooLargeException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun <T> request(
        token: String,
        url: String,
        method: String = "GET",
        type: String? = null,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
        onSuccess: (HttpURLConnection) -> T
    ): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Authorization", "Bearer $token")
            type?.let { setRequestProperty("Content-Type", it) }
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            body?.let {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(it.size)
                connection.outputStream.use { output -> output.write(it) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val reason = if (status == 403) {
                    runCatching {
                        connection.errorStream.bufferedReader().use { it.readLine().orEmpty() }
                    }.getOrDefault("")
                } else {
                    ""
                }
                throw DriveRequestException(status, reason.contains("rateLimit", ignoreCase = true))
            }
            onSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val FILE_NAME = "expense-bucket-backup-v1"
        const val DRIVE = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val MAX_METADATA_BYTES = 256 * 1024
    }
}

internal class DriveRequestException(val status: Int, private val rateLimited: Boolean) : IOException() {
    val retryable get() = rateLimited || status == 408 || status == 429 || status in 500..599
}
