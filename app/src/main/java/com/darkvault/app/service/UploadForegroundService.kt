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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class UploadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "darkvault_uploads"
        const val NOTIF_ID = 1001
        const val ACTION_CANCEL_ALL = "com.darkvault.app.CANCEL_ALL_UPLOADS"
        const val ACTION_CANCEL_JOB = "com.darkvault.app.CANCEL_JOB"
        const val EXTRA_JOB_ID = "job_id"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_ALL -> {
                UploadState.queue.forEach { UploadState.cancelledIds.add(it.id) }
            }
            ACTION_CANCEL_JOB -> {
                intent.getStringExtra(EXTRA_JOB_ID)?.let { UploadState.cancelledIds.add(it) }
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification("Preparing…", 0, 0))
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

            // Debug diagnostics tracking
            val activeJobIds = mutableListOf<String>()
            val activeFileNames = mutableListOf<String>()
            var failedCount = 0
            var lastFailedError: String? = null

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

                try {
                    // Fault injection: simulate failure if requested
                    if (com.darkvault.app.BuildConfig.DEBUG &&
                        com.darkvault.app.debug.DeveloperOptionsManager.simulateUploadFailure.value) {
                        com.darkvault.app.debug.DeveloperOptionsManager.simulateUploadFailure.value = false
                        throw Exception("Simulated upload failure (debug injection)")
                    }
                    // Idempotent check — skip if this job was already completed on Drive
                    if (client.fileExistsByClientId(job.id, job.folderId)) {
                        UploadState.events.emit(UploadEvent.Completed(job.id, job.originalName))
                        continue
                    }

                    // Rename duplicates instead of skipping
                    val uploadName = client.findUniqueOriginalName(job.originalName, job.folderId)
                    val wasRenamed = uploadName != job.originalName

                    // Encrypt
                    UploadState.active.value = ActiveUpload(job.id, uploadName, 0, job.fileSize, "Encrypting…")
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

                    // Start resumable session — include clientId for idempotency
                    val sessionUri = client.startResumableSession(
                        "${uploadName}.vault",
                        uploadName,
                        job.mimeType,
                        job.folderId,
                        total,
                        clientId = job.id
                    )

                    // Upload in chunks
                    UploadState.active.value = ActiveUpload(job.id, uploadName, 0, total, "Uploading…")
                    UploadState.events.emit(UploadEvent.Uploading(job.id, uploadName, 0, total))

                    client.uploadChunked(sessionUri, encBytes) { uploaded, t ->
                        if (job.id !in UploadState.cancelledIds) {
                            UploadState.active.value = ActiveUpload(job.id, uploadName, uploaded, t, "Uploading…")
                            UploadState.events.emit(UploadEvent.Uploading(job.id, uploadName, uploaded, t))
                            updateNotification("Uploading $uploadName", uploaded, t)
                        }
                    }

                    } finally {
                        // Fix: MEDIUM-001 — zero encBytes after upload completes, fails, or is cancelled
                        java.util.Arrays.fill(encBytes, 0)
                    }

                    UploadState.active.value = null
                    activeJobIds.remove(job.id)
                    activeFileNames.remove(job.originalName)

                    if (wasRenamed) {
                        UploadState.events.emit(UploadEvent.Renamed(job.id, job.originalName, uploadName))
                    }
                    UploadState.events.emit(UploadEvent.Completed(job.id, uploadName))

                } catch (e: Exception) {
                    UploadState.active.value = null
                    activeJobIds.remove(job.id)
                    activeFileNames.remove(job.originalName)
                    failedCount++
                    lastFailedError = e.message ?: "Upload failed"
                    UploadState.events.emit(
                        UploadEvent.Failed(job.id, job.originalName, lastFailedError!!)
                    )
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String, uploaded: Long, total: Long): Notification {
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("darkVault Upload")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)

        if (total > 0) {
            val pct = ((uploaded.toFloat() / total) * 100).toInt()
            builder.setProgress(100, pct, false)
                .setSubText("$pct%")
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(text: String, uploaded: Long, total: Long) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text, uploaded, total))
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
