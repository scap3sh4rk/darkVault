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

sealed class AuthState {
    object Init : AuthState()
    object SignIn : AuthState()
    object CheckingVault : AuthState()
    object Setup : AuthState()
    object Unlock : AuthState()
    /** DEK retained in memory; biometric gates UI only — no Drive call needed to resume. */
    object AppLocked : AuthState()
    object Home : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    /** Emits current diagnostic snapshot to DeveloperOptionsManager in debug builds. */
    private fun emitDebugDiagnostics(autoLockFireAt: Long = 0L) {
        if (!com.darkvault.app.BuildConfig.DEBUG) return
        com.darkvault.app.debug.DeveloperOptionsManager.updateAuthState(
            stateName = _authState.value::class.simpleName ?: "Unknown",
            passwordEntered = _sessionPasswordEntered,
            biometricEnrolledNow = com.darkvault.app.crypto.BiometricKeyManager.keyExists(),
            autoLockFireAt = autoLockFireAt,
            failedAttempts = failedAttempts.value,
            lockoutUntil = lockoutUntilMs.value
        )
        com.darkvault.app.debug.DeveloperOptionsManager.dekLoaded.value = com.darkvault.app.VaultSession.dek != null
    }

    // ── Auth state machine ────────────────────────────────────────────────

    private val _authState = MutableStateFlow<AuthState>(AuthState.Init)
    val authState: StateFlow<AuthState> = _authState

    @Volatile private var _pendingFolderId: String? = null
    @Volatile private var _pendingAccount: GoogleSignInAccount? = null

    /**
     * Must be called once from the NavGraph on first composition.
     * Checks the last signed-in Google account and checks Drive for an existing vault.
     */
    fun initializeAuth() {
        if (_authState.value != AuthState.Init) return
        viewModelScope.launch {
            @Suppress("DEPRECATION")
            val account = withContext(Dispatchers.IO) {
                GoogleSignIn.getLastSignedInAccount(getApplication())
            }
            if (account == null) {
                _authState.value = AuthState.SignIn
                return@launch
            }
            val email = account.email
            if (email != null) {
                withContext(Dispatchers.IO) {
                    val storedEmail = prefs.linkedAccountEmail.first()
                    if (storedEmail != null && storedEmail != email) {
                        Log.w(TAG, "Startup: account changed ($storedEmail → $email) — clearing local state")
                        clearLocalAccountState(email)
                    } else if (storedEmail == null) {
                        prefs.saveLinkedAccount(email)
                    }
                }
            }
            checkVaultOnDrive(account)
        }
    }

    /**
     * Call after Google sign-in completes successfully from SignInScreen.
     * Handles account switches and drives the vault-check flow.
     */
    fun onGoogleSignInCompleted(account: GoogleSignInAccount) {
        viewModelScope.launch {
            val email = account.email
            if (email != null) {
                withContext(Dispatchers.IO) {
                    val storedEmail = prefs.linkedAccountEmail.first()
                    if (storedEmail != null && storedEmail != email) {
                        Log.w(TAG, "New sign-in with different account ($storedEmail → $email) — clearing local state")
                        clearLocalAccountState(email)
                    } else if (storedEmail == null) {
                        prefs.saveLinkedAccount(email)
                    }
                }
            }
            checkVaultOnDrive(account)
        }
    }

    private suspend fun checkVaultOnDrive(account: GoogleSignInAccount) {
        _authState.value = AuthState.CheckingVault
        withContext(Dispatchers.IO) {
            try {
                val client = DriveApiClient(getApplication(), account)
                val savedFolderId = prefs.vaultFolderId.first()
                val folderId = client.ensureVaultFolder(savedFolderId)
                prefs.saveVaultFolderId(folderId)
                _pendingFolderId = folderId
                _pendingAccount = account

                val vaultKeyExists = client.downloadVaultKey(folderId) != null
                if (vaultKeyExists) {
                    prefs.setHasVaultKey(folderId)
                    _authState.value = AuthState.Unlock
                } else {
                    _authState.value = AuthState.Setup
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vault check failed (offline?) — using local state", e)
                _pendingFolderId = prefs.vaultKeyFolderId.first()
                val setupDone = prefs.isSetupDone.first()
                _authState.value = if (setupDone) AuthState.Unlock else AuthState.Setup
            }
        }
    }

    /**
     * Clears all local credentials and navigates back to the sign-in screen.
     * Caller is responsible for signing out of the Google client before calling this.
     */
    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.clearAll()
            BiometricKeyManager.deleteKey()
            VaultSession.clearDek()
            VaultSession.signedInAccount = null
            VaultSession.masterPassword = null
            _masterPassword.value = null
            _sessionPasswordEntered = false
            _biometricAutoLaunch.value = false
            autoLockJob?.cancel()
            sessionTimeoutJob?.cancel()
            sessionTimeoutJob = null
            _sessionExpiresAtMs = 0L
            _pendingFolderId = null
            _pendingAccount = null
            _authState.value = AuthState.SignIn
            if (com.darkvault.app.BuildConfig.DEBUG) {
                com.darkvault.app.debug.DeveloperOptionsManager.onDekCleared()
                emitDebugDiagnostics()
            }
        }
    }

    // ── Downstream state ──────────────────────────────────────────────────

    val isSetupDone: StateFlow<Boolean?> = prefs.isSetupDone.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    val biometricEnabled: StateFlow<Boolean> = prefs.biometricEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    val autoLockMinutes: StateFlow<Int> = prefs.autoLockMinutes.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )

    val sessionTimeoutMinutes: StateFlow<Int> = prefs.sessionTimeoutMinutes.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )

    val failedAttempts: StateFlow<Int> = prefs.failedAttempts.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )

    val lockoutUntilMs: StateFlow<Long> = prefs.lockoutUntilMs.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0L
    )

    /** The vault folder ID cached from Drive, or null if not yet discovered. */
    val vaultFolderId: StateFlow<String?> = prefs.vaultKeyFolderId.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    private val _masterPassword = MutableStateFlow<String?>(null)
    val masterPassword: StateFlow<String?> = _masterPassword

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _enrollCipherReady = MutableStateFlow<Cipher?>(null)
    val enrollCipherReady: StateFlow<Cipher?> = _enrollCipherReady

    val biometricAvailableOnDevice: Boolean
        get() = BiometricHelper.isAvailable(getApplication())

    private var autoLockJob: Job? = null
    private var sessionTimeoutJob: Job? = null
    // Epoch ms when the current master-password session expires (0 = no expiry).
    @Volatile private var _sessionExpiresAtMs = 0L

    private val _recoveryKey = MutableStateFlow<String?>(null)
    val recoveryKey: StateFlow<String?> = _recoveryKey

    fun clearRecoveryKey() { _recoveryKey.value = null }

    // True once the user has typed their master password in the current process lifetime.
    // Cleared only on process death or manual "Lock Now". Never persisted.
    @Volatile private var _sessionPasswordEntered = false

    // Suppresses the next onAppBackground() lock — used when launching file/folder pickers
    // so that leaving the app to a system picker doesn't trigger a lock.
    @Volatile private var _suppressNextLock = false

    private val _biometricAutoLaunch = MutableStateFlow(false)
    val biometricAutoLaunch: StateFlow<Boolean> = _biometricAutoLaunch

    fun suppressNextLock() { _suppressNextLock = true }

    // ── Setup ─────────────────────────────────────────────────────────────

    fun setup(password: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val (hash, salt) = withContext(Dispatchers.Default) {
                CryptoManager.hashPassword(password)
            }
            prefs.savePasswordHash(hash, salt)

            // Create DEK + upload vault.key immediately if Drive is available
            val folderId = _pendingFolderId
            val account = _pendingAccount
            if (folderId != null && account != null) {
                try {
                    createAndUploadDek(password, folderId, account)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create DEK during setup — will retry when online", e)
                }
            }

            _sessionPasswordEntered = true
            _biometricAutoLaunch.value = false
            setActiveSession(password)
            _authState.value = AuthState.Home
            emitDebugDiagnostics()
            _isLoading.value = false
        }
    }

    // ── Unlock ────────────────────────────────────────────────────────────

    private enum class UnlockAttemptResult {
        SUCCESS,
        WRONG_PASSWORD,
        CHANGED_ON_OTHER_DEVICE,
        NETWORK_FALLBACK
    }

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

                val stored = prefs.getPasswordHashAndSalt()
                if (stored == null || !CryptoManager.verifyPassword(password, stored.first, stored.second)) {
                    val (h, s) = CryptoManager.hashPassword(password)
                    prefs.savePasswordHash(h, s)
                    Log.d(TAG, "Local hash updated: password was changed on another device")
                }

                VaultSession.dek = dek
                if (com.darkvault.app.BuildConfig.DEBUG) {
                    com.darkvault.app.debug.DeveloperOptionsManager.onDekUnwrapped(bundle.kekSalt)
                    com.darkvault.app.debug.DeveloperOptionsManager.setVaultKeyPresent(true)
                }
                UnlockAttemptResult.SUCCESS
            } catch (e: javax.crypto.AEADBadTagException) {
                java.util.Arrays.fill(kek, 0)
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

    fun unlock(password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null

            val lockout = prefs.lockoutUntilMs.first()
            val now = System.currentTimeMillis()
            if (now < lockout) {
                val seconds = (lockout - now + 999) / 1000
                _authError.value = "Too many failed attempts. Try again in ${seconds}s."
                _isLoading.value = false
                return@launch
            }

            @Suppress("DEPRECATION")
            val account = GoogleSignIn.getLastSignedInAccount(getApplication())
            if (account != null) {
                when (tryUnlockWithVaultKey(password, account)) {
                    UnlockAttemptResult.SUCCESS -> {
                        prefs.clearFailedAttempts()
                        _sessionPasswordEntered = true
                        _biometricAutoLaunch.value = false
                        setActiveSession(password)
                        _authState.value = AuthState.Home
                        _isLoading.value = false
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
                    UnlockAttemptResult.NETWORK_FALLBACK -> { /* fall through to local hash */ }
                }
            }

            // Offline fallback: local hash
            val stored = prefs.getPasswordHashAndSalt()
            val valid = stored != null && withContext(Dispatchers.Default) {
                CryptoManager.verifyPassword(password, stored.first, stored.second)
            }
            if (valid) {
                prefs.clearFailedAttempts()
                _sessionPasswordEntered = true
                _biometricAutoLaunch.value = false
                setActiveSession(password)
                _authState.value = AuthState.Home
                emitDebugDiagnostics()
            } else {
                recordFailedAttemptAndError()
                emitDebugDiagnostics()
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

    fun unlockWithBiometricCipher(cipher: Cipher) {
        viewModelScope.launch {
            val creds = prefs.getBiometricCredentials()
            if (creds == null) {
                _authError.value = "Biometric credentials not found"
                return@launch
            }
            try {
                val password = String(cipher.doFinal(creds.second), Charsets.UTF_8)
                _biometricAutoLaunch.value = false
                // In AppLocked mode the DEK is still in memory — setActiveSession restores the
                // password reference and we go straight to Home with no Drive call needed.
                // resetSessionTimer=false: the session clock keeps running from last password entry
                // so biometric unlocks cannot postpone the session timeout indefinitely.
                setActiveSession(password, resetSessionTimer = false)
                _authState.value = AuthState.Home
            } catch (e: Exception) {
                _authError.value = "Biometric decryption failed"
            }
        }
    }

    // ── DEK helpers ───────────────────────────────────────────────────────

    private suspend fun createAndUploadDek(password: String, folderId: String, account: GoogleSignInAccount) {
        withContext(Dispatchers.IO) {
            val client = DriveApiClient(getApplication(), account)
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
            java.util.Arrays.fill(kek, 0)
            client.uploadVaultKey(bundle.toJson(), folderId)
            prefs.setHasVaultKey(folderId)
            VaultSession.dek = dek
            _recoveryKey.value = VaultKeyManager.formatRecoveryKey(recoveryKey)
            java.util.Arrays.fill(recoveryKey, 0)
            if (com.darkvault.app.BuildConfig.DEBUG) {
                com.darkvault.app.debug.DeveloperOptionsManager.onDekWrapped()
                com.darkvault.app.debug.DeveloperOptionsManager.setVaultKeyPresent(true)
            }
        }
    }

    /**
     * Loads the DEK from an existing vault.key, or creates a new one if none exists.
     * Used as an offline-fallback retry from HomeScreen when DEK was not loaded during unlock.
     */
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
                        createAndUploadDek(password, folderId, account)
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
            _biometricAutoLaunch.value = false
        }
    }

    // ── Lock / auto-lock ──────────────────────────────────────────────────

    /**
     * Lock the vault.
     * [auto]=true (background/timer): if biometric is enrolled and password was entered this
     *   session, enter AppLocked — DEK + session stay in memory, biometric gates the UI.
     *   Otherwise do a full vault lock.
     * [auto]=false (manual "Lock Now"): always full vault lock, clears session flag.
     */
    fun lockVault(auto: Boolean = false) {
        autoLockJob?.cancel()
        if (auto && _sessionPasswordEntered && biometricEnabled.value
            && BiometricHelper.isAvailable(getApplication())
            && BiometricKeyManager.keyExists()
        ) {
            // App-level lock: keep DEK + password in memory, only gate the UI
            _biometricAutoLaunch.value = true
            _authState.value = AuthState.AppLocked
            return
        }
        // Full vault lock: wipe all in-memory secrets and stop session timer
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = null
        _sessionExpiresAtMs = 0L
        VaultSession.clearDek()
        _masterPassword.value = null
        VaultSession.masterPassword = null
        _biometricAutoLaunch.value = false
        if (!auto) _sessionPasswordEntered = false
        _authState.value = AuthState.Unlock
        emitDebugDiagnostics()
    }

    /**
     * Called from UnlockScreen when user taps "Use master password instead" while in AppLocked.
     * Clears DEK so they go through the full password + Drive path.
     */
    fun revertToVaultLock() {
        autoLockJob?.cancel()
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = null
        _sessionExpiresAtMs = 0L
        VaultSession.clearDek()
        _masterPassword.value = null
        VaultSession.masterPassword = null
        _biometricAutoLaunch.value = false
        _sessionPasswordEntered = false
        _authState.value = AuthState.Unlock
    }

    fun onAppBackground() {
        if (_suppressNextLock) {
            _suppressNextLock = false
            return
        }
        if (_authState.value != AuthState.Home) return
        // Immediate app-level lock when biometric is configured and password entered this session
        if (_sessionPasswordEntered && biometricEnabled.value) {
            lockVault(auto = true)
            return
        }
        // No biometric: timed full vault lock
        val minutes = autoLockMinutes.value
        if (minutes <= 0) return
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            lockVault(auto = true)
        }
    }

    fun onAppForeground() {
        autoLockJob?.cancel()
        // Belt-and-suspenders: if the session expired while the process was frozen/backgrounded,
        // enforce the full vault lock immediately on resume.
        if (_sessionPasswordEntered && _sessionExpiresAtMs > 0
            && System.currentTimeMillis() >= _sessionExpiresAtMs
        ) {
            lockSessionExpired()
        }
    }

    fun clearError() { _authError.value = null }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setAutoLockMinutes(minutes) }
    }

    // ── Change password ───────────────────────────────────────────────────

    sealed class PasswordChangeResult {
        object Success : PasswordChangeResult()
        data class Error(val message: String) : PasswordChangeResult()
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        account: GoogleSignInAccount? = null,
        folderId: String? = null
    ): PasswordChangeResult = withContext(Dispatchers.IO) {
        val stored = prefs.getPasswordHashAndSalt()
            ?: return@withContext PasswordChangeResult.Error("No stored credentials")
        if (!CryptoManager.verifyPassword(currentPassword, stored.first, stored.second)) {
            return@withContext PasswordChangeResult.Error("Current password is incorrect")
        }

        if (account != null && folderId != null) {
            val client = DriveApiClient(getApplication(), account)
            var lastError: String? = null
            repeat(3) { attempt ->
                try {
                    val full = client.downloadVaultKeyFull(folderId)
                        ?: return@repeat
                    val oldBundle = VaultKeyBundle.fromJson(full.json)

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
                        VaultSession.dek = dek
                        lastError = null
                        return@repeat
                    }
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
                    _authState.value = AuthState.Home
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

    // ── Recovery key rotation ─────────────────────────────────────────────

    sealed class RecoveryKeyRotationResult {
        data class Success(val newFormattedKey: String) : RecoveryKeyRotationResult()
        data class Error(val message: String) : RecoveryKeyRotationResult()
    }

    suspend fun rotateRecoveryKey(
        password: String,
        account: GoogleSignInAccount,
        folderId: String
    ): RecoveryKeyRotationResult = withContext(Dispatchers.IO) {
        try {
            val client = DriveApiClient(getApplication(), account)
            var lastError = "vault.key not found on Drive"
            repeat(3) { attempt ->
                val full = client.downloadVaultKeyFull(folderId) ?: run {
                    lastError = "vault.key not found on Drive"
                    return@repeat
                }
                val bundle = VaultKeyBundle.fromJson(full.json)
                val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded
                val dek = try {
                    VaultKeyManager.unwrapDek(bundle.dekWrappedByKek, kek)
                } catch (e: javax.crypto.AEADBadTagException) {
                    java.util.Arrays.fill(kek, 0)
                    return@withContext RecoveryKeyRotationResult.Error("Password is incorrect")
                } finally {
                    java.util.Arrays.fill(kek, 0)
                }
                val newRecoveryKeyBytes = VaultKeyManager.generateRecoveryKey()
                val newBundle = VaultKeyBundle(
                    version = 1,
                    kekSalt = bundle.kekSalt,
                    dekWrappedByKek = bundle.dekWrappedByKek,
                    dekWrappedByRecovery = VaultKeyManager.wrapDek(dek, newRecoveryKeyBytes)
                )
                java.util.Arrays.fill(dek, 0)
                val updated = client.updateVaultKeyInPlace(newBundle.toJson(), full.fileId, full.modifiedTime)
                if (updated) {
                    val formattedKey = VaultKeyManager.formatRecoveryKey(newRecoveryKeyBytes)
                    java.util.Arrays.fill(newRecoveryKeyBytes, 0)
                    return@withContext RecoveryKeyRotationResult.Success(formattedKey)
                }
                Log.w(TAG, "vault.key conflict on rotation attempt $attempt — retrying")
                lastError = "vault.key was modified concurrently. Please try again."
            }
            RecoveryKeyRotationResult.Error(lastError)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate recovery key", e)
            RecoveryKeyRotationResult.Error(e.message ?: "Rotation failed")
        }
    }

    // ── Account management (internal) ─────────────────────────────────────

    private suspend fun clearLocalAccountState(newEmail: String) {
        prefs.clearAll()
        BiometricKeyManager.deleteKey()
        VaultSession.clearDek()
        VaultSession.masterPassword = null
        _masterPassword.value = null
        prefs.saveLinkedAccount(newEmail)
    }

    // [resetSessionTimer] = false for biometric re-auth — timer keeps counting from last password entry.
    private fun setActiveSession(password: String, resetSessionTimer: Boolean = true) {
        _masterPassword.value = password
        VaultSession.masterPassword = password
        autoLockJob?.cancel()
        if (resetSessionTimer) startSessionTimer()
    }

    // ── Session timeout ───────────────────────────────────────────────────

    private fun startSessionTimer(overrideMinutes: Int? = null) {
        sessionTimeoutJob?.cancel()
        val minutes = overrideMinutes ?: sessionTimeoutMinutes.value
        if (minutes <= 0) {
            _sessionExpiresAtMs = 0L
            return
        }
        _sessionExpiresAtMs = System.currentTimeMillis() + minutes * 60_000L
        sessionTimeoutJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            lockSessionExpired()
        }
    }

    private fun lockSessionExpired() {
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = null
        autoLockJob?.cancel()
        _sessionExpiresAtMs = 0L
        VaultSession.clearDek()
        _masterPassword.value = null
        VaultSession.masterPassword = null
        _biometricAutoLaunch.value = false
        _sessionPasswordEntered = false
        _authState.value = AuthState.Unlock
    }

    fun setSessionTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            prefs.setSessionTimeoutMinutes(minutes)
            if (_sessionPasswordEntered) startSessionTimer(minutes)
        }
    }
}
