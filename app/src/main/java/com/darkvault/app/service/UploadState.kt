package com.darkvault.app.service

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentHashMap

data class UploadJob(
    val id: String,
    val uri: Uri,
    val originalName: String,
    val mimeType: String,
    val fileSize: Long,
    val folderId: String
)

sealed class UploadEvent {
    data class Encrypting(val jobId: String, val fileName: String) : UploadEvent()
    data class Uploading(val jobId: String, val fileName: String, val uploaded: Long, val total: Long) : UploadEvent()
    data class Completed(val jobId: String, val fileName: String) : UploadEvent()
    data class Failed(val jobId: String, val fileName: String, val reason: String) : UploadEvent()
    data class Duplicate(val jobId: String, val fileName: String) : UploadEvent()
    data class Cancelled(val jobId: String, val fileName: String) : UploadEvent()
}

/** Active upload job visible in the UI progress card. */
data class ActiveUpload(
    val jobId: String,
    val fileName: String,
    val uploaded: Long = 0L,
    val total: Long = 0L,
    val stage: String = "Queued"
) {
    val progress: Float get() = if (total > 0) uploaded.toFloat() / total else 0f
}

object UploadState {
    val queue = ConcurrentLinkedDeque<UploadJob>()
    val cancelledIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Shared flow for completion/error/duplicate events consumed by the ViewModel. */
    val events = MutableSharedFlow<UploadEvent>(extraBufferCapacity = 128)

    /** Currently active upload, visible in the UI as a progress card. */
    val active = MutableStateFlow<ActiveUpload?>(null)

    /** Snapshot of all active + queued job IDs (updated by service). */
    val queueSize = MutableStateFlow(0)
}
