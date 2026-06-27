package com.darkvault.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.darkvault.app.MainActivity
import com.darkvault.app.VaultSession
import com.darkvault.app.cache.LocalVaultCache
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.crypto.VaultKeyManager
import com.darkvault.app.drive.DriveApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class UploadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID        = "darkvault_uploads"
        const val NOTIF_ID          = 1001
        const val ACTION_CANCEL_ALL = "com.darkvault.app.CANCEL_ALL_UPLOADS"
        const val ACTION_CANCEL_JOB = "com.darkvault.app.CANCEL_JOB"
        const val ACTION_PAUSE_ALL  = "com.darkvault.app.PAUSE_ALL_UPLOADS"
        const val ACTION_RESUME_ALL = "com.darkvault.app.RESUME_ALL_UPLOADS"
        const val EXTRA_JOB_ID      = "job_id"

        // ponytail: max thumbnail dimension for upload companions
        private const val THUMB_MAX_PX = 400
        private const val THUMB_QUALITY = 60

        // ponytail: 3 encrypt workers (CPU-bound) and 2 upload workers (IO-bound)
        private const val ENCRYPT_WORKERS = 3
        private const val UPLOAD_WORKERS  = 2
    }

    // Thrown from the onProgress callback to abort uploadChunkedFromFile cleanly.
    private class UploadCancelledException : Exception("Upload cancelled by user")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_ALL -> {
                UploadState.queue.forEach { UploadState.cancelledIds.add(it.id) }
                UploadState.active.value?.let { active ->
                    UploadState.cancelledIds.add(active.jobId)
                    UploadState.resumeSignals[active.jobId]?.trySend(Unit)
                }
                UploadState.pausedIds.toList().forEach { id ->
                    UploadState.cancelledIds.add(id)
                    UploadState.resumeSignals[id]?.trySend(Unit)
                }
            }
            ACTION_CANCEL_JOB -> {
                intent.getStringExtra(EXTRA_JOB_ID)?.let { id ->
                    UploadState.cancelledIds.add(id)
                    UploadState.resumeSignals[id]?.trySend(Unit)
                }
            }
            ACTION_PAUSE_ALL -> {
                UploadState.active.value?.let { upload ->
                    if (!UploadState.pausedIds.contains(upload.jobId)) {
                        UploadState.pausedIds.add(upload.jobId)
                        if (!UploadState.resumeSignals.containsKey(upload.jobId)) {
                            UploadState.resumeSignals[upload.jobId] = Channel(capacity = 1)
                        }
                        UploadState.pausedCount.value = UploadState.pausedIds.size
                        UploadState.active.value = upload.copy(isPaused = true)
                        updateNotificationPaused()
                    }
                }
            }
            ACTION_RESUME_ALL -> {
                UploadState.pausedIds.toList().forEach { id ->
                    UploadState.pausedIds.remove(id)
                    UploadState.resumeSignals[id]?.trySend(Unit)
                }
                UploadState.pausedCount.value = 0
                UploadState.active.value = UploadState.active.value?.copy(isPaused = false)
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification("Preparing…", 0, 0, false))
                processQueue()
            }
        }
        return START_NOT_STICKY
    }

    // ── Queue processing ────────────────────────────────────────────────────

    private fun processQueue() {
        scope.launch {
            val dek      = VaultSession.dek
            val password = VaultSession.masterPassword
            val account  = VaultSession.signedInAccount
            if (password == null || account == null) { finish(); return@launch }

            val stagingDir = File(cacheDir, "encrypt_staging").also { it.mkdirs() }
            val client     = DriveApiClient(this@UploadForegroundService, account)
            // Serializes conflict-resolution dialogs so only one shows at a time
            val conflictMutex = Mutex()

            // Channel connecting encryption workers → upload workers
            // ponytail: capacity=8 lets encryption stay ahead of upload without unbounded buffering
            val readyChannel = Channel<ReadyToUploadJob>(capacity = 8)

            val totalJobs = UploadState.queue.size
            UploadState.totalInQueue.value  = totalJobs
            UploadState.completedInQueue.value = 0
            val completedCount = AtomicInteger(0)
            val failedCount    = AtomicInteger(0)

            // ── Encryption workers (CPU-bound) ──────────────────────────────
            val encryptWorkers = (1..ENCRYPT_WORKERS).map {
                launch(Dispatchers.Default) {
                    while (true) {
                        val job = UploadState.queue.poll() ?: break
                        UploadState.queueSize.value = UploadState.queue.size

                        if (job.id in UploadState.cancelledIds) {
                            UploadState.cancelledIds.remove(job.id)
                            UploadState.events.emit(UploadEvent.Cancelled(job.id, job.originalName))
                            continue
                        }

                        // Idempotency check — skip if already uploaded
                        val alreadyDone = withContext(Dispatchers.IO) {
                            client.fileExistsByClientId(job.id, job.folderId)
                        }
                        if (alreadyDone) {
                            UploadState.events.emit(UploadEvent.Completed(job.id, job.originalName))
                            completedCount.incrementAndGet()
                            UploadState.completedInQueue.value = completedCount.get()
                            continue
                        }

                        // Conflict detection (serialized so only one dialog shows at a time)
                        val uploadName = withContext(Dispatchers.IO) {
                            resolveConflict(client, job, conflictMutex)
                        } ?: continue // null → user chose Skip

                        // Encrypt main file to staging
                        UploadState.active.value = ActiveUpload(
                            job.id, uploadName, 0, job.fileSize, "Encrypting…",
                            currentIndex = completedCount.get() + 1, totalInBatch = totalJobs
                        )
                        UploadState.events.emit(UploadEvent.Encrypting(job.id, uploadName))
                        updateNotification("Encrypting $uploadName…", 0, 0)

                        if (job.id in UploadState.cancelledIds) {
                            UploadState.cancelledIds.remove(job.id)
                            UploadState.events.emit(UploadEvent.Cancelled(job.id, job.originalName))
                            continue
                        }

                        val mainStaging = File(stagingDir, "${job.id}.vault")
                        try {
                            encryptToFile(job, mainStaging, dek, password)
                        } catch (e: Exception) {
                            mainStaging.delete()
                            failedCount.incrementAndGet()
                            UploadState.events.emit(
                                UploadEvent.Failed(job.id, job.originalName, e.message ?: "Encryption failed")
                            )
                            continue
                        }

                        // Generate + encrypt thumbnail for images (best-effort)
                        val thumbStaging: File? = if (isImageMime(job.mimeType)) {
                            val f = File(stagingDir, "${job.id}_thumb.vault")
                            val ok = generateEncryptedThumbnail(job, f, dek)
                            if (ok) f else null
                        } else null

                        readyChannel.send(
                            ReadyToUploadJob(
                                id           = job.id,
                                mainFile     = mainStaging,
                                thumbFile    = thumbStaging,
                                originalName = job.originalName,
                                uploadName   = uploadName,
                                mimeType     = job.mimeType,
                                originalSize = job.fileSize,
                                folderId     = job.folderId
                            )
                        )
                    }
                }
            }

            // ── Upload workers (IO-bound) ────────────────────────────────────
            val uploadWorkers = (1..UPLOAD_WORKERS).map {
                launch(Dispatchers.IO) {
                    for (ready in readyChannel) {
                        UploadState.resumeSignals[ready.id] = Channel(capacity = 1)
                        try {
                            if (com.darkvault.app.BuildConfig.DEBUG &&
                                com.darkvault.app.debug.DeveloperOptionsManager.simulateUploadFailure.value) {
                                com.darkvault.app.debug.DeveloperOptionsManager.simulateUploadFailure.value = false
                                throw Exception("Simulated upload failure (debug injection)")
                            }

                            val didRename  = ready.uploadName != ready.originalName
                            val driveFileId = uploadFromFile(client, ready, dek, completedCount, totalJobs)

                            // Promote staging file to local vault cache after a successful upload
                            // (zero-copy rename: the staging file IS the cache entry)
                            dek?.let { activeDek ->
                                LocalVaultCache.promoteFromStaging(
                                    context      = this@UploadForegroundService,
                                    fileId       = driveFileId,
                                    modifiedTime = "",   // not yet known; will be populated on next listItems
                                    stagingFile  = ready.mainFile,
                                    dek          = activeDek
                                )
                            } ?: ready.mainFile.delete()

                            if (didRename)
                                UploadState.events.emit(UploadEvent.Renamed(ready.id, ready.originalName, ready.uploadName))
                            UploadState.events.emit(UploadEvent.Completed(ready.id, ready.uploadName))
                            completedCount.incrementAndGet()
                            UploadState.completedInQueue.value = completedCount.get()

                        } catch (e: UploadCancelledException) {
                            UploadState.active.value = null
                            UploadState.cancelledIds.remove(ready.id)
                            ready.mainFile.delete()
                            ready.thumbFile?.delete()
                            UploadState.events.emit(UploadEvent.Cancelled(ready.id, ready.originalName))
                        } catch (e: Exception) {
                            UploadState.active.value = null
                            ready.mainFile.delete()
                            ready.thumbFile?.delete()
                            failedCount.incrementAndGet()
                            UploadState.events.emit(
                                UploadEvent.Failed(ready.id, ready.originalName, e.message ?: "Upload failed")
                            )
                        } finally {
                            UploadState.resumeSignals.remove(ready.id)
                            UploadState.pausedIds.remove(ready.id)
                        }
                    }
                }
            }

            // Wait for all encryption workers to drain the input queue, then close the channel
            encryptWorkers.joinAll()
            readyChannel.close()
            // Wait for upload workers to finish what's in the channel
            uploadWorkers.joinAll()

            finish()
        }
    }

    // ── Encrypt to staging file ─────────────────────────────────────────────

    private fun encryptToFile(job: UploadJob, dest: File, dek: ByteArray?, password: String) {
        val input = contentResolver.openInputStream(job.uri)
            ?: error("Cannot open URI for ${job.originalName}")
        FileOutputStream(dest).use { out ->
            if (dek != null) {
                CryptoManager.encryptWithDek(input, out, dek)
            } else {
                CryptoManager.encrypt(input, out, password)
            }
        }
        input.close()
    }

    // ── Thumbnail generation ────────────────────────────────────────────────

    private fun generateEncryptedThumbnail(job: UploadJob, dest: File, dek: ByteArray?): Boolean {
        if (dek == null) return false
        return try {
            // Two-pass decode: first get dimensions, then sample-decode
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(job.uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return false

            val sample = calcSampleSize(opts.outWidth, opts.outHeight, THUMB_MAX_PX, THUMB_MAX_PX)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(job.uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return false

            val jpegBytes = ByteArrayOutputStream().use { baos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, baos)
                bmp.recycle()
                baos.toByteArray()
            }

            // Encrypt JPEG bytes with DEK
            val encBytes = try {
                VaultKeyManager.encryptWithDek(jpegBytes, dek)
            } finally {
                Arrays.fill(jpegBytes, 0)
            }

            dest.writeBytes(encBytes)
            true
        } catch (_: Exception) {
            dest.delete()
            false
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

    // ── Upload from staging file ────────────────────────────────────────────

    private suspend fun uploadFromFile(
        client: DriveApiClient,
        ready: ReadyToUploadJob,
        dek: ByteArray?,
        completedCount: AtomicInteger,
        totalJobs: Int
    ): String {
        val encryptedSize = ready.mainFile.length()

        // Upload thumbnail first so we can embed its Drive ID in the main file metadata
        val thumbDriveId: String? = ready.thumbFile?.let { thumbFile ->
            try {
                val thumbSession = client.startThumbnailSession(ready.id, ready.folderId, thumbFile.length())
                client.uploadChunkedFromFile(thumbSession, thumbFile) { _, _ -> }
            } catch (_: Exception) {
                null
            } finally {
                thumbFile.delete()
            }
        }

        val sessionUri = client.startResumableSession(
            fileName      = "${ready.uploadName}.vault",
            originalName  = ready.uploadName,
            originalMimeType = ready.mimeType,
            folderId      = ready.folderId,
            totalBytes    = encryptedSize,
            clientId      = ready.id,
            thumbnailId   = thumbDriveId ?: ""
        )

        UploadState.active.value = ActiveUpload(
            ready.id, ready.uploadName, 0, encryptedSize, "Uploading…",
            currentIndex = completedCount.get() + 1, totalInBatch = totalJobs
        )
        UploadState.events.emit(UploadEvent.Uploading(ready.id, ready.uploadName, 0, encryptedSize))

        return client.uploadChunkedFromFile(sessionUri, ready.mainFile) { uploaded, total ->
            if (ready.id in UploadState.cancelledIds) throw UploadCancelledException()

            if (ready.id in UploadState.pausedIds) {
                UploadState.active.value = ActiveUpload(
                    ready.id, ready.uploadName, uploaded, total, "Paused",
                    isPaused = true,
                    currentIndex = completedCount.get() + 1, totalInBatch = totalJobs
                )
                updateNotificationPaused()
                UploadState.resumeSignals[ready.id]?.receive()
                if (ready.id in UploadState.cancelledIds) throw UploadCancelledException()
            }

            UploadState.active.value = ActiveUpload(
                ready.id, ready.uploadName, uploaded, total, "Uploading…",
                currentIndex = completedCount.get() + 1, totalInBatch = totalJobs
            )
            UploadState.events.emit(UploadEvent.Uploading(ready.id, ready.uploadName, uploaded, total))
            updateNotification("Uploading ${ready.uploadName}", uploaded, total)
        }.also {
            UploadState.active.value = null
        }
    }

    // ── Conflict resolution ─────────────────────────────────────────────────

    /**
     * Returns the resolved upload name, or null if the user chose Skip.
     * Uses [conflictMutex] to ensure only one dialog is shown at a time.
     */
    private suspend fun resolveConflict(
        client: DriveApiClient,
        job: UploadJob,
        conflictMutex: Mutex
    ): String? {
        val existingName = client.findExistingOriginalName(job.originalName, job.folderId)
            ?: return job.originalName // no conflict

        return conflictMutex.withLock {
            val suggestedName = client.findUniqueOriginalName(job.originalName, job.folderId)
            UploadState.events.emit(
                UploadEvent.ConflictDetected(
                    jobId         = job.id,
                    originalName  = job.originalName,
                    suggestedName = suggestedName,
                    conflictIndex = 1,
                    totalConflicts = 1
                )
            )
            val resolution = try {
                UploadState.conflictChannel.receive()
            } catch (_: Exception) {
                ConflictResolution.Skip
            }
            when (resolution) {
                is ConflictResolution.Skip    -> { UploadState.events.emit(UploadEvent.Skipped(job.id, job.originalName)); null }
                is ConflictResolution.Rename  -> suggestedName
                is ConflictResolution.RenameAs -> resolution.newName.ifBlank { suggestedName }
                is ConflictResolution.Replace -> {
                    try {
                        val existing = client.findFileIdByOriginalName(job.originalName, job.folderId)
                        if (existing != null) client.trashFile(existing)
                    } catch (_: Exception) {}
                    job.originalName
                }
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun isImageMime(mime: String) =
        mime.startsWith("image/") || mime == "image/heic" || mime == "image/heif" || mime == "image/avif"

    private fun finish() {
        UploadState.active.value = null
        UploadState.queueSize.value = 0
        UploadState.totalInQueue.value = 0
        UploadState.completedInQueue.value = 0
        UploadState.pausedCount.value = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notification ────────────────────────────────────────────────────────

    private fun buildNotification(text: String, uploaded: Long, total: Long, paused: Boolean = false): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, UploadForegroundService::class.java).apply { action = ACTION_CANCEL_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseIntent = PendingIntent.getService(
            this, 2,
            Intent(this, UploadForegroundService::class.java).apply { action = ACTION_PAUSE_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val resumeIntent = PendingIntent.getService(
            this, 3,
            Intent(this, UploadForegroundService::class.java).apply { action = ACTION_RESUME_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notifText = if (text.length > 60) text.take(57) + "…" else text
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("darkVault Upload")
            .setContentText(notifText)
            .setOngoing(true)
            .setContentIntent(openIntent)

        if (paused) builder.addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
        else        builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
        builder.addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)

        if (total > 0) {
            val pct = ((uploaded.toFloat() / total) * 100).toInt()
            builder.setProgress(100, pct, false).setSubText("$pct%")
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun updateNotification(text: String, uploaded: Long, total: Long) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text, uploaded, total, false))
    }

    private fun updateNotificationPaused() {
        val upload = UploadState.active.value
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(
                "${UploadState.pausedIds.size} upload(s) paused",
                upload?.uploaded ?: 0L,
                upload?.total    ?: 0L,
                true
            ))
    }

    private fun ensureChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Upload Progress", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Shows darkVault upload progress"; setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
