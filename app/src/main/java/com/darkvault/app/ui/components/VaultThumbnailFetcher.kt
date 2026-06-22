package com.darkvault.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.darkvault.app.VaultSession
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.drive.DriveApiClient
import com.darkvault.app.model.VaultFile
import okio.Buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Arrays

/**
 * Data class used as the Coil model for vault thumbnails.
 * Carries everything the fetcher needs: the file to fetch and credentials.
 */
data class VaultThumbnailRequest(
    val file: VaultFile,
    val password: String,
    val account: com.google.android.gms.auth.api.signin.GoogleSignInAccount
)

/**
 * Custom Coil Fetcher that downloads an encrypted vault file from Google Drive,
 * decrypts it entirely in memory, and returns the plaintext bytes to Coil.
 *
 * SECURITY: Disk cache is DISABLED (enforced in VaultThumbnailFetcherFactory).
 *           No plaintext bytes are ever written to disk.
 *           Files larger than MAX_ENCRYPTED_SIZE are skipped (null returned).
 */
class VaultThumbnailFetcher(
    private val data: VaultThumbnailRequest,
    private val context: Context
) : Fetcher {

    companion object {
        /** Maximum encrypted file size eligible for thumbnail (2 MB). */
        private const val MAX_ENCRYPTED_SIZE = 2L * 1024 * 1024
    }

    override suspend fun fetch(): FetchResult? {
        val file = data.file
        // Only fetch thumbnails for image files
        if (!file.originalMimeType.startsWith("image/")) return null
        // Skip large files to prevent OOM
        if (file.size > MAX_ENCRYPTED_SIZE) return null

        val dek = VaultSession.dek
        val password = data.password

        val client = DriveApiClient(context, data.account)
        val encBytes = try {
            client.downloadFile(file.id)
        } catch (_: Exception) {
            return null
        }

        val decryptedBytes: ByteArray = try {
            val out = ByteArrayOutputStream()
            CryptoManager.decrypt(ByteArrayInputStream(encBytes), out, password, dek)
            out.toByteArray()
        } catch (_: Exception) {
            return null
        } finally {
            Arrays.fill(encBytes, 0)
        }

        // Validate it's actually a decodable bitmap before handing to Coil
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
}

/**
 * Coil FetcherFactory for vault thumbnails.
 * Registers this fetcher for [VaultThumbnailRequest] data types.
 * Disk cache is disabled at the ImageLoader level (see VaultImageLoader).
 */
class VaultThumbnailFetcherFactory(private val context: Context) :
    Fetcher.Factory<VaultThumbnailRequest> {

    override fun create(
        data: VaultThumbnailRequest,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher = VaultThumbnailFetcher(data, context)
}
