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
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.drive.DriveApiClient
import com.darkvault.app.model.VaultFile
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import java.util.UUID

/**
 * Data class used as the Coil model for vault thumbnails.
 */
data class VaultThumbnailRequest(
    val file: VaultFile,
    val password: String,
    val account: com.google.android.gms.auth.api.signin.GoogleSignInAccount
)

/**
 * Custom Coil Fetcher that downloads, decrypts, and generates thumbnails for vault files.
 *
 * Supported types: images, videos (first frame), PDFs (first page).
 *
 * SECURITY: Disk cache DISABLED. Images decoded entirely in RAM.
 *           Video/PDF require a private temp file (in app's cacheDir) which is deleted
 *           immediately after the thumbnail bitmap is extracted. Same pattern as PreviewDialogs.
 */
class VaultThumbnailFetcher(
    private val data: VaultThumbnailRequest,
    private val context: Context
) : Fetcher {

    companion object {
        private const val MAX_SIZE_IMAGE = 2L * 1024 * 1024   // 2 MB — pure in-memory decode
        private const val MAX_SIZE_VIDEO = 20L * 1024 * 1024  // 20 MB — only first frame needed
        private const val MAX_SIZE_PDF   = 5L * 1024 * 1024   // 5 MB  — only first page needed
        private const val PDF_THUMB_WIDTH = 300                // px — small render for thumbnail
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
        if (file.size > MAX_SIZE_IMAGE) return null
        val decryptedBytes = decryptFile(file) ?: return null

        val opts = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            Arrays.fill(decryptedBytes, 0)
            return null
        }

        val buffer = Buffer()
        buffer.write(decryptedBytes)
        Arrays.fill(decryptedBytes, 0)

        return SourceResult(
            source = ImageSource(buffer, context),
            mimeType = file.originalMimeType,
            dataSource = DataSource.NETWORK
        )
    }

    // ── Video (first frame) ───────────────────────────────────────────────────

    private suspend fun fetchVideoFrame(file: VaultFile): FetchResult? {
        if (file.size > MAX_SIZE_VIDEO) return null
        val decryptedBytes = decryptFile(file) ?: return null

        val tmp = File(context.cacheDir, "thumb_${UUID.randomUUID()}.tmp")
        return try {
            FileOutputStream(tmp).use { it.write(decryptedBytes) }
            Arrays.fill(decryptedBytes, 0)

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tmp.absolutePath)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return null
                DrawableResult(
                    drawable = BitmapDrawable(context.resources, frame),
                    isSampled = false,
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
        val decryptedBytes = decryptFile(file) ?: return null

        val tmp = File(context.cacheDir, "thumb_${UUID.randomUUID()}.tmp")
        return try {
            FileOutputStream(tmp).use { it.write(decryptedBytes) }
            Arrays.fill(decryptedBytes, 0)

            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val page = renderer.openPage(0)
                val scale = PDF_THUMB_WIDTH.toFloat() / page.width
                val bmp = Bitmap.createBitmap(
                    PDF_THUMB_WIDTH,
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                DrawableResult(
                    drawable = BitmapDrawable(context.resources, bmp),
                    isSampled = false,
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

    // ── Shared decrypt ────────────────────────────────────────────────────────

    private suspend fun decryptFile(file: VaultFile): ByteArray? {
        val dek = VaultSession.dek
        val encBytes = try {
            DriveApiClient(context, data.account).downloadFile(file.id)
        } catch (_: Exception) {
            return null
        }
        return try {
            val out = ByteArrayOutputStream()
            CryptoManager.decrypt(ByteArrayInputStream(encBytes), out, data.password, dek)
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            Arrays.fill(encBytes, 0)
        }
    }
}

/**
 * Coil FetcherFactory for vault thumbnails.
 */
class VaultThumbnailFetcherFactory(private val context: Context) :
    Fetcher.Factory<VaultThumbnailRequest> {

    override fun create(
        data: VaultThumbnailRequest,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher = VaultThumbnailFetcher(data, context)
}
