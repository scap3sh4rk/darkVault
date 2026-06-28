package com.darkvault.app.ui.screens

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.darkvault.app.crypto.BiometricHelper
import com.darkvault.app.crypto.BiometricKeyManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.nfc.NfcTagEvent
import com.darkvault.app.nfc.NfcTagManager
import com.darkvault.app.nfc.NfcTagType
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.VaultLogo
import com.darkvault.app.ui.components.VaultTextField
import com.darkvault.app.ui.theme.AlertAmber
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.GlassHighlight
import com.darkvault.app.ui.theme.SecureGreen
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.viewmodel.AuthState
import com.darkvault.app.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(viewModel: AuthViewModel) {
    val isLoading         by viewModel.isLoading.collectAsState()
    val authError         by viewModel.authError.collectAsState()
    val biometricEnabled  by viewModel.biometricEnabled.collectAsState()
    val authState         by viewModel.authState.collectAsState()
    val nfcEnabled        by viewModel.nfcEnabled.collectAsState()
    val nfcPinRequired    by viewModel.nfcPinRequired.collectAsState()
    val nfcError          by viewModel.nfcError.collectAsState()

    val context  = LocalContext.current
    val activity = context as FragmentActivity
    val prefs    = remember { PreferencesManager(context) }
    val scope    = rememberCoroutineScope()

    val nfcAvailable = remember { NfcTagManager.isAvailable(context) }

    // Real-time NFC tag detection banner state
    var lastTagEvent by remember { mutableStateOf<NfcTagEvent?>(null) }
    LaunchedEffect(Unit) {
        NfcTagManager.tagFlow.collect { event ->
            lastTagEvent = event
            delay(4_000L)
            lastTagEvent = null
        }
    }

    // NFC PIN entry dialog
    var nfcPinInput by remember { mutableStateOf("") }
    if (nfcPinRequired) {
        AlertDialog(
            onDismissRequest = {
                nfcPinInput = ""
                viewModel.cancelNfcPin()
            },
            containerColor = VaultSurfaceVariant,
            title = { Text("Enter NFC PIN", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your NFC unlock PIN to continue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VaultTextField(
                        value = nfcPinInput,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 8) nfcPinInput = it },
                        label = "PIN",
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nfcPinInput.isNotBlank(),
                    onClick = {
                        val pin = nfcPinInput
                        nfcPinInput = ""
                        viewModel.submitNfcPin(pin)
                    }
                ) { Text("Unlock", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { nfcPinInput = ""; viewModel.cancelNfcPin() }) { Text("Cancel") }
            }
        )
    }

    var password         by remember { mutableStateOf("") }
    var biometricError   by remember { mutableStateOf<String?>(null) }
    val lockoutUntilMs   by viewModel.lockoutUntilMs.collectAsState()
    val failedAttemptsCount by viewModel.failedAttempts.collectAsState()
    var timeLeftMs       by remember { mutableStateOf(0L) }
    val view = LocalView.current

    @Suppress("DEPRECATION")
    val signedInEmail = remember { GoogleSignIn.getLastSignedInAccount(context)?.email }

    var showRecoveryDialog    by remember { mutableStateOf(false) }
    var recoveryKey           by remember { mutableStateOf("") }
    var recoveryNewPwd        by remember { mutableStateOf("") }
    var recoveryConfirmPwd    by remember { mutableStateOf("") }
    var recoveryError         by remember { mutableStateOf<String?>(null) }
    var recoveryLoading       by remember { mutableStateOf(false) }

    var showSwitchAccountDialog by remember { mutableStateOf(false) }
    var switchSignInError       by remember { mutableStateOf<String?>(null) }

    val switchGso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    }
    @Suppress("DEPRECATION")
    val switchGoogleClient = remember { GoogleSignIn.getClient(context, switchGso) }

    @Suppress("DEPRECATION")
    val switchSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .addOnSuccessListener { account ->
                    switchSignInError = null
                    viewModel.onGoogleSignInCompleted(account)
                }
                .addOnFailureListener { e ->
                    switchSignInError = "Sign in failed: ${e.message}"
                    viewModel.navigateToSignIn()
                }
        } else {
            viewModel.navigateToSignIn()
        }
    }

    fun resetRecoveryDialog() {
        showRecoveryDialog  = false
        recoveryKey         = ""
        recoveryNewPwd      = ""
        recoveryConfirmPwd  = ""
        recoveryError       = null
        recoveryLoading     = false
    }

    val isAppLocked       = authState is AuthState.AppLocked
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) && BiometricKeyManager.keyExists() }
    val canUseBiometric   = biometricEnabled && biometricAvailable

    val biometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricError = null
                    val cipher = result.cryptoObject?.cipher ?: run {
                        biometricError = "Biometric cipher unavailable"; return
                    }
                    viewModel.unlockWithBiometricCipher(cipher)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
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
        scope.launch {
            try {
                val creds = prefs.getBiometricCredentials() ?: run {
                    biometricError = "Biometric credentials not set up"; return@launch
                }
                val cipher = try {
                    BiometricKeyManager.getCipherForDecryption(creds.first)
                } catch (e: Exception) {
                    biometricError = "Biometric unavailable: ${e.message}"; return@launch
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

    LaunchedEffect(Unit) {
        if (isAppLocked && canUseBiometric) launchBiometric()
    }
    LaunchedEffect(authError) {
        if (authError != null) password = ""
    }
    LaunchedEffect(lockoutUntilMs) {
        while (true) {
            val remaining = lockoutUntilMs - System.currentTimeMillis()
            if (remaining <= 0L) { timeLeftMs = 0L; break }
            timeLeftMs = remaining
            kotlinx.coroutines.delay(1000L)
        }
    }
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

    // ── Ambient background animations ─────────────────────────────────────────
    val bgAnim = rememberInfiniteTransition(label = "unlock_bg")
    val bgPulse by bgAnim.animateFloat(
        initialValue = 0.03f, targetValue = 0.06f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Reverse),
        label = "bg_pulse"
    )

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Ambient background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(CyanPrimary.copy(bgPulse), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.35f),
                    radius = size.minDimension * 0.55f
                )
            )
        }

        if (isAppLocked) {
            AppLockedContent(
                canUseBiometric    = canUseBiometric,
                biometricError     = biometricError,
                onBiometricTap     = { biometricError = null; launchBiometric() },
                onUsePassword      = { viewModel.revertToVaultLock() },
                nfcEnabled         = nfcEnabled && nfcAvailable,
                nfcError           = nfcError,
                onClearNfcError    = { viewModel.clearNfcError() }
            )
        } else {
            FullUnlockContent(
                password             = password,
                onPasswordChange     = { password = it; if (authError != null) viewModel.clearError() },
                authError            = authError,
                timeLeftMs           = timeLeftMs,
                failedAttemptsCount  = failedAttemptsCount,
                isLoading            = isLoading,
                signedInEmail        = signedInEmail,
                onUnlock             = { if (password.isNotBlank() && timeLeftMs == 0L) viewModel.unlock(password) },
                onShowRecovery       = { showRecoveryDialog = true },
                onShowSwitchAccount  = { showSwitchAccountDialog = true },
                switchSignInError    = switchSignInError,
                nfcEnabled           = nfcEnabled && nfcAvailable
            )
        }

        // Floating NFC tag detection banner (shows for 4 seconds after a tag scan)
        lastTagEvent?.let { event ->
            val (bannerColor, bannerText) = when (event.type) {
                NfcTagType.WRITABLE -> SecureGreen to "NFC tag detected — writable"
                NfcTagType.READONLY -> AlertAmber to "Bank card detected — read-only, PPSE+UID used"
                NfcTagType.UNKNOWN  -> MaterialTheme.colorScheme.error to "Unsupported NFC tag"
            }
            AnimatedVisibility(
                visible = lastTagEvent != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, bannerColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(bannerText, style = MaterialTheme.typography.bodySmall, color = bannerColor)
                }
            }
        }
    }

    // ── Switch account dialog ─────────────────────────────────────────────────
    if (showSwitchAccountDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountDialog = false },
            containerColor = VaultSurfaceVariant,
            title = { Text("Switch Account?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "This will remove the current account's local data from this device. " +
                    "Your encrypted files on Google Drive are safe and untouched. " +
                    "You will sign in with a different Google account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchAccountDialog = false
                    scope.launch {
                        viewModel.clearLocalDataForSwitch()
                        switchGoogleClient.signOut().addOnCompleteListener {
                            switchSignInLauncher.launch(switchGoogleClient.signInIntent)
                        }
                    }
                }) { Text("Switch Account", color = CyanPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Recovery key dialog ───────────────────────────────────────────────────
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { resetRecoveryDialog() },
            containerColor = VaultSurfaceVariant,
            title = {
                Text("Recover with Recovery Key",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
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
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !recoveryLoading,
                    onClick = {
                        when {
                            recoveryKey.isBlank()        -> { recoveryError = "Enter your recovery key"; return@TextButton }
                            recoveryNewPwd.length < 8    -> { recoveryError = "Password must be at least 8 characters"; return@TextButton }
                            recoveryNewPwd != recoveryConfirmPwd -> { recoveryError = "Passwords do not match"; return@TextButton }
                        }
                        @Suppress("DEPRECATION")
                        val account = GoogleSignIn.getLastSignedInAccount(context)
                        if (account == null) { recoveryError = "Not signed in to Google."; return@TextButton }
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
                                is AuthViewModel.PasswordChangeResult.Error   -> recoveryError = result.message
                            }
                        }
                    }
                ) {
                    if (recoveryLoading) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), color = CyanPrimary, strokeWidth = 2.dp
                    ) else Text("Recover", color = CyanPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetRecoveryDialog() }) { Text("Cancel") }
            }
        )
    }
}

// ── App-locked view (biometric-first) ─────────────────────────────────────────

@Composable
private fun AppLockedContent(
    canUseBiometric: Boolean,
    biometricError: String?,
    onBiometricTap: () -> Unit,
    onUsePassword: () -> Unit,
    nfcEnabled: Boolean = false,
    nfcError: String? = null,
    onClearNfcError: () -> Unit = {}
) {
    val anim = rememberInfiniteTransition(label = "biometric_anim")
    val outerPulse by anim.animateFloat(
        initialValue = 0.12f, targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "outer_pulse"
    )
    val innerPulse by anim.animateFloat(
        initialValue = 0.40f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "inner_pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 56.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.28f))

        VaultLogo()

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            "VAULT LOCKED",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
        )

        Spacer(modifier = Modifier.height(52.dp))

        // ── Primary unlock target — biometric ring (only if biometric is enrolled) ───
        if (canUseBiometric) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBiometricTap
                    )
            ) {
                Canvas(Modifier.size(96.dp)) {
                    drawCircle(
                        color = CyanPrimary.copy(outerPulse * 0.4f),
                        radius = size.minDimension / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Fill
                    )
                    drawCircle(
                        color = CyanPrimary.copy(outerPulse),
                        radius = size.minDimension / 2f - 1f,
                        style = Stroke(1.dp.toPx())
                    )
                }
                Canvas(Modifier.size(72.dp)) {
                    drawCircle(
                        color = CyanPrimary.copy(innerPulse),
                        radius = size.minDimension / 2f - 1f,
                        style = Stroke(1.dp.toPx())
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, Brush.radialGradient(listOf(GlassHighlight, CyanPrimary.copy(0.3f))), CircleShape)
                ) {
                    Icon(
                        Icons.Outlined.Fingerprint, "Unlock with biometric",
                        tint = CyanPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "TAP TO UNLOCK",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.5.sp),
                color = CyanPrimary.copy(0.55f)
            )

            biometricError?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(err, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }

        // ── NFC unlock prompt ──────────────────────────────────────────────────────
        if (nfcEnabled) {
            Spacer(modifier = Modifier.height(if (canUseBiometric) 28.dp else 0.dp))

            if (!canUseBiometric) {
                // NFC is the only quick-unlock method — make it the primary visual target
                Icon(
                    Icons.Outlined.Nfc,
                    contentDescription = "Tap NFC card",
                    tint = CyanPrimary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "TAP NFC CARD TO UNLOCK",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.5.sp),
                    color = CyanPrimary.copy(0.75f)
                )
            } else {
                // NFC is a secondary option alongside biometric
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "TAP NFC CARD TO UNLOCK",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = CyanPrimary.copy(0.7f)
                    )
                }
            }

            nfcError?.let { err ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(err, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                LaunchedEffect(err) {
                    delay(4_000L)
                    onClearNfcError()
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        TextButton(onClick = onUsePassword) {
            Text(
                "Use master password instead",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.weight(0.72f))
    }
}

// ── Full vault-lock view (password-first) ─────────────────────────────────────

@Composable
private fun FullUnlockContent(
    password: String,
    onPasswordChange: (String) -> Unit,
    authError: String?,
    timeLeftMs: Long,
    failedAttemptsCount: Int,
    isLoading: Boolean,
    signedInEmail: String?,
    onUnlock: () -> Unit,
    onShowRecovery: () -> Unit,
    onShowSwitchAccount: () -> Unit,
    switchSignInError: String?,
    nfcEnabled: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 56.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.38f))

        VaultLogo()

        Spacer(modifier = Modifier.height(52.dp))

        Text(
            "UNLOCK VAULT",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
        )

        // Signed-in account chip
        if (!signedInEmail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.5f), RoundedCornerShape(8.dp))
                    .semantics { disabled() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        Icons.Outlined.AccountCircle, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        signedInEmail,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(
                onClick = onShowSwitchAccount,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    "Switch account",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            switchSignInError?.let { err ->
                Text(err, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
            }
        } else {
            Spacer(modifier = Modifier.height(28.dp))
        }

        VaultTextField(
            value = password,
            onValueChange = onPasswordChange,
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

        if (failedAttemptsCount in 1..4 && timeLeftMs == 0L) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$failedAttemptsCount / 5 incorrect attempts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        CyberButton(
            text = "Unlock",
            onClick = onUnlock,
            enabled = password.isNotBlank() && timeLeftMs == 0L,
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = onShowRecovery) {
            Text(
                "Forgot password? Use recovery key",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (nfcEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "or tap your NFC card",
                style = MaterialTheme.typography.bodySmall,
                color = CyanPrimary.copy(0.4f)
            )
        }

        Spacer(modifier = Modifier.weight(0.62f))
    }
}
