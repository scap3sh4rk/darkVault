package com.darkvault.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkvault.app.VaultSession
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.crypto.VaultKeyManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.drive.DriveApiClient
import com.darkvault.app.model.VaultKeyBundle
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

private const val TAG = "AuthViewModel"

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    val isSetupDone: StateFlow<Boolean> = prefs.isSetupDone.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false
    )

    private val _masterPassword = MutableStateFlow<String?>(null)
    val masterPassword: StateFlow<String?> = _masterPassword

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * Emits the formatted recovery key string exactly once after first-time vault key creation.
     * The UI must display this to the user and then call [clearRecoveryKey].
     */
    private val _recoveryKey = MutableStateFlow<String?>(null)
    val recoveryKey: StateFlow<String?> = _recoveryKey

    fun clearRecoveryKey() {
        _recoveryKey.value = null
    }

    fun setup(password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val (hash, salt) = withContext(Dispatchers.Default) {
                CryptoManager.hashPassword(password)
            }
            prefs.savePasswordHash(hash, salt)
            _masterPassword.value = password
            _isLoading.value = false
            onDone()
        }
    }

    fun unlock(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            val stored = prefs.getPasswordHashAndSalt()
            val valid = if (stored != null) {
                withContext(Dispatchers.Default) {
                    CryptoManager.verifyPassword(password, stored.first, stored.second)
                }
            } else false

            if (valid) {
                _masterPassword.value = password
                onSuccess()
            } else {
                _authError.value = "Incorrect password"
            }
            _isLoading.value = false
        }
    }

    /**
     * Loads (or creates on first use) the vault DEK from Drive and stores it in [VaultSession.dek].
     *
     * Must be called after a successful unlock when the user's Google account is available.
     * On first call (no vault.key on Drive): generates a fresh DEK + Recovery Key, wraps both,
     * uploads vault.key, and emits the formatted recovery key via [recoveryKey] for the UI to show.
     *
     * On subsequent calls: downloads vault.key, re-derives the KEK from the master password + kekSalt,
     * and unwraps the DEK.
     *
     * If Drive is unavailable or the operation fails, the error is logged and the DEK stays null —
     * the app falls back to per-file password derivation for old files.
     *
     * @param password  The verified master password.
     * @param folderId  The darkVault Drive folder ID.
     * @param account   The signed-in Google account used to call the Drive API.
     */
    fun loadOrCreateDek(password: String, folderId: String, account: GoogleSignInAccount) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val client = DriveApiClient(getApplication(), account)
                    val existingJson = client.downloadVaultKey(folderId)

                    if (existingJson != null) {
                        // Existing vault.key: re-derive KEK and unwrap DEK
                        val bundle = VaultKeyBundle.fromJson(existingJson)
                        val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded
                        val dek = VaultKeyManager.unwrapDek(bundle.dekWrappedByKek, kek)
                        VaultSession.dek = dek
                        Log.d(TAG, "DEK loaded from existing vault.key")
                    } else {
                        // First time: generate DEK + Recovery Key, wrap both, upload vault.key
                        val dek = VaultKeyManager.generateDek()
                        val recoveryKey = VaultKeyManager.generateRecoveryKey()
                        val kekSalt = SecureRandom().generateSeed(16)
                        val kek = CryptoManager.deriveKey(password, kekSalt).encoded

                        val wrappedByKek = VaultKeyManager.wrapDek(dek, kek)
                        val wrappedByRecovery = VaultKeyManager.wrapDek(dek, recoveryKey)

                        val bundle = VaultKeyBundle(
                            version = 1,
                            kekSalt = kekSalt,
                            dekWrappedByKek = wrappedByKek,
                            dekWrappedByRecovery = wrappedByRecovery
                        )

                        client.uploadVaultKey(bundle.toJson(), folderId)
                        prefs.setHasVaultKey(folderId)

                        // Store DEK in session
                        VaultSession.dek = dek

                        // Emit formatted recovery key for the UI to display once
                        _recoveryKey.value = VaultKeyManager.formatRecoveryKey(recoveryKey)

                        // Zero out local recovery key bytes — they're now in the bundle on Drive
                        java.util.Arrays.fill(recoveryKey, 0)
                        java.util.Arrays.fill(kek, 0)

                        Log.d(TAG, "New DEK created and vault.key uploaded")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load/create DEK — falling back to per-file derivation", e)
                    // DEK stays null; old files still decrypt via per-file password derivation
                }
            }
        }
    }

    fun clearError() { _authError.value = null }

    fun lockVault() {
        VaultSession.clearDek()
        _masterPassword.value = null
    }
}
