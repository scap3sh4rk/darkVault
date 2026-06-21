package com.darkvault.app.drive

import android.content.Context
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
import java.util.concurrent.TimeUnit

class DriveApiClient(
    private val context: Context,
    private val account: GoogleSignInAccount
) {
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val driveScope = "oauth2:https://www.googleapis.com/auth/drive.file"

    private suspend fun token(): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, account.account!!, driveScope)
    }

    suspend fun ensureVaultFolder(savedFolderId: String?): String = withContext(Dispatchers.IO) {
        val t = token()

        if (savedFolderId != null) {
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$savedFolderId?fields=id,trashed")
                .addHeader("Authorization", "Bearer $t")
                .build()
            val resp = http.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = gson.fromJson(resp.body!!.string(), JsonObject::class.java)
                val trashed = body.get("trashed")?.asBoolean ?: false
                if (!trashed) return@withContext savedFolderId
            }
        }

        val queryUrl = "https://www.googleapis.com/drive/v3/files" +
                "?q=name%3D'darkVault'%20and%20mimeType%3D'application%2Fvnd.google-apps.folder'" +
                "%20and%20trashed%3Dfalse&fields=files(id)"
        val searchReq = Request.Builder()
            .url(queryUrl)
            .addHeader("Authorization", "Bearer $t")
            .build()
        val searchResp = http.newCall(searchReq).execute()
        if (searchResp.isSuccessful) {
            val body = gson.fromJson(searchResp.body!!.string(), JsonObject::class.java)
            val files = body.getAsJsonArray("files")
            if (files != null && files.size() > 0) {
                return@withContext files[0].asJsonObject.get("id").asString
            }
        }

        val metaJson = """{"name":"darkVault","mimeType":"application/vnd.google-apps.folder"}"""
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .addHeader("Authorization", "Bearer $t")
            .post(metaJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        val createResp = http.newCall(createReq).execute()
        if (!createResp.isSuccessful) error("Failed to create darkVault folder: ${createResp.code}")
        val body = gson.fromJson(createResp.body!!.string(), JsonObject::class.java)
        body.get("id").asString
    }

    suspend fun listFiles(folderId: String): List<VaultFile> = withContext(Dispatchers.IO) {
        val t = token()
        val q = "q='$folderId'+in+parents+and+trashed%3Dfalse"
        val fields = "files(id,name,size,createdTime,mimeType,appProperties)"
        val url = "https://www.googleapis.com/drive/v3/files?$q&fields=$fields&orderBy=createdTime+desc"

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $t")
            .build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) return@withContext emptyList()

        val body = gson.fromJson(resp.body!!.string(), JsonObject::class.java)
        val files = body.getAsJsonArray("files") ?: return@withContext emptyList()

        files.mapNotNull { el ->
            val obj = el.asJsonObject
            val appProps = obj.getAsJsonObject("appProperties") ?: return@mapNotNull null
            val originalName = appProps.get("originalName")?.asString ?: return@mapNotNull null
            val originalMime = appProps.get("originalMimeType")?.asString ?: "application/octet-stream"
            VaultFile(
                id = obj.get("id")?.asString ?: return@mapNotNull null,
                name = obj.get("name")?.asString ?: return@mapNotNull null,
                originalName = originalName,
                originalMimeType = originalMime,
                size = obj.get("size")?.asLong ?: 0L,
                createdTime = obj.get("createdTime")?.asString ?: ""
            )
        }
    }

    suspend fun uploadFile(
        fileName: String,
        encryptedBytes: ByteArray,
        originalName: String,
        originalMimeType: String,
        folderId: String
    ): String = withContext(Dispatchers.IO) {
        val t = token()
        val boundary = "darkvault_${System.currentTimeMillis()}"

        val metaJson = """
            {
              "name": "$fileName",
              "parents": ["$folderId"],
              "appProperties": {
                "originalName": "$originalName",
                "originalMimeType": "$originalMimeType"
              }
            }
        """.trimIndent()

        val metaPart = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metaJson\r\n"
        val filePart = "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n"
        val closing = "\r\n--$boundary--\r\n"

        val body = metaPart.toByteArray(Charsets.UTF_8) +
                filePart.toByteArray(Charsets.UTF_8) +
                encryptedBytes +
                closing.toByteArray(Charsets.UTF_8)

        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .addHeader("Authorization", "Bearer $t")
            .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) error("Upload failed: ${resp.code} ${resp.body?.string()}")

        val respBody = gson.fromJson(resp.body!!.string(), JsonObject::class.java)
        respBody.get("id").asString
    }

    suspend fun downloadFile(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val t = token()
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $t")
            .build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) error("Download failed: ${resp.code}")
        resp.body!!.bytes()
    }

    suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        val t = token()
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId")
            .addHeader("Authorization", "Bearer $t")
            .delete()
            .build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful && resp.code != 404) error("Delete failed: ${resp.code}")
    }
}
