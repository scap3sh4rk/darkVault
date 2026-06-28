package com.darkvault.app.debug

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * No-op stub for release builds.
 * The real implementation lives in src/debug/ and is only compiled into debug builds.
 * All call sites are gated behind BuildConfig.DEBUG, so none of these methods
 * are ever invoked in release — but the type must be resolvable at compile time.
 */
object DeveloperOptionsManager {

    val dekLoaded = MutableStateFlow(false)
    val simulateUploadFailure = MutableStateFlow(false)
    val inject429 = MutableStateFlow(false)

    fun recordCryptoOp(opName: String, fileSizeBytes: Long, durationMs: Long) = Unit
    fun recordDriveCall(endpoint: String, httpStatus: Int, latencyMs: Long) = Unit
    fun onRetryAttempt(attempt: Int) = Unit
    fun updateUploadDiagnostics(
        activeJobIds: List<String>,
        activeFileNames: List<String>,
        failedCount: Int,
        lastFailedError: String?
    ) = Unit
    fun updateAuthState(
        stateName: String,
        passwordEntered: Boolean,
        biometricEnrolledNow: Boolean,
        autoLockFireAt: Long,
        failedAttempts: Int,
        lockoutUntil: Long
    ) = Unit
    fun onDekUnwrapped(kekSaltBytes: ByteArray?) = Unit
    fun onDekWrapped() = Unit
    fun onDekCleared() = Unit
    fun setVaultKeyPresent(present: Boolean) = Unit
    fun onNfcEnrolled(tagType: String, mode: String) = Unit
    fun onNfcTagDetected(tagType: String, uidPrefix: String) = Unit
    fun onNfcCleared() = Unit
    fun resetAll() = Unit
}
