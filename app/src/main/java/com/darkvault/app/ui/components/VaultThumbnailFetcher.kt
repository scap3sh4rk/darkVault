package com.darkvault.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.darkvault.app.VaultSession
import com.darkvault.app.cache.EncryptedFileCache
import com.darkvault.app.cache.LocalVaultCache
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.crypto.VaultKeyManager
import com.darkvault.app.drive.DriveApiClient
import com.darkvault.app.model.VaultFile
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import java.util.UUID

data class VaultThumbnailRequest(
    val file: VaultFile,
    val password: String,
    val account: com.google.android.gms.auth.api.signin.GoogleSignInAccount
)

/**
 * Custom Coil Fetcher that downloads, decrypts, and renders thumbnails for vault files.
 *
 * Cache hierarchy (fastest → slowest):
 *  1. Coil memory cache (decoded bitmap, 10% of heap) — managed by Coil
 *  2. EncryptedFileCache (encrypted bytes in RAM, 64 MB) — cleared on vault lock
 *  3. LocalVaultCache (encrypted bytes on disk) — persistent, cleared on sign-out
 *  4. Drive download — last resort
 *
 * SECURITY: No plaintext bytes ever reach disk. Disk cache stores only ciphertext.
 *           Coil disk cache is DISABLED (VaultImageLoader sets diskCachePolicy=DISABLED).
 *           Temp files for video/PDF extraction are in app-private cacheDir and deleted
 *           immediately after the thumbnail bitmap is extracted.
 */
class VaultThumbnailFetcher(
    private val data: VaultThumbnailRequest,
    private val context: Context
) : Fetcher {

    companion object {
        // For images: no hard size cap — sample-decode large images instead of skipping them
        private const val MAX_SIZE_VIDEO = 20L * 1024 * 1024
        private const val MAX_SIZE_PDF   =  5L * 1024 * 1024
        private const val THUMB_MAX_PX   = 400
        private const val PDF_THUMB_WIDTH = 300
    }

    override suspend fun fetch(): FetchResult? {
        val file = data.file
        val mime = file.originalMimeType
        return when {
            mime.startsWith("image/") -> fetchImage(file)
            mime.startsWith("video/") -> fetchVideoFrame(file)
            mime == "application/pdf" -> fetchPdfPage(file)
            else -> null
        }
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    private suspend fun fetchImage(file: VaultFile): FetchResult? {
        // If the file has a dedicated thumbnail companion on Drive, prefer it:
        // it's tiny (10–30 KB) and already scaled. The thumbnail is stored as a raw
        // AES-GCM payload (no version byte), decryptable with VaultKeyManager.decryptWithDek.
        val dek = VaultSession.dek
        if (file.thumbnailFileId != null && dek != null) {
            fetchThumbnailCompanion(file.thumbnailFileId, dek)?.let { return it }
        }

        // Full-file path: download → decrypt → sample-decode to thumbnail size in RAM
        val encBytes = fetchEncryptedBytes(file) ?: return null
        return try {
            val decrypted = decrypt(encBytes, file) ?: return null
            try {
                sampleDecodeImage(decrypted, file.originalMimeType)
            } finally {
                Arrays.fill(decrypted, 0)
            }
        } finally {
            Arrays.fill(encBytes, 0)
        }
    }

    /** Downloads and decodes the pre-generated thumbnail companion file. */
    private suspend fun fetchThumbnailCompanion(thumbFileId: String, dek: ByteArray): FetchResult? {
        // Check memory cache first (keyed by thumbFileId, no modifiedTime needed for thumbnails)
        val memCached = EncryptedFileCache.get(thumbFileId, "thumb")
        val encThumb = if (memCached != null) memCached else {
            try {
                val bytes = DriveApiClient(context, data.account).downloadFile(thumbFileId)
                EncryptedFileCache.put(thumbFileId, "thumb", bytes)
                bytes
            } catch (_: Exception) { return null }
        }

        return try {
            // Thumbnails are encrypted with VaultKeyManager.encryptWithDek (no version byte)
            val jpegBytes = VaultKeyManager.decryptWithDek(encThumb, dek)
            val buffer = Buffer()
            buffer.write(jpegBytes)
            Arrays.fill(jpegBytes, 0)
            SourceResult(
                source    = ImageSource(buffer, context),
                mimeType  = "image/jpeg",
                dataSource = DataSource.NETWORK
            )
        } catch (_: Exception) {
            null
        } finally {
            Arrays.fill(encThumb, 0)
        }
    }

    /** Sample-decodes [decrypted] to at most [THUMB_MAX_PX] × [THUMB_MAX_PX]. */
    private fun sampleDecodeImage(decrypted: ByteArray, mimeType: String): FetchResult? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        val sample = calcSampleSize(opts.outWidth, opts.outHeight, THUMB_MAX_PX, THUMB_MAX_PX)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size, decodeOpts)
            ?: return null

        val buffer = Buffer()
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        bmp.recycle()
        val jpeg = baos.toByteArray()
        buffer.write(jpeg)
        return SourceResult(
            source    = ImageSource(buffer, context),
            mimeType  = mimeType,
            dataSource = DataSource.NETWORK
        )
    }

    // ── Video (first frame) ───────────────────────────────────────────────────

    private suspend fun fetchVideoFrame(file: VaultFile): FetchResult? {
        if (file.size > MAX_SIZE_VIDEO) return null
        val encBytes  = fetchEncryptedBytes(file) ?: return null
        val decrypted = try {
            decrypt(encBytes, file) ?: return null
        } finally {
            Arrays.fill(encBytes, 0)
        }

        val tmp = File(context.cacheDir, "thumb_${UUID.randomUUID()}.tmp")
        return try {
            FileOutputStream(tmp).use { it.write(decrypted) }
            Arrays.fill(decrypted, 0)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tmp.absolutePath)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return null
                DrawableResult(
                    drawable   = BitmapDrawable(context.resources, frame),
                    isSampled  = false,
                    dataSource = DataSource.NETWORK
                )
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    // ── PDF (first page) ──────────────────────────────────────────────────────

    private suspend fun fetchPdfPage(file: VaultFile): FetchResult? {
        if (file.size > MAX_SIZE_PDF) return null
        val encBytes  = fetchEncryptedBytes(file) ?: return null
        val decrypted = try {
            decrypt(encBytes, file) ?: return null
        } finally {
            Arrays.fill(encBytes, 0)
        }

        val tmp = File(context.cacheDir, "thumb_${UUID.randomUUID()}.tmp")
        return try {
            FileOutputStream(tmp).use { it.write(decrypted) }
            Arrays.fill(decrypted, 0)
            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val page     = renderer.openPage(0)
                val scale    = PDF_THUMB_WIDTH.toFloat() / page.width
                val bmp      = Bitmap.createBitmap(
                    PDF_THUMB_WIDTH,
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close(); renderer.close()
                DrawableResult(
                    drawable   = BitmapDrawable(context.resources, bmp),
                    isSampled  = false,
                    dataSource = DataSource.NETWORK
                )
            } finally {
                pfd.close()
            }
        } catch (_: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Returns encrypted bytes for [file], checking caches before hitting Drive.
     * Memory cache → disk cache → Drive download.
     */
    private suspend fun fetchEncryptedBytes(file: VaultFile): ByteArray? {
        // 1. Memory cache
        EncryptedFileCache.get(file.id, file.modifiedTime)?.let { return it }

        // 2. Disk cache
        LocalVaultCache.getEncryptedBytes(context, file.id, file.modifiedTime)?.let { cached ->
            EncryptedFileCache.put(file.id, file.modifiedTime, cached) // warm memory cache
            return cached
        }

        // 3. Drive download
        return try {
            val bytes = DriveApiClient(context, data.account).downloadFile(file.id)
            EncryptedFileCache.put(file.id, file.modifiedTime, bytes)
            bytes
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(encBytes: ByteArray, file: VaultFile): ByteArray? {
        val dek = VaultSession.dek
        return try {
            val out = ByteArrayOutputStream()
            CryptoManager.decrypt(ByteArrayInputStream(encBytes), out, data.password, dek)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun calcSampleSize(w: Int, h: Int, maxW: Int, maxH: Int): Int {
        var s = 1
        if (h > maxH || w > maxW) {
            val hh = h / 2; val hw = w / 2
            while (hh / s >= maxH && hw / s >= maxW) s *= 2
        }
        return s
    }
}

class VaultThumbnailFetcherFactory(private val context: Context) :
    Fetcher.Factory<VaultThumbnailRequest> {
    override fun create(data: VaultThumbnailRequest, options: Options, imageLoader: ImageLoader): Fetcher =
        VaultThumbnailFetcher(data, context)
}
