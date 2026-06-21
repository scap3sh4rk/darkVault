package com.darkvault.app.drive

import android.content.Context
import com.darkvault.app.model.StorageInfo
import com.darkvault.app.model.VaultFile
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class DriveApiClient(
    private val context: Context,
    private val account: GoogleSignInAccount
) {
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val driveScope = "oauth2:https://www.googleapis.com/auth/drive.file"

    private suspend fun token(): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, account.account!!, driveScope)
    }

    // ── Retry helper ───────────────────────────────────────────────────────

    /**
     * Wraps a suspend block with exponential back-off retry for transient Drive
     * errors (429, 500, 503).  Respects the Retry-After header when present.
     */
    private suspend fun <T> withRetry(maxAttempts: Int = 4, block: suspend () -> T): T {
        var attempt = 0
        var delayMs = 1000L
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) throw e
                val message = e.message ?: ""
                if (!message.contains("429") && !message.contains("500") && !message.contains("503")) throw e
                // Extract numeric Retry-After if present in the message (format "Retry-After: N")
                val retryAfterSecs = Regex("Retry-After:\\s*(\\d+)").find(message)
                    ?.groupValues?.get(1)?.toLongOrNull()
                val waitMs = if (retryAfterSecs != null) retryAfterSecs * 1000L else delayMs
                kotlinx.coroutines.delay(waitMs)
                delayMs = minOf(delayMs * 2, 30_000L)
            }
        }
    }

    // ── Folder management ──────────────────────────────────────────────────

    suspend fun ensureVaultFolder(savedFolderId: String?): String = withContext(Dispatchers.IO) {
        val t = token()

        if (savedFolderId != null) {
            val resp = http.newCall(
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$savedFolderId?fields=id,trashed")
                    .addHeader("Authorization", "Bearer $t")
                    .build()
            ).execute()
            if (resp.isSuccessful) {
                val obj = gson.fromJson(resp.body!!.string(), JsonObject::class.java)
                if (obj.get("trashed")?.asBoolean != true) return@withContext savedFolderId
            }
        }

        val q = java.net.URLEncoder.encode(
            "name='darkVault' and mimeType='application/vnd.google-apps.folder' and trashed=false",
            "UTF-8"
        )
        val searchResp = http.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)")
                .addHeader("Authorization", "Bearer $t")
                .build()
        ).execute()

        if (searchResp.isSuccessful) {
            val body = gson.fromJson(searchResp.body!!.string(), JsonObject::class.java)
            val files = body.getAsJsonArray("files")
            if (files != null && files.size() > 0) {
                return@withContext files[0].asJsonObject.get("id").asString
            }
        }

        val createResp = http.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id")
                .addHeader("Authorization", "Bearer $t")
                .post("""{"name":"darkVault","mimeType":"application/vnd.google-apps.folder"}"""
                    .toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
        ).execute()

        if (!createResp.isSuccessful) error("Failed to create darkVault folder: ${createResp.code}")
        gson.fromJson(createResp.body!!.string(), JsonObject::class.java).get("id").asString
    }

    suspend fun ensureSubFolder(name: String, parentFolderId: String): String = withContext(Dispatchers.IO) {
        val t = token()
        val safeName = name.replace("'", "\\'")
        val q = java.net.URLEncoder.encode(
            "name='$safeName' and mimeType='application/vnd.google-apps.folder'" +
                    " and '$parentFolderId' in parents and trashed=false",
            "UTF-8"
        )
        val searchResp = http.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)")
                .addHeader("Authorization", "Bearer $t")
                .build()
        ).execute()

        if (searchResp.isSuccessful) {
            val body = gson.fromJson(searchResp.body!!.string(), JsonObject::class.java)
            val files = body.getAsJsonArray("files")
            if (files != null && files.size() > 0) {
                return@withContext files[0].asJsonObject.get("id").asString
            }
        }

        val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
        val createResp = http.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id")
                .addHeader("Authorization", "Bearer $t")
                .post("""{"name":"$escaped","mimeType":"application/vnd.google-apps.folder","parents":["$parentFolderId"]}"""
                    .toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
        ).execute()
        if (!createResp.isSuccessful) error("Failed to create folder '$name': ${createResp.code}")
        gson.fromJson(createResp.body!!.string(), JsonObject::class.java).get("id").asString
    }

    // ── File listing ───────────────────────────────────────────────────────

    /** Returns both .vault files and sub-folders inside [folderId]. */
    suspend fun listItems(folderId: String): List<VaultFile> = withRetry {
        withContext(Dispatchers.IO) {
            val t = token()
            val q = java.net.URLEncoder.encode("'$folderId' in parents and trashed=false", "UTF-8")
            val fields = "files(id,name,size,createdTime,modifiedTime,mimeType,appProperties)"
            val resp = http.newCall(
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=$fields&orderBy=name")
                    .addHeader("Authorization", "Bearer $t")
                    .build()
            ).execute()

            if (!resp.isSuccessful) {
                error("listItems failed: ${resp.code}")
            }

            val body = gson.fromJson(resp.body!!.string(), JsonObject::class.java)
            val files = body.getAsJsonArray("files") ?: return@withContext emptyList()

            files.mapNotNull { el ->
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                val name = obj.get("name")?.asString ?: return@mapNotNull null
                val mimeType = obj.get("mimeType")?.asString ?: ""
                val createdTime = obj.get("createdTime")?.asString ?: ""
                val modifiedTime = obj.get("modifiedTime")?.asString ?: ""

                if (mimeType == "application/vnd.google-apps.folder") {
                    VaultFile(
                        id = id,
                        name = name,
                        originalName = name,
                        originalMimeType = mimeType,
                        size = 0L,
                        createdTime = createdTime,
                        modifiedTime = modifiedTime,
                        isFolder = true
                    )
                } else {
                    val appProps = obj.getAsJsonObject("appProperties") ?: return@mapNotNull null
                    val originalName = appProps.get("originalName")?.asString ?: return@mapNotNull null
                    val originalMime = appProps.get("originalMimeType")?.asString ?: "application/octet-stream"
                    VaultFile(
                        id = id,
                        name = name,
                        originalName = originalName,
                        originalMimeType = originalMime,
                        size = obj.get("size")?.asLong ?: 0L,
                        createdTime = createdTime,
                        modifiedTime = modifiedTime,
                        isFolder = false
                    )
                }
            }
        }
    }

    // Kept for compatibility with existing callers
    suspend fun listFiles(folderId: String): List<VaultFile> = listItems(folderId)

    // ── Duplicate / rename detection ───────────────────────────────────────

    suspend fun fileExistsByOriginalName(originalName: String, folderId: String): Boolean =
        withContext(Dispatchers.IO) {
            val t = token()
            val safe = originalName.take(100).replace("'", "\\'")
            val q = java.net.URLEncoder.encode(
                "appProperties has { key='originalName' and value='$safe' }" +
                        " and '$folderId' in parents and trashed=false",
                "UTF-8"
            )
            val resp = http.newCall(
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)")
                    .addHeader("Authorization", "Bearer $t")
                    .build()
            ).execute()
            if (!resp.isSuccessful) return@withContext false
            val body = gson.fromJson(resp.body!!.string(), JsonObject::class.java)
            val files = body.getAsJsonArray("files")
            files != null && files.size() > 0
        }

    /**
     * Returns a unique originalName for the file, adding a counter suffix
     * (e.g. "photo (2).jpg") if the base name already exists.
     */
    suspend fun findUniqueOriginalName(baseName: String, folderId: String): String {
        if (!fileExistsByOriginalName(baseName, folderId)) return baseName
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var counter = 2
        while (counter <= 999) {
            val candidate = "$stem ($counter)$ext"
            if (!fileExistsByOriginalName(candidate, folderId)) return candidate
            counter++
        }
        return "$stem (${System.currentTimeMillis()})$ext"
    }

    // ── Idempotent upload key ──────────────────────────────────────────────

    /**
     * Checks whether a file with this clientId (job UUID) was already uploaded.
     * Used to make retried uploads idempotent.
     */
    suspend fun fileExistsByClientId(clientId: String, folderId: String): Boolean =
        withContext(Dispatchers.IO) {
            val t = token()
            val q = java.net.URLEncoder.encode(
                "appProperties has { key='clientId' and value='$clientId' }" +
                    " and '$folderId' in parents and trashed=false", "UTF-8"
            )
            val resp = http.newCall(
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)")
                    .addHeader("Authorization", "Bearer $t")
                    .build()
            ).execute()
            if (!resp.isSuccessful) return@withContext false
            val files = gson.fromJson(resp.body!!.string(), JsonObject::class.java).getAsJsonArray("files")
            files != null && files.size() > 0
        }

    // ── Upload ─────────────────────────────────────────────────────────────

    /**
     * Starts a Drive resumable upload session.
     * [clientId] is the UploadJob UUID, stored in appProperties for idempotent retries.
     */
    suspend fun startResumableSession(
        fileName: String,
        originalName: String,
        originalMimeType: String,
        folderId: String,
        totalBytes: Long,
        clientId: String = ""
    ): String = withContext(Dispatchers.IO) {
        val t = token()
        val escapedFile = fileName.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedOrig = originalName.take(100).replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedMime = originalMimeType.take(100).replace("\\", "\\\\").replace("\"", "\\\"")
        val clientIdProp = if (clientId.isNotEmpty()) ""","clientId":"$clientId"""" else ""
        val metaJson = """{"name":"$escapedFile","parents":["$folderId"],"appProperties":{"originalName":"$escapedOrig","originalMimeType":"$escapedMime","updatedAt":"${System.currentTimeMillis()}"$clientIdProp}}"""

        val resp = http.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id")
                .addHeader("Authorization", "Bearer $t")
                .addHeader("X-Upload-Content-Type", "application/octet-stream")
                .addHeader("X-Upload-Content-Length", totalBytes.toString())
                .post(metaJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
        ).execute()
        if (!resp.isSuccessful) error("Failed to start resumable session: ${resp.code}")
        resp.header("Location") ?: error("No Location header in resumable upload response")
    }

    /** Uploads chunks to an active session. Returns Drive file ID on completion. */
    suspend fun uploadChunked(
        sessionUri: String,
        data: ByteArray,
        startOffset: Long = 0L,
        onProgress: suspend (uploaded: Long, total: Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val total = data.size.toLong()
        val chunkSize = 256 * 1024 // 256 KB
        var offset = startOffset

        while (offset < total) {
            val end = minOf(offset + chunkSize - 1, total - 1)
            val chunk = data.copyOfRange(offset.toInt(), (end + 1).toInt())

            val resp = withRetry {
                http.newCall(
                    Request.Builder()
                        .url(sessionUri)
                        .addHeader("Content-Range", "bytes $offset-$end/$total")
                        .put(chunk.toRequestBody("application/octet-stream".toMediaType()))
                        .build()
                ).execute().also { r ->
                    if (r.code != 200 && r.code != 201 && r.code != 308) {
                        error("Upload chunk failed: ${r.code} at offset $offset")
                    }
                }
            }

            when (resp.code) {
                200, 201 -> {
                    onProgress(total, total)
                    return@withContext gson.fromJson(resp.body!!.string(), JsonObject::class.java)
                        .get("id").asString
                }
                308 -> {
                    offset = end + 1
                    onProgress(offset, total)
                }
                else -> error("Upload chunk failed: ${resp.code} at offset $offset")
            }
        }
        error("Upload finished without receiving file ID")
    }

    /** Query how many bytes Drive has received for an interrupted session. */
    suspend fun queryResumeOffset(sessionUri: String, totalBytes: Long): Long =
        withContext(Dispatchers.IO) {
            val resp = http.newCall(
                Request.Builder()
                    .url(sessionUri)
                    .addHeader("Content-Range", "bytes */$totalBytes")
                    .addHeader("Content-Length", "0")
                    .put(ByteArray(0).toRequestBody())
                    .build()
            ).execute()
            when (resp.code) {
                200, 201 -> totalBytes // Already complete
                308 -> {
                    val range = resp.header("Range") ?: return@withContext 0L
                    range.substringAfter("bytes=0-").toLongOrNull()?.plus(1) ?: 0L
                }
                else -> 0L
            }
        }

    /** Convenience — full upload with progress, returns file ID. */
    suspend fun uploadFile(
        fileName: String,
        encryptedBytes: ByteArray,
        originalName: String,
        originalMimeType: String,
        folderId: String
    ): String {
        val sessionUri = startResumableSession(fileName, originalName, originalMimeType, folderId, encryptedBytes.size.toLong())
        return uploadChunked(sessionUri, encryptedBytes) { _, _ -> }
    }

    // ── Download ───────────────────────────────────────────────────────────

    suspend fun downloadFile(fileId: String): ByteArray = downloadFileWithProgress(fileId) { _, _ -> }

    suspend fun downloadFileWithProgress(
        fileId: String,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ): ByteArray = withRetry {
        withContext(Dispatchers.IO) {
            val t = token()
            val resp = http.newCall(
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .addHeader("Authorization", "Bearer $t")
                    .build()
            ).execute()
            if (!resp.isSuccessful) error("Download failed: ${resp.code}")

            val body = resp.body ?: error("Empty download response")
            val total = body.contentLength()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(65536)
            val stream = body.byteStream()
            var read: Int
            var downloaded = 0L

            while (stream.read(buf).also { read = it } != -1) {
                out.write(buf, 0, read)
                downloaded += read
                if (total > 0) onProgress(downloaded, total)
            }
            out.toByteArray()
        }
    }

    // ── Delete / Trash ─────────────────────────────────────────────────────

    /** Permanently deletes the file from Drive. */
    suspend fun deleteFile(fileId: String) = withRetry {
        withContext(Dispatchers.IO) {
            val t = token()
            val resp = http.newCall(
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId")
                    .addHeader("Authorization", "Bearer $t")
                    .delete()
                    .build()
            ).execute()
            if (!resp.isSuccessful && resp.code != 404) error("Delete failed: ${resp.code}")
        }
    }

    /** Moves a file to Drive trash (soft delete). */
    suspend fun trashFile(fileId: String) = withContext(Dispatchers.IO) {
        val t = token()
        val resp = http.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=id")
                .addHeader("Authorization", "Bearer $t")
                .method("PATCH", """{"trashed":true}""".toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
        ).execute()
        if (!resp.isSuccessful && resp.code != 404) error("Trash failed: ${resp.code}")
    }

    /** Restores a trashed file. */
    suspend fun restoreFile(fileId: String) = withContext(Dispatchers.IO) {
        val t = token()
        val resp = http.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=id")
                .addHeader("Authorization", "Bearer $t")
                .method("PATCH", """{"trashed":false}""".toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
        ).execute()
        if (!resp.isSuccessful) error("Restore failed: ${resp.code}")
    }

    // ── Storage quota ──────────────────────────────────────────────────────

    suspend fun getStorageInfo(vaultFolderId: String): StorageInfo = withContext(Dispatchers.IO) {
        val t = token()
        val aboutResp = http.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
                .addHeader("Authorization", "Bearer $t")
                .build()
        ).execute()

        val limit: Long
        val totalUsed: Long
        if (aboutResp.isSuccessful) {
            val quota = gson.fromJson(aboutResp.body!!.string(), JsonObject::class.java)
                .getAsJsonObject("storageQuota")
            limit = quota?.get("limit")?.asLong ?: -1L
            totalUsed = quota?.get("usage")?.asLong ?: 0L
        } else {
            limit = -1L; totalUsed = 0L
        }

        val vaultUsed = listItems(vaultFolderId).filter { !it.isFolder }.sumOf { it.size }
        StorageInfo(vaultUsed, totalUsed, limit)
    }
}
