package com.darkvault.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkvault.app.VaultSession
import com.darkvault.app.crypto.BiometricHelper
import com.darkvault.app.crypto.BiometricKeyManager
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.crypto.VaultKeyManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.drive.DriveApiClient
import com.darkvault.app.drive.VaultKeyFull
import com.darkvault.app.model.VaultKeyBundle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher

private const val TAG = "AuthViewModel"

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    val isSetupDone: StateFlow<Boolean?> = prefs.isSetupDone.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    val biometricEnabled: StateFlow<Boolean> = prefs.biometricEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    val autoLockMinutes: StateFlow<Int> = prefs.autoLockMinutes.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )

    val failedAttempts: StateFlow<Int> = prefs.failedAttempts.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )

    val lockoutUntilMs: StateFlow<Long> = prefs.lockoutUntilMs.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0L
    )

    private val _masterPassword = MutableStateFlow<String?>(null)
    val masterPassword: StateFlow<String?> = _masterPassword

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Biometric enrollment cipher needed on SettingsScreen
    private val _enrollCipherReady = MutableStateFlow<Cipher?>(null)
    val enrollCipherReady: StateFlow<Cipher?> = _enrollCipherReady

    val biometricAvailableOnDevice: Boolean
        get() = BiometricHelper.isAvailable(getApplication())

    private var autoLockJob: Job? = null

    /** Emits the formatted recovery key once after first vault key creation. */
    private val _recoveryKey = MutableStateFlow<String?>(null)
    val recoveryKey: StateFlow<String?> = _recoveryKey

    fun clearRecoveryKey() { _recoveryKey.value = null }

    // ── Setup ─────────────────────────────────────────────────────────────

    fun setup(password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val (hash, salt) = withContext(Dispatchers.Default) {
                CryptoManager.hashPassword(password)
            }
            prefs.savePasswordHash(hash, salt)
            setActiveSession(password)
            _isLoading.value = false
            onDone()
        }
    }

    // ── Unlock ────────────────────────────────────────────────────────────

    private enum class UnlockAttemptResult {
        SUCCESS,
        WRONG_PASSWORD,
        /** vault.key was re-keyed by another device with a different password. */
        CHANGED_ON_OTHER_DEVICE,
        /** Network unavailable or no vault.key yet; fall back to local hash. */
        NETWORK_FALLBACK
    }

    /**
     * Drive-backed authentication: derive KEK from [password], attempt to unwrap
     * the DEK from vault.key, and store the DEK in [VaultSession] on success.
     *
     * Also cross-checks the local hash — if the DEK unwrap succeeds but the local
     * hash is stale (another device changed the password), the local hash is updated
     * so subsequent offline unlocks still work.
     *
     * Returns [UnlockAttemptResult.NETWORK_FALLBACK] on any I/O error so the caller
     * can fall back to local-hash verification.
     */
    private suspend fun tryUnlockWithVaultKey(
        password: String,
        account: GoogleSignInAccount
    ): UnlockAttemptResult = withContext(Dispatchers.IO) {
        try {
            val folderId = prefs.vaultKeyFolderId.first() ?: return@withContext UnlockAttemptResult.NETWORK_FALLBACK
            val client = DriveApiClient(getApplication(), account)
            val full = client.downloadVaultKeyFull(folderId) ?: return@withContext UnlockAttemptResult.NETWORK_FALLBACK

            val bundle = VaultKeyBundle.fromJson(full.json)
            val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded
            return@withContext try {
                val dek = VaultKeyManager.unwrapDek(bundle.dekWrappedByKek, kek)
                java.util.Arrays.fill(kek, 0)

                // If local hash is stale (password was changed on another device),
                // update it so offline unlocks work going forward.
                val stored = prefs.getPasswordHashAndSalt()
                if (stored == null || !CryptoManager.verifyPassword(password, stored.first, stored.second)) {
                    val (h, s) = CryptoManager.hashPassword(password)
                    prefs.savePasswordHash(h, s)
                    Log.d(TAG, "Local hash updated: password was changed on another device")
                }

                VaultSession.dek = dek
                UnlockAttemptResult.SUCCESS
            } catch (e: javax.crypto.AEADBadTagException) {
                java.util.Arrays.fill(kek, 0)
                // DEK unwrap failed. If the local hash DOES match, the vault.key was
                // re-encrypted with a different password by another device.
                val stored = prefs.getPasswordHashAndSalt()
                if (stored != null && CryptoManager.verifyPassword(password, stored.first, stored.second)) {
                    UnlockAttemptResult.CHANGED_ON_OTHER_DEVICE
                } else {
                    UnlockAttemptResult.WRONG_PASSWORD
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Drive-backed auth unavailable, falling back to local hash", e)
            UnlockAttemptResult.NETWORK_FALLBACK
        }
    }

    fun unlock(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null

            // Check lockout
            val lockout = prefs.lockoutUntilMs.first()
            val now = System.currentTimeMillis()
            if (now < lockout) {
                val seconds = (lockout - now + 999) / 1000
                _authError.value = "Too many failed attempts. Try again in ${seconds}s."
                _isLoading.value = false
                return@launch
            }

            // ── Drive-backed auth (online, vault.key exists) ──────────────────
            @Suppress("DEPRECATION")
            val account = GoogleSignIn.getLastSignedInAccount(getApplication())
            if (account != null) {
                when (tryUnlockWithVaultKey(password, account)) {
                    UnlockAttemptResult.SUCCESS -> {
                        prefs.clearFailedAttempts()
                        setActiveSession(password)
                        _isLoading.value = false
                        onSuccess()
                        return@launch
                    }
                    UnlockAttemptResult.WRONG_PASSWORD -> {
                        recordFailedAttemptAndError()
                        _isLoading.value = false
                        return@launch
                    }
                    UnlockAttemptResult.CHANGED_ON_OTHER_DEVICE -> {
                        _authError.value =
                            "Password was changed from another device. Enter the new password, or use your recovery key."
                        _isLoading.value = false
                        return@launch
                    }
                    UnlockAttemptResult.NETWORK_FALLBACK -> { /* fall through */ }
                }
            }

            // ── Offline fallback: local hash ──────────────────────────────────
            val stored = prefs.getPasswordHashAndSalt()
            val valid = stored != null && withContext(Dispatchers.Default) {
                CryptoManager.verifyPassword(password, stored.first, stored.second)
            }
            if (valid) {
                prefs.clearFailedAttempts()
                setActiveSession(password)
                onSuccess()
            } else {
                recordFailedAttemptAndError()
            }
            _isLoading.value = false
        }
    }

    private suspend fun recordFailedAttemptAndError() {
        val attempts = prefs.failedAttempts.first() + 1
        val shift = minOf(attempts - 1, 30)
        val backoffMs = minOf(30_000L * (1L shl shift), 30 * 60 * 1000L)
        prefs.recordFailedAttempt(attempts, System.currentTimeMillis() + backoffMs)
        _authError.value = "Incorrect password"
    }

    fun unlockWithBiometricCipher(cipher: Cipher, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val creds = prefs.getBiometricCredentials()
            if (creds == null) {
                _authError.value = "Biometric credentials not found"
                return@launch
            }
            try {
                val password = String(cipher.doFinal(creds.second), Charsets.UTF_8)
                setActiveSession(password)
                onSuccess()
            } catch (e: Exception) {
                _authError.value = "Biometric decryption failed"
            }
        }
    }

    fun loadOrCreateDek(password: String, folderId: String, account: GoogleSignInAccount) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val client = DriveApiClient(getApplication(), account)
                    val existingJson = client.downloadVaultKey(folderId)

                    if (existingJson != null) {
                        val bundle = VaultKeyBundle.fromJson(existingJson)
                        val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded
                        val dek = VaultKeyManager.unwrapDek(bundle.dekWrappedByKek, kek)
                        java.util.Arrays.fill(kek, 0)
                        VaultSession.dek = dek
                        Log.d(TAG, "DEK loaded from existing vault.key")
                    } else {
                        val dek = VaultKeyManager.generateDek()
                        val recoveryKey = VaultKeyManager.generateRecoveryKey()
                        val kekSalt = SecureRandom().generateSeed(16)
                        val kek = CryptoManager.deriveKey(password, kekSalt).encoded

                        val bundle = VaultKeyBundle(
                            version = 1,
                            kekSalt = kekSalt,
                            dekWrappedByKek = VaultKeyManager.wrapDek(dek, kek),
                            dekWrappedByRecovery = VaultKeyManager.wrapDek(dek, recoveryKey)
                        )

                        client.uploadVaultKey(bundle.toJson(), folderId)
                        prefs.setHasVaultKey(folderId)
                        VaultSession.dek = dek
                        _recoveryKey.value = VaultKeyManager.formatRecoveryKey(recoveryKey)

                        java.util.Arrays.fill(recoveryKey, 0)
                        java.util.Arrays.fill(kek, 0)
                        Log.d(TAG, "New DEK created and vault.key uploaded")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load/create DEK — falling back to per-file derivation", e)
                }
            }
        }
    }

    // ── Biometric enrollment ──────────────────────────────────────────────

    fun prepareEnrollCipher(): Cipher? {
        return try { BiometricKeyManager.getCipherForEncryption() } catch (e: Exception) { null }
    }

    fun completeEnrollment(cipher: Cipher) {
        viewModelScope.launch {
            val password = _masterPassword.value ?: return@launch
            try {
                val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
                prefs.saveBiometricCredentials(cipher.iv, encrypted)
            } catch (e: Exception) {
                prefs.clearBiometricCredentials()
                BiometricKeyManager.deleteKey()
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            prefs.clearBiometricCredentials()
            BiometricKeyManager.deleteKey()
        }
    }

    // ── Lock / auto-lock ──────────────────────────────────────────────────

    fun lockVault() {
        VaultSession.clearDek()
        _masterPassword.value = null
        VaultSession.masterPassword = null
        autoLockJob?.cancel()
    }

    fun onAppBackground() {
        val minutes = autoLockMinutes.value
        if (minutes <= 0) return
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            lockVault()
        }
    }

    fun onAppForeground() { autoLockJob?.cancel() }

    fun clearError() { _authError.value = null }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setAutoLockMinutes(minutes) }
    }

    // ── Change password ───────────────────────────────────────────────────

    sealed class PasswordChangeResult {
        object Success : PasswordChangeResult()
        data class Error(val message: String) : PasswordChangeResult()
    }

    /**
     * Changes the master password.
     * - Verifies [currentPassword] against vault.key (Drive source of truth) or local hash.
     * - Re-derives KEK, re-wraps DEK in-place on Drive (no delete–create gap).
     * - Retries up to 3× on conflict (another device modified vault.key concurrently).
     * - Updates local hash so offline unlocks work on all devices once they next connect.
     */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        account: GoogleSignInAccount? = null,
        folderId: String? = null
    ): PasswordChangeResult = withContext(Dispatchers.IO) {
        // Local hash verification first (fast gate)
        val stored = prefs.getPasswordHashAndSalt()
            ?: return@withContext PasswordChangeResult.Error("No stored credentials")
        if (!CryptoManager.verifyPassword(currentPassword, stored.first, stored.second)) {
            return@withContext PasswordChangeResult.Error("Current password is incorrect")
        }

        // Re-wrap DEK on Drive using conditional in-place update
        if (account != null && folderId != null) {
            val client = DriveApiClient(getApplication(), account)
            var lastError: String? = null
            repeat(3) { attempt ->
                try {
                    val full = client.downloadVaultKeyFull(folderId)
                        ?: return@repeat   // vault.key doesn't exist yet; skip Drive update
                    val oldBundle = VaultKeyBundle.fromJson(full.json)

                    // Confirm current KEK still unwraps the DEK (catches stale local hash)
                    val currentKek = CryptoManager.deriveKey(currentPassword, oldBundle.kekSalt).encoded
                    val dek = try {
                        VaultKeyManager.unwrapDek(oldBundle.dekWrappedByKek, currentKek)
                    } catch (e: javax.crypto.AEADBadTagException) {
                        java.util.Arrays.fill(currentKek, 0)
                        lastError = "Current password does not match vault.key — it may have been changed from another device"
                        return@repeat
                    } finally {
                        java.util.Arrays.fill(currentKek, 0)
                    }

                    val newKekSalt = SecureRandom().generateSeed(16)
                    val newKek = CryptoManager.deriveKey(newPassword, newKekSalt).encoded
                    val newBundle = VaultKeyBundle(
                        version = 1,
                        kekSalt = newKekSalt,
                        dekWrappedByKek = VaultKeyManager.wrapDek(dek, newKek),
                        dekWrappedByRecovery = oldBundle.dekWrappedByRecovery
                    )
                    java.util.Arrays.fill(newKek, 0)

                    val updated = client.updateVaultKeyInPlace(newBundle.toJson(), full.fileId, full.modifiedTime)
                    if (updated) {
                        // Update VaultSession.dek so UploadForegroundService uses new wrapping
                        VaultSession.dek = dek
                        lastError = null
                        return@repeat
                    }
                    // Conflict: another device modified vault.key, retry with fresh download
                    Log.w(TAG, "vault.key conflict on attempt $attempt — retrying")
                    lastError = "vault.key was modified concurrently. Please try again."
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to re-wrap DEK on attempt $attempt", e)
                    lastError = e.message
                }
            }
            if (lastError != null) return@withContext PasswordChangeResult.Error(lastError!!)
        }

        val (newHash, newSalt) = CryptoManager.hashPassword(newPassword)
        prefs.savePasswordHash(newHash, newSalt)
        prefs.clearFailedAttempts()
        setActiveSession(newPassword)
        PasswordChangeResult.Success
    }

    // ── Recovery with recovery key ────────────────────────────────────────

    /**
     * Recovers vault access using the offline recovery key:
     * 1. Downloads vault.key from Drive
     * 2. Unwraps DEK using the recovery key
     * 3. Sets a new master password and updates local hash
     * 4. Re-wraps DEK in-place on Drive (conflict-safe, retries 3×)
     * 5. Unlocks the session
     */
    suspend fun recoverWithRecoveryKey(
        recoveryKeyFormatted: String,
        newPassword: String,
        account: GoogleSignInAccount,
        folderId: String
    ): PasswordChangeResult = withContext(Dispatchers.IO) {
        try {
            val recoveryKeyBytes = VaultKeyManager.parseRecoveryKey(recoveryKeyFormatted)
            val client = DriveApiClient(getApplication(), account)

            var lastError = "vault.key not found on Drive — make sure you're signed in to the correct account"
            repeat(3) { attempt ->
                val full = client.downloadVaultKeyFull(folderId) ?: return@repeat
                val bundle = VaultKeyBundle.fromJson(full.json)

                val dek = try {
                    VaultKeyManager.unwrapDek(bundle.dekWrappedByRecovery, recoveryKeyBytes)
                } catch (e: javax.crypto.AEADBadTagException) {
                    lastError = "Recovery key is incorrect"
                    return@withContext PasswordChangeResult.Error(lastError)
                }

                val newKekSalt = SecureRandom().generateSeed(16)
                val newKek = CryptoManager.deriveKey(newPassword, newKekSalt).encoded
                val newBundle = VaultKeyBundle(
                    version = 1,
                    kekSalt = newKekSalt,
                    dekWrappedByKek = VaultKeyManager.wrapDek(dek, newKek),
                    dekWrappedByRecovery = bundle.dekWrappedByRecovery
                )
                java.util.Arrays.fill(newKek, 0)

                val updated = client.updateVaultKeyInPlace(newBundle.toJson(), full.fileId, full.modifiedTime)
                if (updated) {
                    java.util.Arrays.fill(recoveryKeyBytes, 0)
                    val (newHash, newSalt) = CryptoManager.hashPassword(newPassword)
                    prefs.savePasswordHash(newHash, newSalt)
                    prefs.setHasVaultKey(folderId)
                    prefs.clearFailedAttempts()
                    VaultSession.dek = dek
                    setActiveSession(newPassword)
                    return@withContext PasswordChangeResult.Success
                }
                Log.w(TAG, "vault.key conflict during recovery on attempt $attempt — retrying")
                lastError = "vault.key was modified concurrently. Please try again."
            }
            PasswordChangeResult.Error(lastError)
        } catch (e: IllegalArgumentException) {
            PasswordChangeResult.Error("Invalid recovery key format")
        } catch (e: Exception) {
            PasswordChangeResult.Error(e.message ?: "Recovery failed")
        }
    }

    private fun setActiveSession(password: String) {
        _masterPassword.value = password
        VaultSession.masterPassword = password
        autoLockJob?.cancel()
    }
}
