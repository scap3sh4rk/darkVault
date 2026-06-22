package com.darkvault.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.VaultLogo
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.viewmodel.AuthState
import com.darkvault.app.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

@Suppress("DEPRECATION")
@Composable
fun SignInScreen(viewModel: AuthViewModel) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()

    var signInError by remember { mutableStateOf<String?>(null) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    // Primary sign-in launcher (Google account chooser + initial consent)
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .addOnSuccessListener { account ->
                    signInError = null
                    viewModel.onGoogleSignInCompleted(account)
                }
                .addOnFailureListener { e ->
                    signInError = "Sign in failed: ${e.message}"
                }
        } else {
            // User dismissed the account chooser or denied the initial consent.
            signInError = "Sign in cancelled. Please try again and grant Drive access when prompted."
        }
    }

    // Consent-recovery launcher — used when the drive.file scope was not granted
    // and Google has given us a recovery intent via UserRecoverableAuthException.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            signInError = null
            // Scope granted — retry the vault check with the already-known account.
            viewModel.retryAfterConsent()
        } else {
            // User dismissed the consent screen again — stay on this screen and explain.
            signInError = "Drive access is required. darkVault cannot work without it."
        }
    }

    // Auto-launch the consent screen as soon as we enter NeedsConsent state.
    // The user gets one automatic prompt; if they cancel, we show the button.
    LaunchedEffect(authState) {
        if (authState is AuthState.NeedsConsent) {
            consentLauncher.launch((authState as AuthState.NeedsConsent).consentIntent)
        }
    }

    val isChecking = authState == AuthState.Init || authState == AuthState.CheckingVault
    val needsConsent = authState is AuthState.NeedsConsent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 56.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.35f))

        VaultLogo()

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "darkVault",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Encrypted personal backup",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(56.dp))

        when {
            isChecking -> {
                CircularProgressIndicator(
                    color = CyanPrimary,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(36.dp)
                )
                if (authState == AuthState.CheckingVault) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Checking your vault…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            needsConsent -> {
                // Drive scope not granted — show a clear explanation and a retry button.
                // (The consent screen was already auto-launched; this is the fallback UI
                // shown if the user dismissed it or if the auto-launch is pending.)
                ConsentRequiredBanner()

                Spacer(modifier = Modifier.height(24.dp))

                CyberButton(
                    text = "Grant Drive Access",
                    onClick = {
                        signInError = null
                        consentLauncher.launch(
                            (authState as AuthState.NeedsConsent).consentIntent
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary option: sign in with a different account entirely.
                CyberButton(
                    text = "Use a different account",
                    onClick = {
                        signInError = null
                        signInLauncher.launch(googleClient.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                signInError?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                CyberButton(
                    text = "Sign in with Google",
                    onClick = {
                        signInError = null
                        signInLauncher.launch(googleClient.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                signInError?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.65f))

        Text(
            "AES-256-GCM · Drive-backed · Zero-knowledge",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun ConsentRequiredBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp).padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(
                    "Drive access required",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "darkVault stores your encrypted files in Google Drive. " +
                        "Please grant access when prompted — without it the app cannot function.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
