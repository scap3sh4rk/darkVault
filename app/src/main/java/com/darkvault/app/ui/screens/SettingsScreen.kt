package com.darkvault.app.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.darkvault.app.crypto.BiometricHelper
import com.darkvault.app.crypto.BiometricKeyManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultBackground
import com.darkvault.app.ui.theme.VaultOutline
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val prefs = remember { PreferencesManager(context) }

    val biometricEnabled by authViewModel.biometricEnabled.collectAsState()
    val autoLockMinutes by authViewModel.autoLockMinutes.collectAsState()
    val imagePreview by prefs.imagePreviewEnabled.collectAsState(initial = true)
    val videoPreview by prefs.videoPreviewEnabled.collectAsState(initial = false)

    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    var autoLockExpanded by remember { mutableStateOf(false) }

    val autoLockOptions = listOf(0 to "Never", 1 to "1 minute", 5 to "5 minutes", 15 to "15 minutes", 30 to "30 minutes")

    // Biometric prompt for enrollment
    val biometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher ?: return
                    authViewModel.completeEnrollment(cipher)
                    scope.launch { snackbarHost.showSnackbar("Biometric unlock enabled") }
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
            // ── Security section ───────────────────────────────────────────────

            SectionHeader("Security")

            SettingsCard {
                // Biometric toggle
                SettingRow(
                    icon = { Icon(Icons.Outlined.Fingerprint, null, tint = CyanPrimary, modifier = Modifier.size(22.dp)) },
                    title = "Biometric unlock",
                    subtitle = if (!biometricAvailable) "Not available on this device"
                    else if (biometricEnabled) "Fingerprint / face unlock enabled"
                    else "Enable fingerprint / face unlock"
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
                                    .setTitle("Enable biometric unlock")
                                    .setSubtitle("Authenticate to enable fingerprint / face unlock")
                                    .setNegativeButtonText("Cancel")
                                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                                    .build()
                                biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
                            } else {
                                authViewModel.disableBiometric()
                                scope.launch { snackbarHost.showSnackbar("Biometric unlock disabled") }
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
                    subtitle = "Lock vault after inactivity when app goes to background"
                ) {
                    ExposedDropdownMenuBox(
                        expanded = autoLockExpanded,
                        onExpandedChange = { autoLockExpanded = it }
                    ) {
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
                                    onClick = {
                                        authViewModel.setAutoLockMinutes(minutes)
                                        autoLockExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))

                // Lock now
                SettingRow(
                    icon = { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp)) },
                    title = "Lock vault now",
                    subtitle = "Requires password on next open"
                ) {
                    androidx.compose.material3.TextButton(onClick = { authViewModel.lockVault() }) {
                        Text("Lock", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Previews section ───────────────────────────────────────────────

            SectionHeader("Previews")
            Text(
                "Previews decrypt files in memory. Disable to avoid Drive rate limits or save bandwidth.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsCard {
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
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = CyanPrimary,
        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultSurfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        icon()
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}
