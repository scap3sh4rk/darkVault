package com.darkvault.app.service

import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

data class UploadJob(
    val id: String,
    val uri: Uri,
    val originalName: String,
    val mimeType: String,
    val fileSize: Long,
    val folderId: String
)

/**
 * Produced by the encryption worker after the file has been encrypted to a local staging file.
 * Consumed by the upload worker.
 */
data class ReadyToUploadJob(
    val id: String,               // same UUID as the originating UploadJob
    val mainFile: File,           // encrypted staging file (ciphertext, safe at rest)
    val thumbFile: File?,         // encrypted thumbnail staging file, or null
    val originalName: String,
    val uploadName: String,       // resolved after conflict dialog
    val mimeType: String,
    val originalSize: Long,       // original plaintext size, for UI display
    val folderId: String
)

sealed class UploadEvent {
    data class Encrypting(val jobId: String, val fileName: String) : UploadEvent()
    data class Uploading(val jobId: String, val fileName: String, val uploaded: Long, val total: Long) : UploadEvent()
    data class Completed(val jobId: String, val fileName: String) : UploadEvent()
    data class Renamed(val jobId: String, val originalName: String, val newName: String) : UploadEvent()
    data class Failed(val jobId: String, val fileName: String, val reason: String) : UploadEvent()
    data class Duplicate(val jobId: String, val fileName: String) : UploadEvent()
    data class Cancelled(val jobId: String, val fileName: String) : UploadEvent()
    data class Skipped(val jobId: String, val fileName: String) : UploadEvent()
    // Task 2 — conflict detected; waits for ConflictResolution via conflictChannel
    data class ConflictDetected(
        val jobId: String,
        val originalName: String,
        val suggestedName: String,
        val conflictIndex: Int,
        val totalConflicts: Int
    ) : UploadEvent()
}

/** Resolution chosen by the user in the conflict dialog. */
sealed class ConflictResolution {
    /** Rename the file to suggestedName (auto-suggested) */
    object Rename : ConflictResolution()
    /** Rename the file to a custom user-chosen name */
    data class RenameAs(val newName: String) : ConflictResolution()
    /** Trash existing Drive file then upload with original name */
    object Replace : ConflictResolution()
    /** Skip this file entirely */
    object Skip : ConflictResolution()
}

/** Active upload job visible in the UI progress card. */
data class ActiveUpload(
    val jobId: String,
    val fileName: String,
    val uploaded: Long = 0L,
    val total: Long = 0L,
    val stage: String = "Queued",
    val isPaused: Boolean = false,
    val currentIndex: Int = 0,
    val totalInBatch: Int = 0
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

    // Task 5 — pause / resume
    val pausedIds: MutableSet<String> = CopyOnWriteArraySet()
    /** One channel per job; send Unit to resume. */
    val resumeSignals = ConcurrentHashMap<String, Channel<Unit>>()
    /** How many jobs are currently paused. */
    val pausedCount = MutableStateFlow(0)

    // Task 7 multi-file queue tracking
    val totalInQueue = MutableStateFlow(0)
    val completedInQueue = MutableStateFlow(0)

    // Task 2 — conflict resolution channel (one active at a time)
    val conflictChannel = Channel<ConflictResolution>(capacity = 1)
}
