package com.darkvault.app.cache

import java.util.Arrays
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// ponytail: session-scoped LRU cache of encrypted Drive file bytes
// Keyed by (fileId, modifiedTime). Cleared with the DEK on vault lock.
// Ciphertext-only — identical security posture to the Drive copy.
object EncryptedFileCache {

    private const val MAX_BYTES = 64L * 1024 * 1024 // 64 MB

    private data class Entry(val data: ByteArray, val modifiedTime: String)

    private val lock = ReentrantLock()
    private var totalBytes = 0L

    // accessOrder=true → get() touches entry, making it MRU
    private val lru = object : java.util.LinkedHashMap<String, Entry>(32, 0.75f, true) {
        // Manual eviction below; no elder removal here
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = false
    }

    /** Returns a defensive copy, or null on miss / stale modifiedTime. */
    fun get(fileId: String, modifiedTime: String): ByteArray? = lock.withLock {
        val e = lru[fileId] ?: return@withLock null
        if (e.modifiedTime != modifiedTime) return@withLock null
        e.data.copyOf()
    }

    /** Stores a defensive copy. Evicts LRU entries if over the cap. */
    fun put(fileId: String, modifiedTime: String, data: ByteArray) = lock.withLock {
        val old = lru.put(fileId, Entry(data.copyOf(), modifiedTime))
        totalBytes += data.size - (old?.data?.size ?: 0)
        old?.data?.let { Arrays.fill(it, 0) }
        evict()
    }

    /** Zeros all cached arrays and resets state. Called from VaultSession.clearDek(). */
    fun clear() = lock.withLock {
        lru.values.forEach { Arrays.fill(it.data, 0) }
        lru.clear()
        totalBytes = 0L
    }

    private fun evict() {
        val iter = lru.entries.iterator()
        while (totalBytes > MAX_BYTES && iter.hasNext()) {
            val (_, e) = iter.next()
            totalBytes -= e.data.size
            Arrays.fill(e.data, 0)
            iter.remove()
        }
    }
}
