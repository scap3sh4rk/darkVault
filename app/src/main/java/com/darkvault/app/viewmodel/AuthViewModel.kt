package com.darkvault.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkvault.app.VaultSession
import com.darkvault.app.crypto.BiometricHelper
import com.darkvault.app.crypto.BiometricKeyManager
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.data.PreferencesManager
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
import javax.crypto.Cipher

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

    /**
     * Called after BiometricPrompt succeeds with a decryption CryptoObject.
     * The cipher decrypts the stored ciphertext to recover the master password.
     */
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

    // ── Biometric enrollment ──────────────────────────────────────────────

    /**
     * Prepares an encryption cipher; the caller shows BiometricPrompt with it.
     * On biometric success, call [completeEnrollment] with the authenticated cipher.
     */
    fun prepareEnrollCipher(): Cipher? {
        return try {
            BiometricKeyManager.getCipherForEncryption()
        } catch (e: Exception) {
            null
        }
    }

    /** Called after BiometricPrompt succeeds during enrollment (SettingsScreen). */
    fun completeEnrollment(cipher: Cipher) {
        viewModelScope.launch {
            val password = _masterPassword.value ?: return@launch
            try {
                val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
                prefs.saveBiometricCredentials(cipher.iv, encrypted)
            } catch (e: Exception) {
                // Key invalidated or cipher error — disable biometric
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
        _masterPassword.value = null
        VaultSession.masterPassword = null
        autoLockJob?.cancel()
    }

    /** Called by MainActivity when the app goes to background. */
    fun onAppBackground() {
        val minutes = autoLockMinutes.value
        if (minutes <= 0) return
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            lockVault()
        }
    }

    /** Called by MainActivity when the app returns to foreground. */
    fun onAppForeground() {
        autoLockJob?.cancel()
    }

    fun clearError() { _authError.value = null }

    // ── Settings pass-through ─────────────────────────────────────────────

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setAutoLockMinutes(minutes) }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun setActiveSession(password: String) {
        _masterPassword.value = password
        VaultSession.masterPassword = password
        autoLockJob?.cancel()
    }
}
