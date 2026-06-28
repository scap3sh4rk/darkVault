package com.darkvault.app.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.darkvault.app.BuildConfig
import com.darkvault.app.R
import com.darkvault.app.crypto.BiometricHelper
import com.darkvault.app.crypto.BiometricKeyManager
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.ui.components.StorageInfoCard
import com.darkvault.app.ui.components.VaultTextField
import android.content.Intent
import android.net.Uri
import com.darkvault.app.nfc.NfcTagEvent
import com.darkvault.app.nfc.NfcTagManager
import com.darkvault.app.nfc.NfcTagType
import com.darkvault.app.ui.theme.AlertAmber
import com.darkvault.app.ui.theme.AppFont
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.SecureGreen
import com.darkvault.app.ui.theme.VaultBackground
import com.darkvault.app.ui.theme.VaultError
import com.darkvault.app.ui.theme.VaultOutline
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.viewmodel.AuthViewModel
import com.darkvault.app.viewmodel.HomeViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import android.os.Build
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel? = null,
    password: String? = null,
    account: GoogleSignInAccount? = null,
    onNavigateToDebugPanel: () -> Unit = {},
    onPasswordChanged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val prefs = remember { PreferencesManager(context) }
    val clipboardManager = LocalClipboardManager.current

    val currentAccount = account ?: remember { GoogleSignIn.getLastSignedInAccount(context) }
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    // Switch account dialog state
    var showSwitchAccountDialog by remember { mutableStateOf(false) }
    var switchAccountPassword by remember { mutableStateOf("") }
    var switchAccountError by remember { mutableStateOf<String?>(null) }
    var switchAccountLoading by remember { mutableStateOf(false) }
    var switchAccountAttempts by remember { mutableStateOf(0) }
    val switchAccountMaxAttempts = 5

    fun resetSwitchDialog() {
        showSwitchAccountDialog = false
        switchAccountPassword = ""
        switchAccountError = null
        switchAccountLoading = false
        switchAccountAttempts = 0
    }

    // Rotate recovery key dialog state
    var showRotateKeyDialog by remember { mutableStateOf(false) }
    var rotateKeyPassword by remember { mutableStateOf("") }
    var rotateKeyError by remember { mutableStateOf<String?>(null) }
    var rotateKeyLoading by remember { mutableStateOf(false) }
    var rotatedKeyToShow by remember { mutableStateOf<String?>(null) }

    fun resetRotateDialog() {
        showRotateKeyDialog = false
        rotateKeyPassword = ""
        rotateKeyError = null
        rotateKeyLoading = false
    }

    val biometricEnabled by authViewModel.biometricEnabled.collectAsState()
    val nfcEnabled by authViewModel.nfcEnabled.collectAsState()
    val nfcHardwarePresent = remember { NfcTagManager.isHardwarePresent(context) }
    val nfcAvailable = remember { NfcTagManager.isAvailable(context) }
    val autoLockMinutes by authViewModel.autoLockMinutes.collectAsState()
    val sessionTimeoutMinutes by authViewModel.sessionTimeoutMinutes.collectAsState()
    val imagePreview by prefs.imagePreviewEnabled.collectAsState(initial = true)
    val videoPreview by prefs.videoPreviewEnabled.collectAsState(initial = false)
    val thumbnailsEnabled by prefs.thumbnailsEnabled.collectAsState(initial = true)
    val themeMode by prefs.themeMode.collectAsState(initial = "SYSTEM")
    val viewLayoutPref by prefs.viewLayout.collectAsState(initial = "LIST")
    val fontKey by prefs.appFont.collectAsState(initial = "inter")
    val failedAttempts by authViewModel.failedAttempts.collectAsState()
    val lockoutUntilMs by authViewModel.lockoutUntilMs.collectAsState()
    val cacheCap by prefs.cacheCap.collectAsState(initial = 500)
    // storageInfo is accessed inline via homeViewModel below (avoid conditional collectAsState)

    var localCacheUsedBytes by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        localCacheUsedBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.darkvault.app.cache.LocalVaultCache.usedBytes(context)
        }
    }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheCapExpanded by remember { mutableStateOf(false) }

    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }

    // NFC enrollment dialog state — step machine: scan → confirm_write → disclaimer → choose_mode
    var showNfcEnrollDialog by remember { mutableStateOf(false) }
    var nfcEnrollStep by remember { mutableStateOf("scan") }
    var nfcEnrollTagEvent by remember { mutableStateOf<NfcTagEvent?>(null) }
    var nfcEnrollForceReadOnly by remember { mutableStateOf(false) }
    var nfcDisclaimerChecked by remember { mutableStateOf(false) }
    var nfcDisclaimerCountdown by remember { mutableStateOf(5) }
    var nfcEnrollMode by remember { mutableStateOf("tap_only") }
    var nfcEnrollPin by remember { mutableStateOf("") }
    var nfcEnrollError by remember { mutableStateOf<String?>(null) }
    var nfcEnrollLoading by remember { mutableStateOf(false) }
    var showNfcRemoveDialog by remember { mutableStateOf(false) }
    var nfcRemovePassword by remember { mutableStateOf("") }
    var nfcRemoveError by remember { mutableStateOf<String?>(null) }

    fun resetNfcEnrollDialog() {
        showNfcEnrollDialog = false
        nfcEnrollStep = "scan"
        nfcEnrollTagEvent = null
        nfcEnrollForceReadOnly = false
        nfcDisclaimerChecked = false
        nfcDisclaimerCountdown = 5
        nfcEnrollMode = "tap_only"
        nfcEnrollPin = ""
        nfcEnrollError = null
        nfcEnrollLoading = false
    }

    // Collect NFC tags for initial type detection and final enrollment write
    LaunchedEffect(showNfcEnrollDialog, nfcEnrollStep) {
        if (!showNfcEnrollDialog) return@LaunchedEffect
        when (nfcEnrollStep) {
            "scan" -> NfcTagManager.tagFlow.collect { event ->
                if (nfcEnrollStep != "scan") return@collect
                when (event.type) {
                    NfcTagType.WRITABLE -> { nfcEnrollTagEvent = event; nfcEnrollStep = "confirm_write" }
                    NfcTagType.READONLY -> { nfcEnrollTagEvent = event; nfcEnrollStep = "choose_mode" }
                    NfcTagType.UNKNOWN -> nfcEnrollError = "Unsupported tag type — try a different card"
                }
            }
            // Card must be physically present for writeSecret / readCardIdentifier — re-tap here
            "tap_to_enroll" -> NfcTagManager.tagFlow.collect { event ->
                if (nfcEnrollStep != "tap_to_enroll") return@collect
                nfcEnrollLoading = true
                val result = withContext(Dispatchers.IO) {
                    authViewModel.enrollNfc(
                        tag = event.tag,
                        mode = nfcEnrollMode,
                        pin = if (nfcEnrollMode == "tap_pin") nfcEnrollPin else null,
                        forceReadOnly = nfcEnrollForceReadOnly
                    )
                }
                nfcEnrollLoading = false
                when (result) {
                    is AuthViewModel.NfcEnrollResult.Success -> {
                        resetNfcEnrollDialog()
                        scope.launch { snackbarHost.showSnackbar("NFC unlock enrolled") }
                    }
                    is AuthViewModel.NfcEnrollResult.Error -> {
                        nfcEnrollError = result.message
                        nfcEnrollStep = "choose_mode"
                    }
                }
            }
        }
    }
    // Countdown timer for the write disclaimer — Accept is locked for 5 s
    LaunchedEffect(nfcEnrollStep) {
        if (nfcEnrollStep != "disclaimer") return@LaunchedEffect
        nfcDisclaimerChecked = false
        nfcDisclaimerCountdown = 5
        repeat(5) { delay(1000); nfcDisclaimerCountdown-- }
    }

    var autoLockExpanded by remember { mutableStateOf(false) }
    var sessionTimeoutExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var layoutExpanded by remember { mutableStateOf(false) }
    var fontExpanded by remember { mutableStateOf(false) }
    var showCustomTimeoutDialog by remember { mutableStateOf(false) }
    var customTimeoutInput by remember { mutableStateOf("") }
    var customTimeoutError by remember { mutableStateOf<String?>(null) }

    val autoLockOptions = listOf(0 to "Never", 1 to "1 minute", 5 to "5 minutes", 15 to "15 minutes", 30 to "30 minutes")
    val sessionTimeoutFixedOptions = listOf(0, 30, 60, 120, 240, 480, 1440)

    fun formatSessionTimeout(minutes: Int): String = when (minutes) {
        0 -> "Never"
        30 -> "30 min"
        60 -> "1 hour"
        120 -> "2 hours"
        240 -> "4 hours"
        480 -> "8 hours"
        1440 -> "24 hours"
        else -> if (minutes % 60 == 0) "${minutes / 60} hr" else "$minutes min"
    }

    fun themeModeLabel(mode: String) = when (mode) {
        "DARK" -> "Dark"
        "LIGHT" -> "Light"
        else -> "System default"
    }

    fun layoutLabel(layout: String) = when (layout) {
        "GRID2" -> "Grid (2 col)"
        "GRID3" -> "Grid (3 col)"
        else -> "List"
    }

    // Lockout status helper
    val now = System.currentTimeMillis()
    fun formatTime(ms: Long): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("HH:mm").format(ldt)
        } else {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
            "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        }
    }

    // Change password dialog state
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var changePwdCurrent by remember { mutableStateOf("") }
    var changePwdNew by remember { mutableStateOf("") }
    var changePwdConfirm by remember { mutableStateOf("") }
    var changePwdError by remember { mutableStateOf<String?>(null) }
    // Source of truth for loading state is the ViewModel (survives navigation)
    val changePwdLoading by authViewModel.passwordChangeInFlight.collectAsState()

    fun resetChangePwdDialog() {
        showChangePasswordDialog = false
        changePwdCurrent = ""
        changePwdNew = ""
        changePwdConfirm = ""
        changePwdError = null
    }

    // Swallow back press while Drive re-key is in progress — cancelling mid-write leaves
    // Drive and local credentials inconsistent.
    BackHandler(enabled = changePwdLoading) { /* intentionally blocked */ }

    // Receive success/error from viewModelScope launch
    LaunchedEffect(Unit) {
        authViewModel.passwordChangeEvent.collect { result ->
            when (result) {
                is AuthViewModel.PasswordChangeResult.Success -> {
                    resetChangePwdDialog()
                    snackbarHost.showSnackbar("Password changed. Re-enter your new password to continue.")
                    authViewModel.lockAfterPasswordChange()
                }
                is AuthViewModel.PasswordChangeResult.Error -> {
                    changePwdError = result.message
                }
            }
        }
    }

    val devOptionsEnabled by prefs.devOptionsEnabled.collectAsState(initial = false)

    // Biometric prompt for enrollment
    val biometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher ?: return
                    authViewModel.completeEnrollment(cipher)
                    scope.launch { snackbarHost.showSnackbar("Biometric app lock enabled") }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        scope.launch { snackbarHost.showSnackbar("Biometric error: $errString") }
                    }
                }
                override fun onAuthenticationFailed() {
                    scope.launch { snackbarHost.showSnackbar("Biometric not recognized") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── DEVELOPER INFO card ───────────────────────────────────────────

            fun openUrl(url: String) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.icon),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "darkVault",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "developed with code & coffee",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        "by scap3sh4rk",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Portfolio
                        IconButton(onClick = { openUrl("https://parthivkumarnikku.github.io/portfolio/") }) {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = "Portfolio",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // Email
                        IconButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:scap3sh4rk+darkvault@gmail.com")
                                }
                            )
                        }) {
                            Icon(
                                Icons.Outlined.Email,
                                contentDescription = "Email",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // GitHub profile
                        IconButton(onClick = { openUrl("https://github.com/scap3sh4rk") }) {
                            Icon(
                                Icons.Outlined.Code,
                                contentDescription = "GitHub",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // LinkedIn
                        IconButton(onClick = { openUrl("https://www.linkedin.com/in/parthivkumarnikku") }) {
                            Icon(
                                Icons.Outlined.Work,
                                contentDescription = "LinkedIn",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // GitHub Issues / Feedback
                        IconButton(onClick = { openUrl("https://github.com/scap3sh4rk/darkVault/issues") }) {
                            Icon(
                                Icons.Outlined.BugReport,
                                contentDescription = "Report issue",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Portfolio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("Email",     style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("GitHub",   style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("LinkedIn", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("Feedback", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── APPEARANCE section (NEW — Task 8, 10) ─────────────────────────

            SectionHeader("Appearance")

            SettingsCard {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))

                // Theme row
                SettingRow(
                    icon = { Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Theme",
                    subtitle = themeModeLabel(themeMode)
                ) {
                    ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = it }) {
                        OutlinedTextField(
                            value = themeModeLabel(themeMode),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(themeExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.52f)
                        )
                        ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                            listOf("SYSTEM" to "System default", "DARK" to "Dark", "LIGHT" to "Light").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (themeMode == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { scope.launch { prefs.setThemeMode(value) }; themeExpanded = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Default layout row
                SettingRow(
                    icon = { Icon(Icons.Outlined.GridView, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Default layout",
                    subtitle = layoutLabel(viewLayoutPref)
                ) {
                    ExposedDropdownMenuBox(expanded = layoutExpanded, onExpandedChange = { layoutExpanded = it }) {
                        OutlinedTextField(
                            value = layoutLabel(viewLayoutPref),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(layoutExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.52f)
                        )
                        ExposedDropdownMenu(expanded = layoutExpanded, onDismissRequest = { layoutExpanded = false }) {
                            listOf("LIST" to "List", "GRID2" to "Grid (2 col)", "GRID3" to "Grid (3 col)").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (viewLayoutPref == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { scope.launch { prefs.setViewLayout(value) }; layoutExpanded = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Font row
                val currentFont = AppFont.entries.firstOrNull { it.key == fontKey } ?: AppFont.INTER
                SettingRow(
                    icon = { Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Font",
                    subtitle = currentFont.label
                ) {
                    ExposedDropdownMenuBox(expanded = fontExpanded, onExpandedChange = { fontExpanded = it }) {
                        OutlinedTextField(
                            value = currentFont.label,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fontExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.52f)
                        )
                        ExposedDropdownMenu(expanded = fontExpanded, onDismissRequest = { fontExpanded = false }) {
                            AppFont.entries.forEach { font ->
                                DropdownMenuItem(
                                    text = { Text(font.label, color = if (fontKey == font.key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { scope.launch { prefs.setAppFont(font.key) }; fontExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── SECURITY section ───────────────────────────────────────────────

            SectionHeader("Security")

            SettingsCard {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))

                // Biometric toggle
                SettingRow(
                    icon = { Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Biometric app lock",
                    subtitle = if (!biometricAvailable) "Not available on this device"
                    else if (biometricEnabled) "App locks on background, fingerprint to resume"
                    else "Lock app on background, use fingerprint to resume"
                ) {
                    Switch(
                        checked = biometricEnabled,
                        enabled = biometricAvailable,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val cipher = authViewModel.prepareEnrollCipher()
                                if (cipher == null) {
                                    scope.launch { snackbarHost.showSnackbar("Failed to prepare biometric key") }
                                    return@Switch
                                }
                                val info = BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Enable biometric app lock")
                                    .setSubtitle("Authenticate to enable fingerprint / face lock")
                                    .setNegativeButtonText("Cancel")
                                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                                    .build()
                                biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
                            } else {
                                authViewModel.disableBiometric()
                                scope.launch { snackbarHost.showSnackbar("Biometric app lock disabled") }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // NFC unlock row — status only; enrollment and removal are in Developer options
                val nfcSubtitle = when {
                    !nfcHardwarePresent -> "Not available on this device"
                    !nfcAvailable -> "Enable NFC in system settings to use this feature"
                    nfcEnabled -> "Enrolled — tap card or tag to unlock"
                    else -> "Enable Developer options to set up NFC unlock"
                }
                val nfcIconTint = when {
                    !nfcHardwarePresent || !nfcAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    nfcEnabled -> SecureGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                SettingRow(
                    icon = { Icon(Icons.Outlined.Security, null, tint = nfcIconTint, modifier = Modifier.size(22.dp)) },
                    title = "NFC unlock",
                    subtitle = nfcSubtitle
                ) {}

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Auto-lock
                SettingRow(
                    icon = { Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Auto-lock",
                    subtitle = "Full vault lock after N min in background (when biometric is off)"
                ) {
                    ExposedDropdownMenuBox(expanded = autoLockExpanded, onExpandedChange = { autoLockExpanded = it }) {
                        OutlinedTextField(
                            value = autoLockOptions.find { it.first == autoLockMinutes }?.second ?: "Never",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(autoLockExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.5f)
                        )
                        ExposedDropdownMenu(expanded = autoLockExpanded, onDismissRequest = { autoLockExpanded = false }) {
                            autoLockOptions.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { authViewModel.setAutoLockMinutes(minutes); autoLockExpanded = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Session timeout
                SettingRow(
                    icon = { Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Session timeout",
                    subtitle = "Force master password re-entry after this long, regardless of biometric"
                ) {
                    ExposedDropdownMenuBox(expanded = sessionTimeoutExpanded, onExpandedChange = { sessionTimeoutExpanded = it }) {
                        OutlinedTextField(
                            value = formatSessionTimeout(sessionTimeoutMinutes),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sessionTimeoutExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.5f)
                        )
                        ExposedDropdownMenu(expanded = sessionTimeoutExpanded, onDismissRequest = { sessionTimeoutExpanded = false }) {
                            sessionTimeoutFixedOptions.forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text(formatSessionTimeout(minutes)) },
                                    onClick = { authViewModel.setSessionTimeoutMinutes(minutes); sessionTimeoutExpanded = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Custom…") },
                                onClick = {
                                    sessionTimeoutExpanded = false
                                    customTimeoutInput = if (sessionTimeoutMinutes !in sessionTimeoutFixedOptions && sessionTimeoutMinutes > 0)
                                        sessionTimeoutMinutes.toString() else ""
                                    customTimeoutError = null
                                    showCustomTimeoutDialog = true
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Lockout status row (NEW — Task 10)
                val lockoutSubtitle = when {
                    lockoutUntilMs > now -> {
                        "Locked until ${formatTime(lockoutUntilMs)}"
                    }
                    failedAttempts > 0 -> "$failedAttempts failed attempt(s)"
                    else -> "No failed attempts"
                }
                val lockoutColor = when {
                    lockoutUntilMs > now -> VaultError
                    failedAttempts > 0 -> VaultError.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                SettingRow(
                    icon = { Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Brute-force protection",
                    subtitle = lockoutSubtitle,
                    subtitleColor = lockoutColor
                ) {}

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Lock now
                SettingRow(
                    icon = { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp)) },
                    title = "Lock vault now",
                    subtitle = "Requires master password on next open"
                ) {
                    TextButton(onClick = { authViewModel.lockVault() }) {
                        Text("Lock", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── PASSWORD section ───────────────────────────────────────────────

            SectionHeader("Password")

            SettingsCard {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))

                SettingRow(
                    icon = { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Change password",
                    subtitle = "Update master password and re-key vault"
                ) {
                    TextButton(onClick = { showChangePasswordDialog = true }) {
                        Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                SettingRow(
                    icon = { Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Recovery key",
                    subtitle = "Rotate your offline vault recovery key"
                ) {
                    TextButton(onClick = { showRotateKeyDialog = true }) {
                        Text("Rotate", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── FILES & STORAGE section (NEW — Task 10) ────────────────────────

            SectionHeader("Files & Storage")

            SettingsCard {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))

                // Export local backup
                SettingRow(
                    icon = { Icon(Icons.Outlined.FolderZip, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Export local backup",
                    subtitle = "Decrypt all vault files to Downloads/darkVault-loc/"
                ) {
                    TextButton(onClick = {
                        val pwd = password
                        val acc = currentAccount
                        val vm = homeViewModel
                        if (pwd == null || vm == null) {
                            scope.launch { snackbarHost.showSnackbar("Vault is locked — unlock first") }
                        } else if (acc != null) {
                            vm.exportVaultBackup(pwd, acc)
                            scope.launch { snackbarHost.showSnackbar("Export started") }
                        }
                    }) {
                        Text("Export", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Image previews
                SettingRow(
                    icon = { Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Image previews",
                    subtitle = "Tap to preview encrypted images in-memory"
                ) {
                    Switch(
                        checked = imagePreview,
                        onCheckedChange = { scope.launch { prefs.setImagePreview(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Video previews
                SettingRow(
                    icon = { Icon(Icons.Outlined.VideoFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Video previews",
                    subtitle = "Show play option for encrypted videos (downloads full file)"
                ) {
                    Switch(
                        checked = videoPreview,
                        onCheckedChange = { scope.launch { prefs.setVideoPreview(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Show thumbnails (NEW)
                SettingRow(
                    icon = { Icon(Icons.Outlined.ImageSearch, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Show thumbnails",
                    subtitle = "Decrypt file thumbnails in list view (uses more Drive data)"
                ) {
                    Switch(
                        checked = thumbnailsEnabled,
                        onCheckedChange = { scope.launch { prefs.setThumbnailsEnabled(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Local cache cap
                SettingRow(
                    icon = { Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Local cache limit",
                    subtitle = "Max encrypted data cached on device"
                ) {
                    val capOptions = listOf(100 to "100 MB", 250 to "250 MB", 500 to "500 MB", 1000 to "1 GB", 2000 to "2 GB", 5000 to "5 GB")
                    ExposedDropdownMenuBox(expanded = cacheCapExpanded, onExpandedChange = { cacheCapExpanded = it }) {
                        OutlinedTextField(
                            value = capOptions.firstOrNull { it.first == cacheCap }?.second ?: "${cacheCap} MB",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cacheCapExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.52f)
                        )
                        ExposedDropdownMenu(expanded = cacheCapExpanded, onDismissRequest = { cacheCapExpanded = false }) {
                            capOptions.forEach { (mb, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (cacheCap == mb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { scope.launch { prefs.setCacheCap(mb) }; cacheCapExpanded = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Clear local cache
                SettingRow(
                    icon = { Icon(Icons.Outlined.FolderZip, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Clear local cache",
                    subtitle = "On-device: ${com.darkvault.app.ui.components.formatSize(localCacheUsedBytes)}"
                ) {
                    TextButton(onClick = { showClearCacheDialog = true }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Storage quota
                val si = homeViewModel?.storageInfo?.collectAsState()?.value
                val isCalculating = homeViewModel?.isCalculatingStorage?.collectAsState()?.value ?: false
                SettingRow(
                    icon = { Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Storage quota",
                    subtitle = if (si == null) "Tap Calculate to measure vault size" else ""
                ) {
                    if (isCalculating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick = { homeViewModel?.calculateVaultSize(currentAccount ?: return@TextButton) },
                            enabled = currentAccount != null
                        ) {
                            Text("Calculate", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                si?.let { info ->
                    StorageInfoCard(
                        usedByVault = info.usedByVaultBytes,
                        driveTotalUsed = info.driveTotalUsedBytes,
                        driveLimit = info.driveLimitBytes,
                        modifier = Modifier.padding(start = 50.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── ACCOUNT section ────────────────────────────────────────────────

            SectionHeader("Account")

            SettingsCard {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))

                SettingRow(
                    icon = { Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                    title = "Signed in as",
                    subtitle = currentAccount?.email ?: "No account"
                ) {}

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                SettingRow(
                    icon = { Icon(Icons.Outlined.SwitchAccount, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp)) },
                    title = "Switch account",
                    subtitle = "Sign out and connect a different Google account"
                ) {
                    TextButton(onClick = { showSwitchAccountDialog = true }) {
                        Text("Switch", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── DEVELOPER section ──────────────────────────────────────────────

            Spacer(Modifier.height(16.dp))

            SectionHeader("Developer")

            SettingsCard {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))

                SettingRow(
                    icon = {
                        Icon(
                            Icons.Outlined.Security,
                            null,
                            tint = if (devOptionsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Developer mode",
                    subtitle = if (devOptionsEnabled) "On" else "Off"
                ) {
                    Switch(
                        checked = devOptionsEnabled,
                        onCheckedChange = {
                            scope.launch { prefs.setDevOptionsEnabled(!devOptionsEnabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                if (devOptionsEnabled) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

                    // NFC enrollment — always shown when dev mode is on
                    val devNfcSubtitle = when {
                        !nfcHardwarePresent -> "NFC hardware not detected on this device"
                        !nfcAvailable -> "Enable NFC in system settings first"
                        nfcEnabled -> "Enrolled — tap card or tag to unlock"
                        else -> "Tap only (quick) or Tap + PIN (two-factor)"
                    }
                    SettingRow(
                        icon = { Icon(Icons.Outlined.Security, null, tint = if (nfcEnabled) SecureGreen else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                        title = "NFC unlock",
                        subtitle = devNfcSubtitle
                    ) {
                        when {
                            !nfcHardwarePresent || !nfcAvailable -> { /* no action */ }
                            nfcEnabled -> TextButton(onClick = { showNfcRemoveDialog = true }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                            else -> TextButton(onClick = { showNfcEnrollDialog = true }) {
                                Text("Set up", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

                    if (BuildConfig.DEBUG) {
                        SettingRow(
                            icon = { Icon(Icons.Outlined.BugReport, null, tint = VaultError, modifier = Modifier.size(22.dp)) },
                            title = "Developer options",
                            subtitle = "Diagnostics, fault injection, log viewer"
                        ) {
                            IconButton(onClick = onNavigateToDebugPanel) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Dialogs ────────────────────────────────────────────────────────

            if (showChangePasswordDialog) {
                AlertDialog(
                    // Blocked while Drive re-key is in progress — prevents the interruptible
                    // state where Drive and local credentials diverge.
                    onDismissRequest = { if (!changePwdLoading) resetChangePwdDialog() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Change Password", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            VaultTextField(value = changePwdCurrent, onValueChange = { changePwdCurrent = it; changePwdError = null }, label = "Current password", isPassword = true, modifier = Modifier.fillMaxWidth())
                            VaultTextField(value = changePwdNew, onValueChange = { changePwdNew = it; changePwdError = null }, label = "New password", isPassword = true, modifier = Modifier.fillMaxWidth())
                            VaultTextField(value = changePwdConfirm, onValueChange = { changePwdConfirm = it; changePwdError = null }, label = "Confirm new password", isPassword = true, modifier = Modifier.fillMaxWidth())
                            if (changePwdLoading) {
                                Text("Updating vault key on Drive — do not close the app…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            changePwdError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    confirmButton = {
                        TextButton(enabled = !changePwdLoading, onClick = {
                            when {
                                changePwdCurrent.isBlank() -> { changePwdError = "Enter current password"; return@TextButton }
                                changePwdNew.length < 8 -> { changePwdError = "New password must be at least 8 characters"; return@TextButton }
                                changePwdNew != changePwdConfirm -> { changePwdError = "Passwords do not match"; return@TextButton }
                            }
                            scope.launch {
                                val acc = GoogleSignIn.getLastSignedInAccount(context)
                                val folderId = prefs.vaultKeyFolderId.first()
                                // Run in viewModelScope — result arrives via passwordChangeEvent
                                authViewModel.launchPasswordChange(changePwdCurrent, changePwdNew, acc, folderId)
                            }
                        }) {
                            if (changePwdLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            else Text("Change", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = { TextButton(enabled = !changePwdLoading, onClick = { resetChangePwdDialog() }) { Text("Cancel") } }
                )
            }

            if (showSwitchAccountDialog) {
                AlertDialog(
                    onDismissRequest = { resetSwitchDialog() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Switch Account?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("All local vault credentials will be cleared. Enter your master password to confirm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            VaultTextField(value = switchAccountPassword, onValueChange = { switchAccountPassword = it; switchAccountError = null }, label = "Master password", isPassword = true, modifier = Modifier.fillMaxWidth())
                            switchAccountError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    confirmButton = {
                        val tooManyAttempts = switchAccountAttempts >= switchAccountMaxAttempts
                        TextButton(enabled = !switchAccountLoading && !tooManyAttempts, onClick = {
                            if (switchAccountPassword.isBlank()) { switchAccountError = "Enter your master password"; return@TextButton }
                            switchAccountLoading = true
                            scope.launch {
                                val stored = prefs.getPasswordHashAndSalt()
                                val valid = stored != null && withContext(Dispatchers.Default) {
                                    CryptoManager.verifyPassword(switchAccountPassword, stored.first, stored.second)
                                }
                                if (valid) {
                                    resetSwitchDialog()
                                    googleClient.signOut().addOnCompleteListener { authViewModel.signOut() }
                                } else {
                                    switchAccountAttempts++
                                    switchAccountError = if (switchAccountAttempts >= switchAccountMaxAttempts) "Too many attempts. Try again later." else "Incorrect password"
                                    switchAccountLoading = false
                                }
                            }
                        }) {
                            if (switchAccountLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            else Text("Switch", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { resetSwitchDialog() }) { Text("Cancel") } }
                )
            }

            if (showRotateKeyDialog) {
                AlertDialog(
                    onDismissRequest = { resetRotateDialog() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Rotate Recovery Key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("A new recovery key will be generated and the old one invalidated. Enter your master password to continue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            VaultTextField(value = rotateKeyPassword, onValueChange = { rotateKeyPassword = it; rotateKeyError = null }, label = "Master password", isPassword = true, modifier = Modifier.fillMaxWidth())
                            rotateKeyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    confirmButton = {
                        TextButton(enabled = !rotateKeyLoading, onClick = {
                            if (rotateKeyPassword.isBlank()) { rotateKeyError = "Enter your master password"; return@TextButton }
                            rotateKeyLoading = true
                            scope.launch {
                                val acc = currentAccount
                                val folderId = prefs.vaultKeyFolderId.first()
                                if (acc == null || folderId == null) { rotateKeyError = "Not connected to Drive"; rotateKeyLoading = false; return@launch }
                                when (val result = authViewModel.rotateRecoveryKey(rotateKeyPassword, acc, folderId)) {
                                    is AuthViewModel.RecoveryKeyRotationResult.Success -> { resetRotateDialog(); rotatedKeyToShow = result.newFormattedKey }
                                    is AuthViewModel.RecoveryKeyRotationResult.Error -> { rotateKeyError = result.message; rotateKeyLoading = false }
                                }
                            }
                        }) {
                            if (rotateKeyLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            else Text("Rotate", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = { TextButton(onClick = { resetRotateDialog() }) { Text("Cancel") } }
                )
            }

            rotatedKeyToShow?.let { key ->
                AlertDialog(
                    onDismissRequest = { /* force acknowledgement */ },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("New Recovery Key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Your old recovery key is now invalid. Write down the new key and store it safely.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(key, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = TextUnit(1.5f, TextUnitType.Sp)), color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = { TextButton(onClick = { rotatedKeyToShow = null }) { Text("I have saved it", color = MaterialTheme.colorScheme.primary) } },
                    dismissButton = {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(key))
                            scope.launch {
                                snackbarHost.showSnackbar("Recovery key copied to clipboard")
                                delay(60_000L)
                                clipboardManager.setText(AnnotatedString(""))
                            }
                        }) { Text("Copy", color = MaterialTheme.colorScheme.primary) }
                    }
                )
            }

            if (showClearCacheDialog) {
                AlertDialog(
                    onDismissRequest = { showClearCacheDialog = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Clear Local Cache?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Text(
                            "All locally cached encrypted files and folder listings will be deleted. Files remain on Google Drive — they'll be re-downloaded when accessed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            homeViewModel?.clearLocalCache()
                            localCacheUsedBytes = 0L
                            showClearCacheDialog = false
                            scope.launch { snackbarHost.showSnackbar("Local cache cleared") }
                        }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") } }
                )
            }

            // ── NFC Enrollment Dialog (4-step) ─────────────────────────────────────
            // Steps: scan → confirm_write (writable only) → disclaimer → choose_mode
            if (showNfcEnrollDialog) {
                AlertDialog(
                    onDismissRequest = { if (!nfcEnrollLoading) resetNfcEnrollDialog() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = {
                        Text(
                            when (nfcEnrollStep) {
                                "confirm_write"  -> "Writable Card Detected"
                                "disclaimer"     -> "Before Writing to Your Card"
                                "choose_mode"    -> "Choose Unlock Mode"
                                "tap_to_enroll"  -> "Tap Your Card"
                                else             -> "Set Up NFC Unlock"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (nfcEnrollStep) {

                                // ── Step 1: detect card type ──────────────────────
                                "scan" -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyanPrimary)
                                        Text("Hold your card or tag near the NFC sensor…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    nfcEnrollError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                }

                                // ── Step 5: re-tap to actually write / read ────────
                                "tap_to_enroll" -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyanPrimary)
                                        Text(
                                            if (nfcEnrollLoading) "Writing to card…"
                                            else "Tap your card or tag again to complete enrollment…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    nfcEnrollError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                }

                                // ── Step 2: writable card — ask permission ────────
                                "confirm_write" -> {
                                    Text(
                                        "This card supports writing. darkVault can write a unique random secret onto it — more secure than relying on the card's public identifier alone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "⚠  If this card is used as a college ID, office badge, transit card, hotel key, or any card with another purpose — writing to it may interfere with those functions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AlertAmber
                                    )
                                }

                                // ── Step 3: write disclaimer + checkbox + countdown ─
                                "disclaimer" -> {
                                    Column(
                                        modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "darkVault will write a 32-byte random secret to an NDEF record on this card.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "What this means:\n" +
                                                "• Any existing NDEF data on this card will be permanently overwritten.\n" +
                                                "• On memory-limited tags (NTAG213: 137 B, NTAG215: 504 B) the darkVault secret occupies the available space.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Risks:\n" +
                                                "• College IDs, office badges, transit cards, hotel keys, and gym cards often rely on NDEF or contactless data. Writing may render them unusable for their original purpose.\n" +
                                                "• If write-protect is already active, enrollment fails safely — your card stays unchanged.\n" +
                                                "• In rare cases a card may become corrupted during the write.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AlertAmber
                                        )
                                        Text(
                                            "Responsibility: darkVault and its developers provide this feature with no warranty. You are solely responsible for any consequences including loss of access, replacement costs, or damage. Only use cards or tags dedicated to darkVault. Blank NTAG213 / NTAG215 tags are ideal.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Checkbox(checked = nfcDisclaimerChecked, onCheckedChange = { nfcDisclaimerChecked = it })
                                        Text(
                                            "I understand that writing to this card may affect its other functions and I accept full responsibility.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // ── Step 4: choose unlock mode ────────────────────
                                else -> {
                                    val cardLabel = when {
                                        nfcEnrollTagEvent?.type == NfcTagType.READONLY -> "Read-only card — card identifier used"
                                        nfcEnrollForceReadOnly -> "Writable card — using read-only mode"
                                        else -> "Writable card — secret key written"
                                    }
                                    Text(cardLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Unlock mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { nfcEnrollMode = "tap_only" },
                                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                                contentColor = if (nfcEnrollMode == "tap_only") AlertAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) { Text("Quick Access") }
                                        TextButton(
                                            onClick = { nfcEnrollMode = "tap_pin" },
                                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                                contentColor = if (nfcEnrollMode == "tap_pin") SecureGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) { Text("NFC + PIN") }
                                    }
                                    if (nfcEnrollMode == "tap_only") {
                                        Text(
                                            "Quick Access: losing this card means anyone who finds it can unlock your vault on this device.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AlertAmber
                                        )
                                    } else {
                                        Text(
                                            "NFC + PIN: both the physical card and PIN are required. Genuinely two-factor.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SecureGreen
                                        )
                                        VaultTextField(
                                            value = nfcEnrollPin,
                                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 8) nfcEnrollPin = it },
                                            label = "NFC PIN (4–8 digits)",
                                            isPassword = true,
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    nfcEnrollError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                    if (nfcEnrollLoading) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Text("Enrolling…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        when (nfcEnrollStep) {
                            "scan", "tap_to_enroll" -> { /* no confirm — waiting for tap */ }
                            "confirm_write" -> TextButton(onClick = { nfcEnrollStep = "disclaimer" }) {
                                Text("Write to Card →", color = MaterialTheme.colorScheme.primary)
                            }
                            "disclaimer" -> TextButton(
                                enabled = nfcDisclaimerChecked && nfcDisclaimerCountdown == 0,
                                onClick = { nfcEnrollStep = "choose_mode" }
                            ) {
                                Text(
                                    if (nfcDisclaimerCountdown > 0) "Accept (${nfcDisclaimerCountdown}s)" else "Accept",
                                    color = if (nfcDisclaimerChecked && nfcDisclaimerCountdown == 0)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> TextButton( // choose_mode → tap_to_enroll
                                enabled = !nfcEnrollLoading && (nfcEnrollMode == "tap_only" || nfcEnrollPin.length >= 4),
                                onClick = { nfcEnrollError = null; nfcEnrollStep = "tap_to_enroll" }
                            ) { Text("Next →", color = MaterialTheme.colorScheme.primary) }
                        }
                    },
                    dismissButton = {
                        when (nfcEnrollStep) {
                            "scan" -> TextButton(onClick = { resetNfcEnrollDialog() }) { Text("Cancel") }
                            "tap_to_enroll" -> TextButton(
                                enabled = !nfcEnrollLoading,
                                onClick = { nfcEnrollStep = "choose_mode" }
                            ) { Text("Back") }
                            "confirm_write", "disclaimer" -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { resetNfcEnrollDialog() }) { Text("Cancel") }
                                TextButton(onClick = { nfcEnrollForceReadOnly = true; nfcEnrollStep = "choose_mode" }) {
                                    Text("Read Only", color = CyanPrimary)
                                }
                            }
                            else -> TextButton( // choose_mode → back
                                enabled = !nfcEnrollLoading,
                                onClick = {
                                    if (nfcEnrollTagEvent?.type == NfcTagType.WRITABLE) {
                                        nfcEnrollForceReadOnly = false
                                        nfcEnrollStep = "confirm_write"
                                    } else {
                                        resetNfcEnrollDialog()
                                    }
                                }
                            ) { Text(if (nfcEnrollTagEvent?.type == NfcTagType.WRITABLE) "Back" else "Cancel") }
                        }
                    }
                )
            }

            // ── NFC Remove Dialog ──────────────────────────────────────────────────
            if (showNfcRemoveDialog) {
                AlertDialog(
                    onDismissRequest = { showNfcRemoveDialog = false; nfcRemovePassword = ""; nfcRemoveError = null },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Remove NFC Unlock?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Enter your master password to confirm removal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            VaultTextField(value = nfcRemovePassword, onValueChange = { nfcRemovePassword = it; nfcRemoveError = null }, label = "Master password", isPassword = true, modifier = Modifier.fillMaxWidth())
                            nfcRemoveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (nfcRemovePassword.isBlank()) { nfcRemoveError = "Enter your master password"; return@TextButton }
                            scope.launch {
                                if (authViewModel.verifyPasswordOnly(nfcRemovePassword)) {
                                    authViewModel.disableNfc()
                                    showNfcRemoveDialog = false
                                    nfcRemovePassword = ""
                                    snackbarHost.showSnackbar("NFC unlock removed")
                                } else {
                                    nfcRemoveError = "Incorrect password"
                                }
                            }
                        }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { showNfcRemoveDialog = false; nfcRemovePassword = ""; nfcRemoveError = null }) { Text("Cancel") } }
                )
            }

            if (showCustomTimeoutDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomTimeoutDialog = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Custom session timeout", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Enter how many minutes before the master password is required again. Minimum 1 minute.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = customTimeoutInput,
                                onValueChange = { customTimeoutInput = it.filter { c -> c.isDigit() }; customTimeoutError = null },
                                label = { Text("Minutes") },
                                placeholder = { Text("e.g. 90  =  1 h 30 min") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedLabelColor = CyanPrimary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            customTimeoutError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val mins = customTimeoutInput.toIntOrNull()
                            when {
                                mins == null || mins < 1 -> customTimeoutError = "Enter a value of at least 1 minute"
                                mins > 10080 -> customTimeoutError = "Maximum is 10 080 minutes (7 days)"
                                else -> { authViewModel.setSessionTimeoutMinutes(mins); showCustomTimeoutDialog = false }
                            }
                        }) { Text("Set", color = MaterialTheme.colorScheme.primary) }
                    },
                    dismissButton = { TextButton(onClick = { showCustomTimeoutDialog = false }) { Text("Cancel") } }
                )
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultSurfaceVariant, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
internal fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    subtitleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    action: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        icon()
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (subtitleColor == androidx.compose.ui.graphics.Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else subtitleColor
                )
            }
        }
        action()
    }
}
