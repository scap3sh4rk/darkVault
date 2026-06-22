package com.darkvault.app.ui.components

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

/**
 * Returns a Coil [ImageLoader] configured for vault thumbnails:
 *  - Disk cache: DISABLED (no plaintext ever written to disk)
 *  - Memory cache: enabled (limited to 20 MB) — images stay in RAM only
 *  - Custom fetcher factory registered for [VaultThumbnailRequest]
 */
fun buildVaultImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .diskCache(null)               // CRITICAL: no disk cache
        .diskCachePolicy(CachePolicy.DISABLED)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.10)  // up to 10% of available heap, ~20–40 MB on most devices
                .build()
        }
        .components {
            add(VaultThumbnailFetcherFactory(context))
        }
        .build()
}
