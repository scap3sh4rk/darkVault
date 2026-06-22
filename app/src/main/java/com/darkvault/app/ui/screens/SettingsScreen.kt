package com.darkvault.app.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.darkvault.app.BuildConfig
import com.darkvault.app.crypto.BiometricHelper
import com.darkvault.app.crypto.BiometricKeyManager
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.ui.components.StorageInfoCard
import com.darkvault.app.ui.components.VaultTextField
import com.darkvault.app.ui.theme.CyanPrimary
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
    val autoLockMinutes by authViewModel.autoLockMinutes.collectAsState()
    val sessionTimeoutMinutes by authViewModel.sessionTimeoutMinutes.collectAsState()
    val imagePreview by prefs.imagePreviewEnabled.collectAsState(initial = true)
    val videoPreview by prefs.videoPreviewEnabled.collectAsState(initial = false)
    val thumbnailsEnabled by prefs.thumbnailsEnabled.collectAsState(initial = true)
    val themeMode by prefs.themeMode.collectAsState(initial = "SYSTEM")
    val viewLayoutPref by prefs.viewLayout.collectAsState(initial = "LIST")
    val failedAttempts by authViewModel.failedAttempts.collectAsState()
    val lockoutUntilMs by authViewModel.lockoutUntilMs.collectAsState()
    // storageInfo is accessed inline via homeViewModel below (avoid conditional collectAsState)

    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    var autoLockExpanded by remember { mutableStateOf(false) }
    var sessionTimeoutExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var layoutExpanded by remember { mutableStateOf(false) }
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
    var changePwdLoading by remember { mutableStateOf(false) }

    fun resetChangePwdDialog() {
        showChangePasswordDialog = false
        changePwdCurrent = ""
        changePwdNew = ""
        changePwdConfirm = ""
        changePwdError = null
        changePwdLoading = false
    }

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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = CyanPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBackground)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = VaultBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── APPEARANCE section (NEW — Task 8, 10) ─────────────────────────

            SectionHeader("Appearance")

            SettingsCard {
                HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))

                // Theme row
                SettingRow(
                    icon = { Icon(Icons.Outlined.Palette, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
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
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = VaultOutline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.52f)
                        )
                        ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                            listOf("SYSTEM" to "System default", "DARK" to "Dark", "LIGHT" to "Light").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (themeMode == value) CyanPrimary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { scope.launch { prefs.setThemeMode(value) }; themeExpanded = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                // Default layout row
                SettingRow(
                    icon = { Icon(Icons.Outlined.GridView, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
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
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = VaultOutline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.52f)
                        )
                        ExposedDropdownMenu(expanded = layoutExpanded, onDismissRequest = { layoutExpanded = false }) {
                            listOf("LIST" to "List", "GRID2" to "Grid (2 col)", "GRID3" to "Grid (3 col)").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (viewLayoutPref == value) CyanPrimary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { scope.launch { prefs.setViewLayout(value) }; layoutExpanded = false }
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
                HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))

                // Biometric toggle
                SettingRow(
                    icon = { Icon(Icons.Outlined.Fingerprint, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
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
                            checkedThumbColor = CyanPrimary,
                            checkedTrackColor = CyanPrimary.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                // Auto-lock
                SettingRow(
                    icon = { Icon(Icons.Outlined.Timer, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
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
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = VaultOutline,
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

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                // Session timeout
                SettingRow(
                    icon = { Icon(Icons.Outlined.Timer, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
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
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = VaultOutline,
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

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

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
                    icon = { Icon(Icons.Outlined.Security, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Brute-force protection",
                    subtitle = lockoutSubtitle,
                    subtitleColor = lockoutColor
                ) {}

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

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
                HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))

                SettingRow(
                    icon = { Icon(Icons.Outlined.Lock, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Change password",
                    subtitle = "Update master password and re-key vault"
                ) {
                    TextButton(onClick = { showChangePasswordDialog = true }) {
                        Text("Change", color = CyanPrimary, style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                SettingRow(
                    icon = { Icon(Icons.Outlined.Key, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Recovery key",
                    subtitle = "Rotate your offline vault recovery key"
                ) {
                    TextButton(onClick = { showRotateKeyDialog = true }) {
                        Text("Rotate", color = CyanPrimary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── FILES & STORAGE section (NEW — Task 10) ────────────────────────

            SectionHeader("Files & Storage")

            SettingsCard {
                HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))

                // Export local backup
                SettingRow(
                    icon = { Icon(Icons.Outlined.FolderZip, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
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
                        Text("Export", color = CyanPrimary, style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                // Image previews
                SettingRow(
                    icon = { Icon(Icons.Outlined.Image, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Image previews",
                    subtitle = "Tap to preview encrypted images in-memory"
                ) {
                    Switch(
                        checked = imagePreview,
                        onCheckedChange = { scope.launch { prefs.setImagePreview(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanPrimary,
                            checkedTrackColor = CyanPrimary.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                // Video previews
                SettingRow(
                    icon = { Icon(Icons.Outlined.VideoFile, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Video previews",
                    subtitle = "Show play option for encrypted videos (downloads full file)"
                ) {
                    Switch(
                        checked = videoPreview,
                        onCheckedChange = { scope.launch { prefs.setVideoPreview(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanPrimary,
                            checkedTrackColor = CyanPrimary.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                // Show thumbnails (NEW)
                SettingRow(
                    icon = { Icon(Icons.Outlined.ImageSearch, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Show thumbnails",
                    subtitle = "Decrypt file thumbnails in list view (uses more Drive data)"
                ) {
                    Switch(
                        checked = thumbnailsEnabled,
                        onCheckedChange = { scope.launch { prefs.setThumbnailsEnabled(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanPrimary,
                            checkedTrackColor = CyanPrimary.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                // Storage quota
                SettingRow(
                    icon = { Icon(Icons.Outlined.Storage, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Storage quota",
                    subtitle = ""
                ) {}

                val si = homeViewModel?.storageInfo?.collectAsState()?.value
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
                HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))

                SettingRow(
                    icon = { Icon(Icons.Outlined.AccountCircle, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Signed in as",
                    subtitle = currentAccount?.email ?: "No account"
                ) {}

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

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

            // ── DEVELOPER section (DEBUG only) ─────────────────────────────────

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(16.dp))

                SectionHeader("Developer")

                SettingsCard {
                    HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))

                    SettingRow(
                        icon = { Icon(Icons.Outlined.BugReport, null, tint = VaultError, modifier = Modifier.size(22.dp)) },
                        title = "Developer options",
                        subtitle = "Diagnostics, fault injection, log viewer"
                    ) {
                        IconButton(onClick = onNavigateToDebugPanel) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = CyanPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Dialogs ────────────────────────────────────────────────────────

            if (showChangePasswordDialog) {
                AlertDialog(
                    onDismissRequest = { resetChangePwdDialog() },
                    containerColor = VaultSurfaceVariant,
                    title = { Text("Change Password", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            VaultTextField(value = changePwdCurrent, onValueChange = { changePwdCurrent = it; changePwdError = null }, label = "Current password", isPassword = true, modifier = Modifier.fillMaxWidth())
                            VaultTextField(value = changePwdNew, onValueChange = { changePwdNew = it; changePwdError = null }, label = "New password", isPassword = true, modifier = Modifier.fillMaxWidth())
                            VaultTextField(value = changePwdConfirm, onValueChange = { changePwdConfirm = it; changePwdError = null }, label = "Confirm new password", isPassword = true, modifier = Modifier.fillMaxWidth())
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
                            changePwdLoading = true
                            scope.launch {
                                val acc = GoogleSignIn.getLastSignedInAccount(context)
                                val folderId = prefs.vaultKeyFolderId.first()
                                val result = authViewModel.changePassword(changePwdCurrent, changePwdNew, acc, folderId)
                                changePwdLoading = false
                                when (result) {
                                    is AuthViewModel.PasswordChangeResult.Success -> {
                                        resetChangePwdDialog()
                                        authViewModel.lockVault()
                                        snackbarHost.showSnackbar("Password changed. Please sign in again.")
                                        onPasswordChanged?.invoke()
                                    }
                                    is AuthViewModel.PasswordChangeResult.Error -> changePwdError = result.message
                                }
                            }
                        }) {
                            if (changePwdLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyanPrimary, strokeWidth = 2.dp)
                            else Text("Change", color = CyanPrimary)
                        }
                    },
                    dismissButton = { TextButton(onClick = { resetChangePwdDialog() }) { Text("Cancel") } }
                )
            }

            if (showSwitchAccountDialog) {
                AlertDialog(
                    onDismissRequest = { resetSwitchDialog() },
                    containerColor = VaultSurfaceVariant,
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
                            if (switchAccountLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyanPrimary, strokeWidth = 2.dp)
                            else Text("Switch", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { resetSwitchDialog() }) { Text("Cancel") } }
                )
            }

            if (showRotateKeyDialog) {
                AlertDialog(
                    onDismissRequest = { resetRotateDialog() },
                    containerColor = VaultSurfaceVariant,
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
                            if (rotateKeyLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyanPrimary, strokeWidth = 2.dp)
                            else Text("Rotate", color = CyanPrimary)
                        }
                    },
                    dismissButton = { TextButton(onClick = { resetRotateDialog() }) { Text("Cancel") } }
                )
            }

            rotatedKeyToShow?.let { key ->
                AlertDialog(
                    onDismissRequest = { /* force acknowledgement */ },
                    containerColor = VaultSurfaceVariant,
                    title = { Text("New Recovery Key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Your old recovery key is now invalid. Write down the new key and store it safely.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(key, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = TextUnit(1.5f, TextUnitType.Sp)), color = CyanPrimary, modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = { TextButton(onClick = { rotatedKeyToShow = null }) { Text("I have saved it", color = CyanPrimary) } },
                    dismissButton = {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(key))
                            scope.launch {
                                snackbarHost.showSnackbar("Recovery key copied to clipboard")
                                delay(60_000L)
                                clipboardManager.setText(AnnotatedString(""))
                            }
                        }) { Text("Copy", color = CyanPrimary) }
                    }
                )
            }

            if (showCustomTimeoutDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomTimeoutDialog = false },
                    containerColor = VaultSurfaceVariant,
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
                                    focusedBorderColor = CyanPrimary,
                                    unfocusedBorderColor = VaultOutline,
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
                        }) { Text("Set", color = CyanPrimary) }
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
        color = CyanPrimary,
        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
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
