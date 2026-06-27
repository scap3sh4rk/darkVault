package com.darkvault.app.cache

import android.content.Context
import com.darkvault.app.crypto.VaultKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// ponytail: persistent encrypted-file disk cache in filesDir/vault_cache/
// Only ciphertext is stored — no plaintext, no keys, no filenames in the index.
// Index (.index.json) is unencrypted; it holds only opaque fileIds + sizes.
// Original filenames/mimetypes are kept in FolderMetadataStore (encrypted separately).
object LocalVaultCache {

    // ponytail: 500 MB default; user-configurable via PreferencesManager.cacheCap
    const val DEFAULT_MAX_BYTES = 500L * 1024 * 1024

    private const val VAULT_DIR   = "vault_cache"
    private const val INDEX_FILE  = ".index.json"

    private val mutex = Mutex()

    // In-memory index: fileId → IndexEntry
    // Null means "not yet loaded from disk"
    @Volatile private var index: MutableMap<String, IndexEntry>? = null

    data class IndexEntry(
        val fileId: String,
        val encryptedSize: Long,
        val modifiedTime: String,
        val lastAccessedMs: Long,
        val isPinned: Boolean = false
    )

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the raw encrypted bytes for [fileId] if cached and [modifiedTime] matches.
     * Null on miss or stale entry (stale entries are evicted).
     */
    suspend fun getEncryptedBytes(
        context: Context,
        fileId: String,
        modifiedTime: String
    ): ByteArray? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = dir(context)
            val vaultFile = File(dir, "$fileId.vault")
            if (!vaultFile.exists()) return@withContext null

            val idx = loadIndex(dir)
            val entry = idx[fileId] ?: return@withContext null

            if (entry.modifiedTime != modifiedTime) {
                vaultFile.delete()
                idx.remove(fileId)
                saveIndex(dir, idx)
                return@withContext null
            }

            idx[fileId] = entry.copy(lastAccessedMs = System.currentTimeMillis())
            saveIndex(dir, idx)
            vaultFile.readBytes()
        }
    }

    /**
     * Writes [encryptedBytes] to the cache.
     * Requires [dek] only to write the per-file encrypted metadata sidecar.
     */
    suspend fun put(
        context: Context,
        fileId: String,
        modifiedTime: String,
        encryptedBytes: ByteArray,
        dek: ByteArray,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        isPinned: Boolean = false
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = dir(context)
            File(dir, "$fileId.vault").writeBytes(encryptedBytes)
            writeMeta(dir, fileId, modifiedTime, encryptedBytes.size.toLong(), dek)

            val idx = loadIndex(dir)
            idx[fileId] = IndexEntry(fileId, encryptedBytes.size.toLong(), modifiedTime,
                System.currentTimeMillis(), isPinned)
            saveIndex(dir, idx)
            evictIfNeeded(dir, idx, maxBytes)
        }
    }

    /**
     * Zero-copy promotion: renames [stagingFile] into the cache directory.
     * Falls back to copy+delete if on a different filesystem.
     */
    suspend fun promoteFromStaging(
        context: Context,
        fileId: String,
        modifiedTime: String,
        stagingFile: File,
        dek: ByteArray,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        isPinned: Boolean = false
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = dir(context)
            val dest = File(dir, "$fileId.vault")
            val moved = stagingFile.renameTo(dest)
            if (!moved) {
                stagingFile.copyTo(dest, overwrite = true)
                stagingFile.delete()
            }
            val encryptedSize = dest.length()
            writeMeta(dir, fileId, modifiedTime, encryptedSize, dek)

            val idx = loadIndex(dir)
            idx[fileId] = IndexEntry(fileId, encryptedSize, modifiedTime,
                System.currentTimeMillis(), isPinned)
            saveIndex(dir, idx)
            evictIfNeeded(dir, idx, maxBytes)
        }
    }

    suspend fun isPinned(context: Context, fileId: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) { loadIndex(dir(context))[fileId]?.isPinned ?: false }
    }

    suspend fun pinnedFileIds(context: Context): Set<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadIndex(dir(context)).values.filter { it.isPinned }.map { it.fileId }.toSet()
        }
    }

    suspend fun setPinned(context: Context, fileId: String, pinned: Boolean) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = dir(context)
            val idx = loadIndex(dir)
            idx[fileId]?.let { idx[fileId] = it.copy(isPinned = pinned) }
            saveIndex(dir, idx)
        }
    }

    /** Deletes all cached vault files and clears the index. */
    suspend fun clear(context: Context) = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir(context).listFiles()?.forEach { it.delete() }
            index = null
        }
    }

    /** Bytes currently used on disk by the cache. */
    suspend fun usedBytes(context: Context): Long = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadIndex(dir(context)).values.sumOf { it.encryptedSize }
        }
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun dir(context: Context) =
        File(context.applicationContext.filesDir, VAULT_DIR).also { it.mkdirs() }

    private fun writeMeta(dir: File, fileId: String, modifiedTime: String, size: Long, dek: ByteArray) {
        val json = """{"fileId":"$fileId","modifiedTime":"$modifiedTime","size":$size}"""
        val enc = VaultKeyManager.encryptWithDek(json.toByteArray(Charsets.UTF_8), dek)
        File(dir, "$fileId.meta").writeBytes(enc)
    }

    private fun loadIndex(dir: File): MutableMap<String, IndexEntry> {
        index?.let { return it }
        val file = File(dir, INDEX_FILE)
        if (!file.exists()) return mutableMapOf<String, IndexEntry>().also { index = it }
        return try {
            val json = file.readText()
            val arr = com.google.gson.JsonParser.parseString(json).asJsonArray
            val result = mutableMapOf<String, IndexEntry>()
            for (el in arr) {
                val o = el.asJsonObject
                val entry = IndexEntry(
                    fileId        = o.get("fileId").asString,
                    encryptedSize = o.get("size").asLong,
                    modifiedTime  = o.get("mt")?.asString ?: "",
                    lastAccessedMs = o.get("la").asLong,
                    isPinned      = o.get("pin")?.asBoolean ?: false
                )
                result[entry.fileId] = entry
            }
            result.also { index = it }
        } catch (_: Exception) {
            mutableMapOf<String, IndexEntry>().also { index = it }
        }
    }

    private fun saveIndex(dir: File, idx: Map<String, IndexEntry>) {
        val sb = StringBuilder("[")
        idx.values.forEachIndexed { i, e ->
            if (i > 0) sb.append(',')
            sb.append("""{"fileId":"${e.fileId}","size":${e.encryptedSize},"mt":"${e.modifiedTime}","la":${e.lastAccessedMs},"pin":${e.isPinned}}""")
        }
        sb.append(']')
        File(dir, INDEX_FILE).writeText(sb.toString())
        index = idx.toMutableMap()
    }

    private fun evictIfNeeded(dir: File, idx: MutableMap<String, IndexEntry>, maxBytes: Long) {
        var total = idx.values.sumOf { it.encryptedSize }
        if (total <= maxBytes) return
        // LRU eviction: oldest unpinned entries first
        val candidates = idx.values.filter { !it.isPinned }.sortedBy { it.lastAccessedMs }
        for (entry in candidates) {
            if (total <= maxBytes) break
            File(dir, "${entry.fileId}.vault").delete()
            File(dir, "${entry.fileId}.meta").delete()
            total -= entry.encryptedSize
            idx.remove(entry.fileId)
        }
        saveIndex(dir, idx)
    }
}
