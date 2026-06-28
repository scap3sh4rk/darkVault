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
import com.darkvault.app.nfc.NfcKeyHelper
import com.darkvault.app.nfc.NfcTagEvent
import com.darkvault.app.nfc.NfcTagManager
import com.darkvault.app.nfc.NfcTagType
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.Arrays
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
    /**
     * Google token call returned UserRecoverableAuthException — user signed in but
     * has not granted the drive.file scope (or consent was revoked).
     * [consentIntent] should be launched so the user can approve the scope,
     * then [retryAfterConsent] re-runs the vault check.
     */
    data class NeedsConsent(val consentIntent: android.content.Intent) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    init {
        viewModelScope.launch {
            NfcTagManager.tagFlow.collect { event ->
                if (nfcEnabled.value) handleNfcTag(event)
            }
        }
    }

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

    private val _isOffline = MutableStateFlow(false)
    /** True when the last vault check / unlock attempt fell back to local state due to no network. */
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

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
        // Set early so retryAfterConsent() can find the account even if the
        // Drive token call throws before we reach the assignment below.
        _pendingAccount = account
        withContext(Dispatchers.IO) {
            try {
                val client = DriveApiClient(getApplication(), account)
                val savedFolderId = prefs.vaultFolderId.first()
                val folderId = client.ensureVaultFolder(savedFolderId)
                prefs.saveVaultFolderId(folderId)
                _pendingFolderId = folderId

                val vaultKeyExists = client.downloadVaultKey(folderId) != null
                _isOffline.value = false
                if (vaultKeyExists) {
                    prefs.setHasVaultKey(folderId)
                    _authState.value = AuthState.Unlock
                } else {
                    _authState.value = AuthState.Setup
                }
            } catch (e: UserRecoverableAuthException) {
                // User is signed in but the drive.file scope has not been granted
                // (or was revoked). Surface the recovery intent so the UI can prompt.
                Log.w(TAG, "Drive consent missing — prompting user to grant access", e)
                val recoveryIntent = e.intent
                if (recoveryIntent != null) {
                    _authState.value = AuthState.NeedsConsent(recoveryIntent)
                } else {
                    // No recovery intent — fall back to full sign-in flow.
                    _authState.value = AuthState.SignIn
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vault check failed (offline?) — using local state", e)
                _isOffline.value = true
                _pendingFolderId = prefs.vaultKeyFolderId.first()
                val setupDone = prefs.isSetupDone.first()
                _authState.value = if (setupDone) AuthState.Unlock else AuthState.Setup
            }
        }
    }

    /**
     * Called after the user completes the Google consent screen launched from
     * [AuthState.NeedsConsent]. Re-runs the vault check with the already-known account.
     */
    fun retryAfterConsent() {
        val account = _pendingAccount ?: run {
            _authState.value = AuthState.SignIn
            return
        }
        viewModelScope.launch { checkVaultOnDrive(account) }
    }

    /**
     * Wipes all local credentials and in-memory secrets without changing [authState].
     * Used by the account-switch flow on UnlockScreen: the UI signs out of Google and launches
     * account selection itself, then calls [onGoogleSignInCompleted] with the chosen account.
     * On cancellation, call [navigateToSignIn] to land on the sign-in screen.
     */
    suspend fun clearLocalDataForSwitch() = withContext(Dispatchers.IO) {
        prefs.clearAll()  // clearAll removes NFC keys too
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
        if (com.darkvault.app.BuildConfig.DEBUG) {
            com.darkvault.app.debug.DeveloperOptionsManager.onDekCleared()
            emitDebugDiagnostics()
        }
    }

    /** Transitions to [AuthState.SignIn]. Called when an account switch is cancelled mid-flow. */
    fun navigateToSignIn() {
        _authState.value = AuthState.SignIn
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
        viewModelScope, SharingStarted.Eagerly, 5
    )

    val sessionTimeoutMinutes: StateFlow<Int> = prefs.sessionTimeoutMinutes.stateIn(
        viewModelScope, SharingStarted.Eagerly, 60
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

    val nfcEnabled: StateFlow<Boolean> = prefs.nfcEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val nfcAvailableOnDevice: Boolean
        get() = NfcTagManager.isAvailable(getApplication())

    private val _nfcError = MutableStateFlow<String?>(null)
    val nfcError: StateFlow<String?> = _nfcError

    private val _nfcPinRequired = MutableStateFlow(false)
    val nfcPinRequired: StateFlow<Boolean> = _nfcPinRequired

    // Channel used to relay the PIN from the UI to the suspended handleNfcTag coroutine
    private val _nfcPinChannel = Channel<String>(Channel.RENDEZVOUS)

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
            // Fix: HIGH-001 — derive kek then wrap its usage in try-finally so it is zeroed on
            // ALL exception paths, including IllegalBlockSizeException from malformed ciphertext.
            val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded
            try {
                val dek = VaultKeyManager.unwrapDek(bundle.dekWrappedByKek, kek)

                val stored = prefs.getPasswordHashAndSalt()
                if (stored == null || !CryptoManager.verifyPassword(password, stored.first, stored.second)) {
                    val (h, s) = CryptoManager.hashPassword(password)
                    prefs.savePasswordHash(h, s)
                    Log.d(TAG, "Local hash updated: password was changed on another device")
                }

                VaultSession.dek = dek
                prefs.cacheVaultKeyLocally(bundle.kekSalt, bundle.dekWrappedByKek)
                _isOffline.value = false
                if (com.darkvault.app.BuildConfig.DEBUG) {
                    com.darkvault.app.debug.DeveloperOptionsManager.onDekUnwrapped(bundle.kekSalt)
                    com.darkvault.app.debug.DeveloperOptionsManager.setVaultKeyPresent(true)
                }
                return@withContext UnlockAttemptResult.SUCCESS
            } catch (e: javax.crypto.AEADBadTagException) {
                val stored = prefs.getPasswordHashAndSalt()
                return@withContext if (stored != null && CryptoManager.verifyPassword(password, stored.first, stored.second)) {
                    UnlockAttemptResult.CHANGED_ON_OTHER_DEVICE
                } else {
                    UnlockAttemptResult.WRONG_PASSWORD
                }
            } finally {
                // Fix: HIGH-001 — zero KEK in all paths (success, AEADBadTagException, any other exception)
                java.util.Arrays.fill(kek, 0)
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
                    UnlockAttemptResult.NETWORK_FALLBACK -> {
                        _isOffline.value = true
                        /* fall through to local hash */
                    }
                }
            }

            // Offline fallback: local hash
            val stored = prefs.getPasswordHashAndSalt()
            val valid = stored != null && withContext(Dispatchers.Default) {
                CryptoManager.verifyPassword(password, stored.first, stored.second)
            }
            if (valid) {
                // Restore DEK from locally cached vault key bundle so offline files can be decrypted
                val cached = withContext(Dispatchers.IO) { prefs.getCachedVaultKey() }
                if (cached != null) {
                    val (kekSalt, wrappedDek) = cached
                    val kek = CryptoManager.deriveKey(password, kekSalt).encoded
                    try {
                        VaultSession.dek = VaultKeyManager.unwrapDek(wrappedDek, kek)
                    } catch (e: Exception) {
                        Log.w(TAG, "Offline DEK restore failed — cached bundle invalid or password changed", e)
                    } finally {
                        java.util.Arrays.fill(kek, 0)
                    }
                }
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
        val backoffMs = computeBackoffMs(attempts)
        val lockoutUntil = if (backoffMs > 0L) System.currentTimeMillis() + backoffMs else 0L
        prefs.recordFailedAttempt(attempts, lockoutUntil)
        _authError.value = if (attempts < 5) {
            "Incorrect password ($attempts / 5 attempts)"
        } else {
            "Incorrect password"
        }
    }

    private fun computeBackoffMs(attempts: Int): Long {
        if (attempts < 5) return 0L
        return minOf(30_000L * (1L shl (attempts - 5)), 30 * 60 * 1000L)
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
                _sessionPasswordEntered = true
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
            // Fix: LOW-001 — use nextBytes() instead of generateSeed() for cryptographic material
            val kekSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
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
                        // Fix: MEDIUM-002 — wrap kek in try-finally so it is always zeroed
                        // even when unwrapDek() throws an exception
                        val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded
                        val dek = try {
                            VaultKeyManager.unwrapDek(bundle.dekWrappedByKek, kek)
                        } finally {
                            java.util.Arrays.fill(kek, 0)
                        }
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

    // ── NFC unlock enrollment & handling ──────────────────────────────────

    sealed class NfcEnrollResult {
        object Success : NfcEnrollResult()
        data class Error(val message: String) : NfcEnrollResult()
    }

    /**
     * Enrolls an NFC tag for vault unlock. Call on Dispatchers.IO.
     * For writable tags: generates a 32-byte secret and writes it to the tag.
     * For readonly tags (bank cards): reads card identifier via PPSE APDU (not just UID).
     * Wraps both the DEK and the master password with the NFC-derived KEK.
     */
    suspend fun enrollNfc(
        tag: android.nfc.Tag,
        mode: String,
        pin: String?,
        forceReadOnly: Boolean = false
    ): NfcEnrollResult = withContext(Dispatchers.IO) {
        val dek = VaultSession.dek ?: return@withContext NfcEnrollResult.Error("Vault is locked — unlock first")
        val password = _masterPassword.value ?: return@withContext NfcEnrollResult.Error("Session expired")

        val tagType = if (forceReadOnly) NfcTagType.READONLY else NfcTagManager.classifyTag(tag)
        if (tagType == NfcTagType.UNKNOWN) return@withContext NfcEnrollResult.Error("Unsupported NFC tag type")

        val bindingSalt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        var nfcSecret: ByteArray? = null

        try {
            nfcSecret = when (tagType) {
                NfcTagType.WRITABLE -> {
                    val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    if (!NfcTagManager.writeSecret(tag, secret)) {
                        return@withContext NfcEnrollResult.Error("Failed to write secret to NFC tag. Is it writable?")
                    }
                    secret
                }
                NfcTagType.READONLY -> {
                    NfcTagManager.readCardIdentifier(tag)
                        ?: return@withContext NfcEnrollResult.Error("Failed to read card identifier")
                }
                NfcTagType.UNKNOWN -> return@withContext NfcEnrollResult.Error("Unsupported NFC tag")
            }

            val nfcKek = if (mode == "tap_pin" && !pin.isNullOrBlank()) {
                val pinBytes = pin.toByteArray(Charsets.UTF_8)
                try {
                    NfcKeyHelper.deriveKekWithPin(nfcSecret!!, bindingSalt, pinBytes)
                } finally {
                    Arrays.fill(pinBytes, 0)
                }
            } else {
                NfcKeyHelper.deriveKek(nfcSecret!!, bindingSalt)
            }

            try {
                val passwordBytes = password.toByteArray(Charsets.UTF_8)
                val (iv, ct) = try {
                    NfcKeyHelper.encryptBlob(passwordBytes, nfcKek)
                } finally {
                    Arrays.fill(passwordBytes, 0)
                }
                val wrappedDek = VaultKeyManager.wrapDek(dek, nfcKek)

                prefs.saveNfcCredentials(
                    mode = mode,
                    tagType = tagType.name.lowercase(),
                    bindingSalt = bindingSalt,
                    iv = iv,
                    ct = ct,
                    wrappedDek = wrappedDek
                )

                if (com.darkvault.app.BuildConfig.DEBUG) {
                    com.darkvault.app.debug.DeveloperOptionsManager.onNfcEnrolled(tagType.name, mode)
                }

                NfcEnrollResult.Success
            } finally {
                Arrays.fill(nfcKek, 0)
            }
        } finally {
            nfcSecret?.let { Arrays.fill(it, 0) }
            Arrays.fill(bindingSalt, 0)
        }
    }

    fun disableNfc() {
        viewModelScope.launch { prefs.clearNfcCredentials() }
        if (com.darkvault.app.BuildConfig.DEBUG) {
            com.darkvault.app.debug.DeveloperOptionsManager.onNfcCleared()
        }
    }

    fun clearNfcError() { _nfcError.value = null }

    /** Called by the UI to submit the PIN when tap+PIN mode prompts for it. */
    fun submitNfcPin(pin: String) {
        viewModelScope.launch { _nfcPinChannel.send(pin) }
    }

    /** Called by the UI if the user dismisses the PIN dialog without entering. */
    fun cancelNfcPin() {
        _nfcPinRequired.value = false
        // Send sentinel to immediately unblock the suspended receive() rather than waiting 60s
        viewModelScope.launch { _nfcPinChannel.send("") }
    }

    private suspend fun handleNfcTag(event: NfcTagEvent) {
        val state = _authState.value
        if (state != AuthState.AppLocked && state != AuthState.Unlock) return

        val now = System.currentTimeMillis()
        val lockout = prefs.lockoutUntilMs.first()
        if (now < lockout) {
            val secs = (lockout - now + 999) / 1000
            _nfcError.value = "Too many failed attempts. Try again in ${secs}s."
            return
        }

        val creds = prefs.getNfcCredentials() ?: return

        // Read the secret while the tag is still physically present — BEFORE any dialog.
        // For tap+PIN the card is removed before the user finishes typing the PIN; reading
        // after would always fail with a communication error.
        val secret: ByteArray = withContext(Dispatchers.IO) {
            // Dispatch on enrolled type, not physical type — a writable card can be enrolled as
            // "readonly" (forceReadOnly). Wrong-card detection is via AEAD failure.
            val wrongTag = when (creds.tagType) {
                "writable" -> event.type != NfcTagType.WRITABLE
                "readonly" -> event.type == NfcTagType.UNKNOWN  // allow WRITABLE or READONLY
                else -> true
            }
            if (wrongTag) {
                recordFailedAttemptAndError()
                _nfcError.value = "Wrong tag — tap the card or tag you enrolled"
                return@withContext null
            }
            when (creds.tagType) {
                "writable" -> NfcTagManager.readSecret(event.tag)
                    ?: run { _nfcError.value = "Could not read tag — hold it still and try again"; null }
                else -> NfcTagManager.readCardIdentifier(event.tag)
                    ?: run { _nfcError.value = "Could not read card — hold it still and try again"; null }
            }
        } ?: return

        var pinBytes: ByteArray? = null
        try {
            if (creds.mode == "tap_pin") {
                _nfcPinRequired.value = true
                val pinStr = withTimeoutOrNull(60_000L) { _nfcPinChannel.receive() }
                _nfcPinRequired.value = false
                // null = 60s timeout; "" = user cancelled via cancelNfcPin()
                if (pinStr.isNullOrEmpty()) return
                pinBytes = pinStr.toByteArray(Charsets.UTF_8)
            }

            withContext(Dispatchers.IO) {
                val nfcKek = try {
                    if (creds.mode == "tap_pin" && pinBytes != null) {
                        NfcKeyHelper.deriveKekWithPin(secret, creds.bindingSalt, pinBytes)
                    } else {
                        NfcKeyHelper.deriveKek(secret, creds.bindingSalt)
                    }
                } finally {
                    Arrays.fill(secret, 0)
                }

                try {
                    if (state == AuthState.Unlock) {
                        val dek = try {
                            VaultKeyManager.unwrapDek(creds.wrappedDek, nfcKek)
                        } catch (e: javax.crypto.AEADBadTagException) {
                            recordFailedAttemptAndError()
                            _nfcError.value = "Incorrect PIN or wrong tag — try again"
                            return@withContext
                        }
                        VaultSession.dek = dek
                    }

                    val passwordBytes = try {
                        NfcKeyHelper.decryptBlob(creds.iv, creds.ct, nfcKek)
                    } catch (e: javax.crypto.AEADBadTagException) {
                        if (state == AuthState.Unlock) VaultSession.clearDek()
                        recordFailedAttemptAndError()
                        _nfcError.value = "Incorrect PIN or wrong tag — try again"
                        return@withContext
                    }

                    val recoveredPassword = try {
                        String(passwordBytes, Charsets.UTF_8)
                    } finally {
                        Arrays.fill(passwordBytes, 0)
                    }

                    prefs.clearFailedAttempts()
                    _biometricAutoLaunch.value = false
                    // resetSessionTimer=false if AppLocked: session clock keeps running
                    setActiveSession(recoveredPassword, resetSessionTimer = state == AuthState.Unlock)
                    _authState.value = AuthState.Home

                    if (com.darkvault.app.BuildConfig.DEBUG) {
                        val uidPrefix = event.tag.id.take(4).joinToString("") { "%02X".format(it) }
                        com.darkvault.app.debug.DeveloperOptionsManager.onNfcTagDetected(event.type.name, uidPrefix)
                        emitDebugDiagnostics()
                    }
                } finally {
                    Arrays.fill(nfcKek, 0)
                }
            }
        } finally {
            pinBytes?.let { Arrays.fill(it, 0) }
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
        val canBiometric = biometricEnabled.value && BiometricHelper.isAvailable(getApplication()) && BiometricKeyManager.keyExists()
        val canNfc = nfcEnabled.value && NfcTagManager.isAvailable(getApplication())
        if (auto && _sessionPasswordEntered && (canBiometric || canNfc)) {
            // App-level lock: keep DEK + password in memory, only gate the UI
            _biometricAutoLaunch.value = canBiometric
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
     * Full teardown after a successful password change.
     *
     * changePassword() already zeroed VaultSession.dek and VaultSession.masterPassword on the IO
     * thread. This method handles the ViewModel-level state on Main and deletes the now-orphaned
     * Android Keystore biometric key (DataStore credentials were already cleared by changePassword).
     *
     * Must NOT call setActiveSession — the user must re-authenticate with the new password.
     */
    fun lockAfterPasswordChange() {
        // Synchronous: clear all ViewModel state on the calling thread (Main)
        autoLockJob?.cancel()
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = null
        _sessionExpiresAtMs = 0L
        // Belt-and-suspenders: also clear VaultSession here in case IO thread raced
        VaultSession.clearDek()
        VaultSession.masterPassword = null
        _masterPassword.value = null
        _biometricAutoLaunch.value = false
        _sessionPasswordEntered = false
        _authState.value = AuthState.Unlock
        // Delete the orphaned Keystore key async (DataStore creds already wiped)
        viewModelScope.launch(Dispatchers.IO) {
            BiometricKeyManager.deleteKey()
            if (com.darkvault.app.BuildConfig.DEBUG) emitDebugDiagnostics()
        }
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
        // Immediate app-level lock when biometric OR nfc is enrolled and password entered this session
        val canQuickLock = biometricEnabled.value ||
            (nfcEnabled.value && NfcTagManager.isAvailable(getApplication()))
        if (_sessionPasswordEntered && canQuickLock) {
            lockVault(auto = true)
            return
        }
        // No quick-unlock method: timed full vault lock
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

    suspend fun verifyPasswordOnly(password: String): Boolean {
        val stored = prefs.getPasswordHashAndSalt() ?: return false
        return withContext(Dispatchers.Default) {
            CryptoManager.verifyPassword(password, stored.first, stored.second)
        }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setAutoLockMinutes(minutes) }
    }

    // ── Change password ───────────────────────────────────────────────────

    sealed class PasswordChangeResult {
        object Success : PasswordChangeResult()
        data class Error(val message: String) : PasswordChangeResult()
    }

    // Drive re-key is in-flight — UI must block navigation until this clears.
    private val _passwordChangeInFlight = MutableStateFlow(false)
    val passwordChangeInFlight: StateFlow<Boolean> = _passwordChangeInFlight.asStateFlow()

    private val _passwordChangeEvent = MutableSharedFlow<PasswordChangeResult>(extraBufferCapacity = 1)
    val passwordChangeEvent: SharedFlow<PasswordChangeResult> = _passwordChangeEvent.asSharedFlow()

    /**
     * Launches [changePassword] on [viewModelScope] so the Drive re-key survives navigation
     * away from the settings screen. Result is emitted to [passwordChangeEvent].
     */
    fun launchPasswordChange(
        currentPassword: String,
        newPassword: String,
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?,
        folderId: String?
    ) {
        if (_passwordChangeInFlight.value) return
        viewModelScope.launch {
            _passwordChangeInFlight.value = true
            try {
                _passwordChangeEvent.emit(changePassword(currentPassword, newPassword, account, folderId))
            } finally {
                _passwordChangeInFlight.value = false
            }
        }
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

        // Bug 5A fix: block offline password change — vault.key cannot be re-keyed without Drive
        if (account == null || folderId == null) {
            return@withContext PasswordChangeResult.Error(
                "Cannot change password while offline — connect to the internet and try again."
            )
        }

        val client = DriveApiClient(getApplication(), account)
        var lastError: String? = null
        var succeeded = false
        for (attempt in 0 until 3) {
            try {
                val full = client.downloadVaultKeyFull(folderId) ?: continue
                val oldBundle = VaultKeyBundle.fromJson(full.json)

                val currentKek = CryptoManager.deriveKey(currentPassword, oldBundle.kekSalt).encoded
                val dek = try {
                    VaultKeyManager.unwrapDek(oldBundle.dekWrappedByKek, currentKek)
                } catch (e: javax.crypto.AEADBadTagException) {
                    java.util.Arrays.fill(currentKek, 0)
                    lastError = "Current password does not match vault.key — it may have been changed from another device"
                    break
                } finally {
                    java.util.Arrays.fill(currentKek, 0)
                }

                val newKekSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val newKek = CryptoManager.deriveKey(newPassword, newKekSalt).encoded
                // Zero newKek in finally so it's cleared even if wrapDek throws
                val wrappedByNewKek = try {
                    VaultKeyManager.wrapDek(dek, newKek)
                } finally {
                    java.util.Arrays.fill(newKek, 0)
                }
                val newBundle = VaultKeyBundle(
                    version = 1,
                    kekSalt = newKekSalt,
                    dekWrappedByKek = wrappedByNewKek,
                    dekWrappedByRecovery = oldBundle.dekWrappedByRecovery
                )

                val updated = client.updateVaultKeyInPlace(newBundle.toJson(), full.fileId, full.modifiedTime)
                if (updated) {
                    VaultSession.dek = dek
                    succeeded = true
                    break
                }
                Log.w(TAG, "vault.key conflict on attempt $attempt — retrying")
                lastError = "vault.key was modified concurrently. Please try again."
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-wrap DEK on attempt $attempt", e)
                lastError = e.message
            }
        }
        if (!succeeded) return@withContext PasswordChangeResult.Error(lastError ?: "Password change failed")

        val (newHash, newSalt) = CryptoManager.hashPassword(newPassword)
        prefs.savePasswordHash(newHash, newSalt)
        prefs.clearFailedAttempts()
        // Clear secondary unlock credentials — old encrypted-password blobs are now stale
        prefs.clearBiometricCredentials()
        prefs.clearNfcCredentials()
        // Atomically wipe in-memory secrets before returning — caller must call
        // lockAfterPasswordChange() to finish the ViewModel-level teardown on Main.
        VaultSession.clearDek()
        VaultSession.masterPassword = null
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

                val newKekSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val newKek = CryptoManager.deriveKey(newPassword, newKekSalt).encoded
                val wrappedByNewKek = try {
                    VaultKeyManager.wrapDek(dek, newKek)
                } finally {
                    java.util.Arrays.fill(newKek, 0)
                }
                val newBundle = VaultKeyBundle(
                    version = 1,
                    kekSalt = newKekSalt,
                    dekWrappedByKek = wrappedByNewKek,
                    dekWrappedByRecovery = bundle.dekWrappedByRecovery
                )

                val updated = client.updateVaultKeyInPlace(newBundle.toJson(), full.fileId, full.modifiedTime)
                if (updated) {
                    java.util.Arrays.fill(recoveryKeyBytes, 0)
                    val (newHash, newSalt) = CryptoManager.hashPassword(newPassword)
                    prefs.savePasswordHash(newHash, newSalt)
                    prefs.setHasVaultKey(folderId)
                    prefs.clearFailedAttempts()
                    // Secondary unlock blobs contain the old password — clear before setting new session
                    prefs.clearBiometricCredentials()
                    prefs.clearNfcCredentials()
                    BiometricKeyManager.deleteKey()
                    if (com.darkvault.app.BuildConfig.DEBUG) {
                        com.darkvault.app.debug.DeveloperOptionsManager.onNfcCleared()
                    }
                    VaultSession.dek = dek
                    _sessionPasswordEntered = true
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
                // Zero dek in finally so it's cleared even if wrapDek throws
                val wrappedByNewRecovery = try {
                    VaultKeyManager.wrapDek(dek, newRecoveryKeyBytes)
                } finally {
                    java.util.Arrays.fill(dek, 0)
                }
                val newBundle = VaultKeyBundle(
                    version = 1,
                    kekSalt = bundle.kekSalt,
                    dekWrappedByKek = bundle.dekWrappedByKek,
                    dekWrappedByRecovery = wrappedByNewRecovery
                )
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
