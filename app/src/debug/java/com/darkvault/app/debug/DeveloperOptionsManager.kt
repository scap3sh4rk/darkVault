package com.darkvault.app.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton that aggregates diagnostic data for the debug panel.
 * Only compiled into debug builds (src/debug source set).
 * All main-source-set code references this only behind BuildConfig.DEBUG guards.
 */
object DeveloperOptionsManager {

    // ── Section A: Crypto & Key State ─────────────────────────────────────

    val dekLoaded = MutableStateFlow(false)
    val kekSaltHex = MutableStateFlow<String?>(null)
    val vaultKeyPresentOnDrive = MutableStateFlow<Boolean?>(null) // null = unknown
    val lastWrapTimestampMs = MutableStateFlow(0L)
    val lastUnwrapTimestampMs = MutableStateFlow(0L)

    // ── Section B: Auth & Session ─────────────────────────────────────────

    val currentAuthStateName = MutableStateFlow("Init")
    val sessionPasswordEntered = MutableStateFlow(false)
    val biometricEnrolled = MutableStateFlow(false)
    /** Epoch ms when the auto-lock timer will fire; 0 = disabled. */
    val autoLockFiresAtMs = MutableStateFlow(0L)
    val failedUnlockAttempts = MutableStateFlow(0)
    val lockoutExpiryMs = MutableStateFlow(0L)

    // ── Section C: Drive & Network ────────────────────────────────────────

    data class DriveCallRecord(
        val endpoint: String,
        val httpStatus: Int,
        val latencyMs: Long,
        val timestampMs: Long = System.currentTimeMillis()
    )

    val lastDriveCall = MutableStateFlow<DriveCallRecord?>(null)
    /** Pending retry count (attempts outstanding in withRetry). */
    val retryQueueDepth = MutableStateFlow(0)
    /** Set to true to inject a fake 429 into the next Drive call. */
    val inject429 = MutableStateFlow(false)

    // ── Section D: Upload Pipeline ────────────────────────────────────────

    data class UploadDiagnostics(
        val activeCount: Int = 0,
        val activeFileNames: List<String> = emptyList(),
        val activeClientIds: List<String> = emptyList(),
        val failedCount: Int = 0,
        val lastFailedError: String? = null
    )

    val uploadDiagnostics = MutableStateFlow(UploadDiagnostics())
    /** Set to true to mark the next upload job as failed (fault injection). */
    val simulateUploadFailure = MutableStateFlow(false)

    // ── Section E: Integrity check results ───────────────────────────────

    val integrityCheckRunning = MutableStateFlow(false)
    val integrityCheckResult = MutableStateFlow<String?>(null)

    // ── Section F: Log capture ────────────────────────────────────────────

    val capturedLogs = MutableStateFlow<String>("")

    // ── Helpers called from main source set ──────────────────────────────

    /** Called after each encryptWithDek / decrypt call with file size and duration. */
    fun recordCryptoOp(opName: String, fileSizeBytes: Long, durationMs: Long) {
        // No-op in release; this object only exists in debug
        android.util.Log.d("darkVault", "[Crypto] $opName size=${fileSizeBytes}B duration=${durationMs}ms")
    }

    /** Called after each OkHttp Drive API call. */
    fun recordDriveCall(endpoint: String, httpStatus: Int, latencyMs: Long) {
        lastDriveCall.value = DriveCallRecord(endpoint, httpStatus, latencyMs)
        android.util.Log.d("darkVault", "[Drive] $endpoint → $httpStatus (${latencyMs}ms)")
    }

    /** Called from withRetry to track retry depth. */
    fun onRetryAttempt(attempt: Int) {
        retryQueueDepth.value = attempt
    }

    /** Called from UploadForegroundService with the current pipeline snapshot. */
    fun updateUploadDiagnostics(
        activeJobIds: List<String>,
        activeFileNames: List<String>,
        failedCount: Int,
        lastFailedError: String?
    ) {
        uploadDiagnostics.value = UploadDiagnostics(
            activeCount = activeJobIds.size,
            activeFileNames = activeFileNames,
            activeClientIds = activeJobIds,
            failedCount = failedCount,
            lastFailedError = lastFailedError
        )
    }

    /** Called from AuthViewModel whenever auth state changes. */
    fun updateAuthState(
        stateName: String,
        passwordEntered: Boolean,
        biometricEnrolledNow: Boolean,
        autoLockFireAt: Long,
        failedAttempts: Int,
        lockoutUntil: Long
    ) {
        currentAuthStateName.value = stateName
        sessionPasswordEntered.value = passwordEntered
        biometricEnrolled.value = biometricEnrolledNow
        autoLockFiresAtMs.value = autoLockFireAt
        failedUnlockAttempts.value = failedAttempts
        lockoutExpiryMs.value = lockoutUntil
    }

    /** Called after DEK is loaded (unwrap). */
    fun onDekUnwrapped(kekSaltBytes: ByteArray?) {
        dekLoaded.value = true
        lastUnwrapTimestampMs.value = System.currentTimeMillis()
        kekSaltHex.value = kekSaltBytes?.take(8)?.joinToString("") { "%02X".format(it) }
    }

    /** Called after DEK is wrapped (new setup or password change). */
    fun onDekWrapped() {
        lastWrapTimestampMs.value = System.currentTimeMillis()
    }

    /** Called when DEK is cleared. */
    fun onDekCleared() {
        dekLoaded.value = false
    }

    /** Called when vault.key presence is confirmed or not found. */
    fun setVaultKeyPresent(present: Boolean) {
        vaultKeyPresentOnDrive.value = present
    }

    // ── Reset helpers ─────────────────────────────────────────────────────

    fun resetAll() {
        dekLoaded.value = false
        kekSaltHex.value = null
        vaultKeyPresentOnDrive.value = null
        lastWrapTimestampMs.value = 0L
        lastUnwrapTimestampMs.value = 0L
        currentAuthStateName.value = "Init"
        sessionPasswordEntered.value = false
        biometricEnrolled.value = false
        autoLockFiresAtMs.value = 0L
        failedUnlockAttempts.value = 0
        lockoutExpiryMs.value = 0L
        lastDriveCall.value = null
        retryQueueDepth.value = 0
        inject429.value = false
        uploadDiagnostics.value = UploadDiagnostics()
        simulateUploadFailure.value = false
        integrityCheckResult.value = null
        capturedLogs.value = ""
    }
}
