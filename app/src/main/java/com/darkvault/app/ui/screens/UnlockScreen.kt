package com.darkvault.app.ui.screens

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.darkvault.app.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(viewModel: AuthViewModel, onUnlocked: () -> Unit) {
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()

    val context = LocalContext.current
    val activity = context as FragmentActivity
    val prefs = remember { PreferencesManager(context) }

    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }

    // Recovery key dialog state
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

    val biometricAvailable = remember { BiometricHelper.isAvailable(context) && BiometricKeyManager.keyExists() }
    val showBiometric = biometricEnabled && biometricAvailable

    // Build the BiometricPrompt with a decryption CryptoObject
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
                    viewModel.unlockWithBiometricCipher(cipher) { onUnlocked() }
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

    fun launchBiometric() {
        try {
            val creds = runCatching {
                kotlinx.coroutines.runBlocking { prefs.getBiometricCredentials() }
            }.getOrNull() ?: run {
                biometricError = "Biometric credentials not set up"
                return
            }
            val cipher = BiometricKeyManager.getCipherForDecryption(creds.first)
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

    LaunchedEffect(authError) {
        if (authError != null) password = ""
    }

    // Auto-launch biometric on first composition (covers normal unlock + post-auto-lock re-navigation)
    LaunchedEffect(Unit) {
        if (showBiometric) launchBiometric()
    }

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

        Spacer(modifier = Modifier.height(32.dp))

        VaultTextField(
            value = password,
            onValueChange = {
                password = it
                if (authError != null) viewModel.clearError()
            },
            label = "Master password",
            isPassword = true,
            isError = authError != null,
            errorMessage = authError,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        CyberButton(
            text = "Unlock",
            onClick = { if (password.isNotBlank()) viewModel.unlock(password) { onUnlocked() } },
            enabled = password.isNotBlank(),
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        if (showBiometric) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { launchBiometric() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use fingerprint", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }

            biometricError?.let { err ->
                Spacer(Modifier.height(4.dp))
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = { showRecoveryDialog = true }) {
            Text("Forgot password? Use recovery key", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.weight(0.6f))
    }

    // Recovery key dialog
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { resetRecoveryDialog() },
            containerColor = VaultSurfaceVariant,
            title = { Text("Recover with Recovery Key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
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
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                        val account = GoogleSignIn.getLastSignedInAccount(context)
                        if (account == null) {
                            recoveryError = "Not signed in to Google. Sign in via the Home screen first."
                            return@TextButton
                        }
                        recoveryLoading = true
                        scope.launch {
                            val folderId = prefs.vaultKeyFolderId.first()
                            if (folderId == null) {
                                recoveryError = "No vault folder found. Make sure you've connected Drive before."
                                recoveryLoading = false
                                return@launch
                            }
                            val result = viewModel.recoverWithRecoveryKey(recoveryKey.trim(), recoveryNewPwd, account, folderId)
                            recoveryLoading = false
                            when (result) {
                                is AuthViewModel.PasswordChangeResult.Success -> {
                                    resetRecoveryDialog()
                                    onUnlocked()
                                }
                                is AuthViewModel.PasswordChangeResult.Error -> {
                                    recoveryError = result.message
                                }
                            }
                        }
                    }
                ) {
                    if (recoveryLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyanPrimary, strokeWidth = 2.dp)
                    else Text("Recover", color = CyanPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetRecoveryDialog() }) { Text("Cancel") }
            }
        )
    }
}
