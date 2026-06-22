package com.darkvault.app.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.darkvault.app.crypto.BiometricHelper
import com.darkvault.app.crypto.BiometricKeyManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.VaultLogo
import com.darkvault.app.ui.components.VaultTextField
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.viewmodel.AuthState
import com.darkvault.app.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(viewModel: AuthViewModel) {
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val authState by viewModel.authState.collectAsState()

    val context = LocalContext.current
    val activity = context as FragmentActivity
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }
    val lockoutUntilMs by viewModel.lockoutUntilMs.collectAsState()
    val failedAttemptsCount by viewModel.failedAttempts.collectAsState()
    var timeLeftMs by remember { mutableStateOf(0L) }
    val view = LocalView.current

    // Task 1 — signed-in email chip
    @Suppress("DEPRECATION")
    val signedInEmail = remember { GoogleSignIn.getLastSignedInAccount(context)?.email }

    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryKey by remember { mutableStateOf("") }
    var recoveryNewPwd by remember { mutableStateOf("") }
    var recoveryConfirmPwd by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var recoveryLoading by remember { mutableStateOf(false) }

    fun resetRecoveryDialog() {
        showRecoveryDialog = false
        recoveryKey = ""
        recoveryNewPwd = ""
        recoveryConfirmPwd = ""
        recoveryError = null
        recoveryLoading = false
    }

    // AppLocked = DEK is retained in memory, biometric just gates the UI
    val isAppLocked = authState is AuthState.AppLocked
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) && BiometricKeyManager.keyExists() }
    val canUseBiometric = biometricEnabled && biometricAvailable

    val biometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricError = null
                    val cipher = result.cryptoObject?.cipher ?: run {
                        biometricError = "Biometric cipher unavailable"
                        return
                    }
                    viewModel.unlockWithBiometricCipher(cipher)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        biometricError = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    biometricError = "Fingerprint not recognized"
                }
            }
        )
    }

    // Fix: MEDIUM-003 — replace runBlocking (blocks main thread, ANR risk) with a coroutine
    // launched in rememberCoroutineScope() so the DataStore read is non-blocking.
    fun launchBiometric() {
        scope.launch {
            try {
                val creds = prefs.getBiometricCredentials() ?: run {
                    biometricError = "Biometric credentials not set up"
                    return@launch
                }
                val cipher = try {
                    BiometricKeyManager.getCipherForDecryption(creds.first)
                } catch (e: Exception) {
                    biometricError = "Biometric unavailable: ${e.message}"
                    return@launch
                }
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock darkVault")
                    .setSubtitle("Use your fingerprint to unlock")
                    .setNegativeButtonText("Use password")
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build()
                biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            } catch (e: Exception) {
                biometricError = "Biometric unavailable: ${e.message}"
            }
        }
    }

    // Auto-launch biometric on entry when in AppLocked mode
    LaunchedEffect(Unit) {
        if (isAppLocked && canUseBiometric) launchBiometric()
    }

    LaunchedEffect(authError) {
        if (authError != null) password = ""
    }

    // Live lockout countdown
    LaunchedEffect(lockoutUntilMs) {
        while (true) {
            val remaining = lockoutUntilMs - System.currentTimeMillis()
            if (remaining <= 0L) { timeLeftMs = 0L; break }
            timeLeftMs = remaining
            kotlinx.coroutines.delay(1000L)
        }
    }

    // Haptic feedback on successful unlock
    LaunchedEffect(authState) {
        if (authState is AuthState.Home) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                @Suppress("DEPRECATION")
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    if (isAppLocked) {
        // ── App-level lock: biometric-first UI ────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 28.dp, vertical = 56.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            VaultLogo()

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                "darkVault is locked",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(56.dp))

            IconButton(
                onClick = { biometricError = null; launchBiometric() },
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    Icons.Outlined.Fingerprint,
                    contentDescription = "Unlock with biometric",
                    tint = CyanPrimary,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Tap to unlock with fingerprint",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            biometricError?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            TextButton(onClick = { viewModel.revertToVaultLock() }) {
                Text(
                    "Use master password instead",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(0.7f))
        }
    } else {
        // ── Full vault lock: password-first UI ────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 28.dp, vertical = 56.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.4f))

            VaultLogo()

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                "Unlock Vault",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Task 1 — email chip (only shown in full vault-lock mode, not AppLocked)
            if (!signedInEmail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VaultSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .semantics { disabled() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            signedInEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }

            VaultTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (authError != null) viewModel.clearError()
                },
                label = "Master password",
                isPassword = true,
                isError = authError != null || timeLeftMs > 0,
                errorMessage = when {
                    timeLeftMs > 0 -> {
                        val minutes = timeLeftMs / 60000
                        val seconds = (timeLeftMs % 60000) / 1000
                        "Too many attempts. Try again in $minutes:${seconds.toString().padStart(2, '0')}"
                    }
                    else -> authError
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Show attempt counter when in 1-4 range
            if (failedAttemptsCount in 1..4 && timeLeftMs == 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$failedAttemptsCount / 5 incorrect attempts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            CyberButton(
                text = "Unlock",
                onClick = { if (password.isNotBlank() && timeLeftMs == 0L) viewModel.unlock(password) },
                enabled = password.isNotBlank() && timeLeftMs == 0L,
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = { showRecoveryDialog = true }) {
                Text(
                    "Forgot password? Use recovery key",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(0.6f))
        }
    }

    // ── Recovery key dialog ───────────────────────────────────────────────────

    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { resetRecoveryDialog() },
            containerColor = VaultSurfaceVariant,
            title = {
                Text(
                    "Recover with Recovery Key",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter the recovery key shown when you first set up darkVault, then choose a new master password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VaultTextField(
                        value = recoveryKey,
                        onValueChange = { recoveryKey = it; recoveryError = null },
                        label = "Recovery key (XXXX-XXXX-... format)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    VaultTextField(
                        value = recoveryNewPwd,
                        onValueChange = { recoveryNewPwd = it; recoveryError = null },
                        label = "New master password",
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    VaultTextField(
                        value = recoveryConfirmPwd,
                        onValueChange = { recoveryConfirmPwd = it; recoveryError = null },
                        label = "Confirm new password",
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    recoveryError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !recoveryLoading,
                    onClick = {
                        when {
                            recoveryKey.isBlank() -> { recoveryError = "Enter your recovery key"; return@TextButton }
                            recoveryNewPwd.length < 8 -> { recoveryError = "Password must be at least 8 characters"; return@TextButton }
                            recoveryNewPwd != recoveryConfirmPwd -> { recoveryError = "Passwords do not match"; return@TextButton }
                        }
                        @Suppress("DEPRECATION")
                        val account = GoogleSignIn.getLastSignedInAccount(context)
                        if (account == null) {
                            recoveryError = "Not signed in to Google."
                            return@TextButton
                        }
                        recoveryLoading = true
                        scope.launch {
                            val folderId = prefs.vaultKeyFolderId.first()
                            if (folderId == null) {
                                recoveryError = "No vault folder found."
                                recoveryLoading = false
                                return@launch
                            }
                            val result = viewModel.recoverWithRecoveryKey(
                                recoveryKey.trim(), recoveryNewPwd, account, folderId
                            )
                            recoveryLoading = false
                            when (result) {
                                is AuthViewModel.PasswordChangeResult.Success -> resetRecoveryDialog()
                                is AuthViewModel.PasswordChangeResult.Error -> recoveryError = result.message
                            }
                        }
                    }
                ) {
                    if (recoveryLoading) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = CyanPrimary,
                        strokeWidth = 2.dp
                    )
                    else Text("Recover", color = CyanPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetRecoveryDialog() }) { Text("Cancel") }
            }
        )
    }
}
