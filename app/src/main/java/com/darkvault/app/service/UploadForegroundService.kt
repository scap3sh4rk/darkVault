package com.darkvault.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.darkvault.app.MainActivity
import com.darkvault.app.VaultSession
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.drive.DriveApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class UploadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "darkvault_uploads"
        const val NOTIF_ID = 1001
        const val ACTION_CANCEL_ALL = "com.darkvault.app.CANCEL_ALL_UPLOADS"
        const val ACTION_CANCEL_JOB = "com.darkvault.app.CANCEL_JOB"
        const val ACTION_PAUSE_ALL = "com.darkvault.app.PAUSE_ALL_UPLOADS"
        const val ACTION_RESUME_ALL = "com.darkvault.app.RESUME_ALL_UPLOADS"
        const val EXTRA_JOB_ID = "job_id"
    }

    // Thrown from the onProgress callback to abort uploadChunked cleanly.
    // Caught separately so the job is marked Cancelled, not Failed.
    private class UploadCancelledException : Exception("Upload cancelled by user")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_ALL -> {
                // Cancel all queued jobs (not yet started)
                UploadState.queue.forEach { UploadState.cancelledIds.add(it.id) }
                // Cancel the currently active upload — it was poll()ed off the queue
                // so queue.forEach above misses it; wake its resume signal in case it
                // is paused so the cancel check fires immediately
                UploadState.active.value?.let { activeUpload ->
                    UploadState.cancelledIds.add(activeUpload.jobId)
                    UploadState.resumeSignals[activeUpload.jobId]?.trySend(Unit)
                }
                // Unblock any other paused jobs so they see the cancel flag
                UploadState.pausedIds.toList().forEach { id ->
                    UploadState.cancelledIds.add(id)
                    UploadState.resumeSignals[id]?.trySend(Unit)
                }
            }
            ACTION_CANCEL_JOB -> {
                intent.getStringExtra(EXTRA_JOB_ID)?.let { id ->
                    UploadState.cancelledIds.add(id)
                    // Wake resume channel regardless of pause state so the
                    // cancel check in onProgress fires at the next chunk boundary
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
                val ids = UploadState.pausedIds.toList()
                ids.forEach { id ->
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

    private fun processQueue() {
        scope.launch {
            val password = VaultSession.masterPassword
            val account = VaultSession.signedInAccount
            if (password == null || account == null) {
                finish(); return@launch
            }

            val client = DriveApiClient(this@UploadForegroundService, account)

            val activeJobIds = mutableListOf<String>()
            val activeFileNames = mutableListOf<String>()
            var failedCount = 0
            var lastFailedError: String? = null

            // Task 5 — track total/completed for multi-file display
            val totalJobs = UploadState.queue.size
            UploadState.totalInQueue.value = totalJobs
            UploadState.completedInQueue.value = 0
            var jobsDone = 0

            while (UploadState.queue.isNotEmpty()) {
                val job = UploadState.queue.poll() ?: break
                UploadState.queueSize.value = UploadState.queue.size

                if (job.id in UploadState.cancelledIds) {
                    UploadState.cancelledIds.remove(job.id)
                    UploadState.events.emit(UploadEvent.Cancelled(job.id, job.originalName))
                    continue
                }

                activeJobIds.add(job.id)
                activeFileNames.add(job.originalName)
                if (com.darkvault.app.BuildConfig.DEBUG) {
                    com.darkvault.app.debug.DeveloperOptionsManager.updateUploadDiagnostics(
                        activeJobIds.toList(), activeFileNames.toList(), failedCount, lastFailedError
                    )
                }

                // Task 5 — create resume signal channel for this job
                UploadState.resumeSignals[job.id] = Channel(capacity = 1)

                try {
                    if (com.darkvault.app.BuildConfig.DEBUG &&
                        com.darkvault.app.debug.DeveloperOptionsManager.simulateUploadFailure.value) {
                        com.darkvault.app.debug.DeveloperOptionsManager.simulateUploadFailure.value = false
                        throw Exception("Simulated upload failure (debug injection)")
                    }

                    // Idempotent check
                    if (client.fileExistsByClientId(job.id, job.folderId)) {
                        UploadState.events.emit(UploadEvent.Completed(job.id, job.originalName))
                        jobsDone++
                        UploadState.completedInQueue.value = jobsDone
                        continue
                    }

                    // Task 2 — conflict detection (check if originalName exists)
                    val existingName = client.findExistingOriginalName(job.originalName, job.folderId)
                    val uploadName: String
                    if (existingName != null) {
                        // Conflict: emit event and wait for user resolution
                        val suggestedName = client.findUniqueOriginalName(job.originalName, job.folderId)
                        val conflictsAhead = UploadState.queue.count { q ->
                            client.findExistingOriginalName(q.originalName, q.folderId) != null
                        }
                        UploadState.events.emit(
                            UploadEvent.ConflictDetected(
                                jobId = job.id,
                                originalName = job.originalName,
                                suggestedName = suggestedName,
                                conflictIndex = 1,
                                totalConflicts = 1
                            )
                        )
                        // Wait for user resolution
                        val resolution = try {
                            UploadState.conflictChannel.receive()
                        } catch (e: Exception) {
                            ConflictResolution.Skip
                        }

                        uploadName = when (resolution) {
                            is ConflictResolution.Skip -> {
                                UploadState.events.emit(UploadEvent.Skipped(job.id, job.originalName))
                                jobsDone++
                                UploadState.completedInQueue.value = jobsDone
                                continue
                            }
                            is ConflictResolution.Rename -> suggestedName
                            is ConflictResolution.RenameAs -> resolution.newName.ifBlank { suggestedName }
                            is ConflictResolution.Replace -> {
                                // Trash the existing conflicting file
                                try {
                                    val existingFileId = client.findFileIdByOriginalName(job.originalName, job.folderId)
                                    if (existingFileId != null) client.trashFile(existingFileId)
                                } catch (_: Exception) { /* fail gracefully */ }
                                job.originalName
                            }
                        }
                    } else {
                        uploadName = job.originalName
                    }

                    val wasRenamed = uploadName != job.originalName

                    // Encrypt
                    UploadState.active.value = ActiveUpload(
                        job.id, uploadName, 0, job.fileSize, "Encrypting…",
                        currentIndex = jobsDone + 1, totalInBatch = totalJobs
                    )
                    UploadState.events.emit(UploadEvent.Encrypting(job.id, uploadName))
                    updateNotification("Encrypting $uploadName…", 0, 0)

                    if (job.id in UploadState.cancelledIds) {
                        UploadState.cancelledIds.remove(job.id)
                        UploadState.events.emit(UploadEvent.Cancelled(job.id, job.originalName))
                        continue
                    }

                    val encBytes = withContext(Dispatchers.Default) {
                        val inp = contentResolver.openInputStream(job.uri)!!
                        val out = ByteArrayOutputStream()
                        val dek = VaultSession.dek
                        if (dek != null) {
                            CryptoManager.encryptWithDek(inp, out, dek)
                        } else {
                            CryptoManager.encrypt(inp, out, password)
                        }
                        out.toByteArray()
                    }

                    try {
                        val total = encBytes.size.toLong()

                        val sessionUri = client.startResumableSession(
                            "${uploadName}.vault",
                            uploadName,
                            job.mimeType,
                            job.folderId,
                            total,
                            clientId = job.id
                        )

                        UploadState.active.value = ActiveUpload(
                            job.id, uploadName, 0, total, "Uploading…",
                            currentIndex = jobsDone + 1, totalInBatch = totalJobs
                        )
                        UploadState.events.emit(UploadEvent.Uploading(job.id, uploadName, 0, total))

                        client.uploadChunked(sessionUri, encBytes) { uploaded, t ->
                            // Cancel check — throw to abort the chunk loop;
                            // caught below as UploadCancelledException, not as a failure
                            if (job.id in UploadState.cancelledIds) {
                                throw UploadCancelledException()
                            }

                            // Pause check — actually suspend here until resumed or cancelled
                            if (job.id in UploadState.pausedIds) {
                                UploadState.active.value = ActiveUpload(
                                    job.id, uploadName, uploaded, t, "Paused",
                                    isPaused = true,
                                    currentIndex = jobsDone + 1, totalInBatch = totalJobs
                                )
                                UploadState.events.emit(UploadEvent.Uploading(job.id, uploadName, uploaded, t))
                                updateNotificationPaused()
                                // Block here; unblocked by ACTION_RESUME_ALL or ACTION_CANCEL
                                UploadState.resumeSignals[job.id]?.receive()
                                // Re-check cancel after being woken up
                                if (job.id in UploadState.cancelledIds) {
                                    throw UploadCancelledException()
                                }
                            }

                            UploadState.active.value = ActiveUpload(
                                job.id, uploadName, uploaded, t, "Uploading…",
                                isPaused = false,
                                currentIndex = jobsDone + 1, totalInBatch = totalJobs
                            )
                            UploadState.events.emit(UploadEvent.Uploading(job.id, uploadName, uploaded, t))
                            updateNotification("Uploading $uploadName", uploaded, t)
                        }

                    } finally {
                        java.util.Arrays.fill(encBytes, 0)
                    }

                    UploadState.active.value = null
                    activeJobIds.remove(job.id)
                    activeFileNames.remove(job.originalName)
                    jobsDone++
                    UploadState.completedInQueue.value = jobsDone

                    if (wasRenamed) {
                        UploadState.events.emit(UploadEvent.Renamed(job.id, job.originalName, uploadName))
                    }
                    UploadState.events.emit(UploadEvent.Completed(job.id, uploadName))

                } catch (e: UploadCancelledException) {
                    UploadState.active.value = null
                    activeJobIds.remove(job.id)
                    activeFileNames.remove(job.originalName)
                    UploadState.cancelledIds.remove(job.id)
                    UploadState.events.emit(UploadEvent.Cancelled(job.id, job.originalName))
                } catch (e: Exception) {
                    UploadState.active.value = null
                    activeJobIds.remove(job.id)
                    activeFileNames.remove(job.originalName)
                    failedCount++
                    lastFailedError = e.message ?: "Upload failed"
                    UploadState.events.emit(
                        UploadEvent.Failed(job.id, job.originalName, lastFailedError!!)
                    )
                } finally {
                    UploadState.resumeSignals.remove(job.id)
                    UploadState.pausedIds.remove(job.id)
                }
                if (com.darkvault.app.BuildConfig.DEBUG) {
                    com.darkvault.app.debug.DeveloperOptionsManager.updateUploadDiagnostics(
                        activeJobIds.toList(), activeFileNames.toList(), failedCount, lastFailedError
                    )
                }
            }

            finish()
        }
    }

    private fun finish() {
        UploadState.active.value = null
        UploadState.queueSize.value = 0
        UploadState.totalInQueue.value = 0
        UploadState.completedInQueue.value = 0
        UploadState.pausedCount.value = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

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

        if (paused) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
        }
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
                upload?.total ?: 0L,
                true
            ))
    }

    private fun ensureChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Upload Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows darkVault upload progress"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
