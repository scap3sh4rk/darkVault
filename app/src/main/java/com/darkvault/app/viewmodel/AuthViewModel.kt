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
import com.darkvault.app.model.VaultKeyBundle
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

            val stored = prefs.getPasswordHashAndSalt()
            val valid = stored != null && withContext(Dispatchers.Default) {
                CryptoManager.verifyPassword(password, stored.first, stored.second)
            }
            if (valid) {
                prefs.clearFailedAttempts()
                setActiveSession(password)
                onSuccess()
            } else {
                val attempts = (prefs.failedAttempts.first()) + 1
                // Exponential backoff: 30s * 2^(attempts-1), capped at 30 minutes
                val shift = minOf(attempts - 1, 30)
                val backoffMs = minOf(30_000L * (1L shl shift), 30 * 60 * 1000L)
                prefs.recordFailedAttempt(attempts, System.currentTimeMillis() + backoffMs)
                _authError.value = "Incorrect password"
            }
            _isLoading.value = false
        }
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

    private fun setActiveSession(password: String) {
        _masterPassword.value = password
        VaultSession.masterPassword = password
        autoLockJob?.cancel()
    }
}
