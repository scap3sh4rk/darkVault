package com.darkvault.app.cache

import android.content.Context
import com.darkvault.app.crypto.VaultKeyManager
import com.darkvault.app.model.VaultFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ponytail: encrypted persistent folder-listing cache.
// Enables stale-while-revalidate on cold start — app shows last-known listing
// instantly from disk while Drive refresh runs in background.
// Each folder listing is AES-256-GCM encrypted with the session DEK.
// Never stores plaintext; cleared on sign-out/account-switch.
object FolderMetadataStore {

    private const val META_DIR = "folder_meta"
    private val gson = Gson()
    private val listType = object : TypeToken<List<VaultFile>>() {}.type

    // ── Public API ──────────────────────────────────────────────────────────

    /** Returns the cached listing for [folderId], or null on miss / corruption. */
    suspend fun get(context: Context, folderId: String, dek: ByteArray): List<VaultFile>? =
        withContext(Dispatchers.IO) {
            val file = metaFile(context, folderId)
            if (!file.exists()) return@withContext null
            try {
                val enc = file.readBytes()
                val json = VaultKeyManager.decryptWithDek(enc, dek)
                    .toString(Charsets.UTF_8)
                gson.fromJson(json, listType)
            } catch (_: Exception) {
                // Corrupted or encrypted with a different DEK — treat as miss
                file.delete()
                null
            }
        }

    /** Writes [items] to disk, encrypted with [dek]. */
    suspend fun put(context: Context, folderId: String, items: List<VaultFile>, dek: ByteArray) =
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(items)
                val enc = VaultKeyManager.encryptWithDek(json.toByteArray(Charsets.UTF_8), dek)
                metaFile(context, folderId).writeBytes(enc)
            } catch (_: Exception) { /* non-fatal; Drive is always the source of truth */ }
        }

    /** Returns all VaultFile objects from every cached folder listing. Used for offline file index. */
    suspend fun allCachedFiles(context: Context, dek: ByteArray): List<VaultFile> =
        withContext(Dispatchers.IO) {
            metaDir(context).listFiles()
                ?.filter { it.extension == "vault" }
                ?.flatMap { file ->
                    try {
                        val enc = file.readBytes()
                        val json = VaultKeyManager.decryptWithDek(enc, dek).toString(Charsets.UTF_8)
                        gson.fromJson<List<VaultFile>>(json, listType) ?: emptyList()
                    } catch (_: Exception) {
                        file.delete() // corrupted or encrypted with a different DEK — remove it
                        emptyList()
                    }
                } ?: emptyList()
        }

    /** Deletes all cached folder listings. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        metaDir(context).listFiles()?.forEach { it.delete() }
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun metaDir(context: Context) =
        File(context.applicationContext.filesDir, META_DIR).also { it.mkdirs() }

    private fun metaFile(context: Context, folderId: String) =
        File(metaDir(context), "${folderId.replace("/", "_")}.vault")
}
