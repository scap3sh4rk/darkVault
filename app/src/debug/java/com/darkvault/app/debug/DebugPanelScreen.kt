package com.darkvault.app.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.darkvault.app.VaultSession
import com.darkvault.app.drive.DriveApiClient
import com.darkvault.app.service.UploadForegroundService
import com.darkvault.app.service.UploadState
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultBackground
import com.darkvault.app.ui.theme.VaultOutline
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugPanelScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = DeveloperOptionsManager

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = CyanPrimary)
                    }
                },
                title = { Text("Developer Options", color = CyanPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBackground)
            )
        },
        containerColor = VaultBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section A — Crypto & Key State
            SectionA(mgr)
            // Section B — Auth & Session
            SectionB(mgr, context, scope)
            // Section C — Drive & Network
            SectionC(mgr, context, scope)
            // Section D — Upload Pipeline
            SectionD(mgr, context)
            // Section E — Drive Integrity
            SectionE(mgr, context, scope)
            // Section F — In-App Log Viewer
            SectionF(mgr, context)
            // Section G — Screenshot Toggle
            SectionG(context, scope)
            // Section H — NFC diagnostics
            SectionH(context, scope, mgr)
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Section A — Crypto & Key State ────────────────────────────────────────────

@Composable
private fun SectionA(mgr: DeveloperOptionsManager) {
    val dekLoaded by mgr.dekLoaded.collectAsState()
    val kekSaltHex by mgr.kekSaltHex.collectAsState()
    val vaultKeyPresent by mgr.vaultKeyPresentOnDrive.collectAsState()
    val lastWrap by mgr.lastWrapTimestampMs.collectAsState()
    val lastUnwrap by mgr.lastUnwrapTimestampMs.collectAsState()

    ExpandableSection(title = "A — Crypto & Key State") {
        DiagRow("DEK loaded", if (dekLoaded) "YES" else "NO", if (dekLoaded) Color(0xFF00FF88) else MaterialTheme.colorScheme.error)
        DiagRow("KEK salt (first 8 bytes hex)", kekSaltHex ?: "—")
        DiagRow("vault.key on Drive", when (vaultKeyPresent) { true -> "YES"; false -> "NO"; null -> "unknown" })
        DiagRow("Last wrap", if (lastWrap > 0) formatEpoch(lastWrap) else "—")
        DiagRow("Last unwrap", if (lastUnwrap > 0) formatEpoch(lastUnwrap) else "—")
        Spacer(Modifier.height(8.dp))
        DangerButton("Force DEK null") {
            VaultSession.clearDek()
            mgr.onDekCleared()
        }
    }
}

// ── Section B — Auth & Session ────────────────────────────────────────────────

@Composable
private fun SectionB(
    mgr: DeveloperOptionsManager,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val authState by mgr.currentAuthStateName.collectAsState()
    val sessionPwEntered by mgr.sessionPasswordEntered.collectAsState()
    val biometricEnrolled by mgr.biometricEnrolled.collectAsState()
    val autoLockAt by mgr.autoLockFiresAtMs.collectAsState()
    val failedAttempts by mgr.failedUnlockAttempts.collectAsState()
    val lockoutExpiry by mgr.lockoutExpiryMs.collectAsState()

    val now = System.currentTimeMillis()
    val autoLockLabel = if (autoLockAt > now) {
        val diff = (autoLockAt - now) / 1000
        "fires in ${diff / 60}m ${diff % 60}s"
    } else "disabled"

    ExpandableSection(title = "B — Auth & Session") {
        DiagRow("AuthState", authState)
        DiagRow("sessionPasswordEntered", if (sessionPwEntered) "true" else "false")
        DiagRow("Biometric enrolled", if (biometricEnrolled) "YES" else "NO")
        DiagRow("Auto-lock timer", autoLockLabel)
        DiagRow("Failed unlock attempts", failedAttempts.toString())
        DiagRow("Lockout expiry", if (lockoutExpiry > now) formatEpoch(lockoutExpiry) else "not locked")
        Spacer(Modifier.height(8.dp))
        DangerButton("Reset lockout") {
            scope.launch {
                val prefs = com.darkvault.app.data.PreferencesManager(context)
                prefs.clearFailedAttempts()
                mgr.failedUnlockAttempts.value = 0
                mgr.lockoutExpiryMs.value = 0L
            }
        }
    }
}

// ── Section C — Drive & Network ───────────────────────────────────────────────

@Composable
private fun SectionC(
    mgr: DeveloperOptionsManager,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val lastCall by mgr.lastDriveCall.collectAsState()
    val retryDepth by mgr.retryQueueDepth.collectAsState()
    val inject429 by mgr.inject429.collectAsState()

    ExpandableSection(title = "C — Drive & Network") {
        if (lastCall != null) {
            DiagRow("Last endpoint", lastCall!!.endpoint)
            DiagRow("HTTP status", lastCall!!.httpStatus.toString())
            DiagRow("Latency", "${lastCall!!.latencyMs} ms")
            DiagRow("Recorded at", formatEpoch(lastCall!!.timestampMs))
        } else {
            DiagRow("Last Drive call", "—")
        }
        DiagRow("Retry queue depth", retryDepth.toString())
        DiagRow("Inject 429 active", if (inject429) "YES (pending)" else "NO")
        Spacer(Modifier.height(8.dp))
        DangerButton("Simulate 429 on next call") {
            mgr.inject429.value = true
        }
    }
}

// ── Section D — Upload Pipeline ───────────────────────────────────────────────

@Composable
private fun SectionD(mgr: DeveloperOptionsManager, context: Context) {
    val diag by mgr.uploadDiagnostics.collectAsState()
    val simulateFail by mgr.simulateUploadFailure.collectAsState()

    ExpandableSection(title = "D — Upload Pipeline") {
        DiagRow("Active upload jobs", diag.activeCount.toString())
        if (diag.activeFileNames.isNotEmpty()) {
            diag.activeFileNames.forEachIndexed { i, name ->
                DiagRow("  Job ${i + 1} file", name)
                if (i < diag.activeClientIds.size) {
                    DiagRow("  Job ${i + 1} clientId", diag.activeClientIds[i])
                }
            }
        }
        DiagRow("Queue size", UploadState.queueSize.collectAsState().value.toString())
        DiagRow("Failed jobs", diag.failedCount.toString())
        DiagRow("Last error", diag.lastFailedError ?: "—")
        DiagRow("Simulate failure active", if (simulateFail) "YES (pending)" else "NO")
        Spacer(Modifier.height(8.dp))
        DangerButton("Cancel all uploads") {
            UploadState.queue.forEach { UploadState.cancelledIds.add(it.id) }
        }
        Spacer(Modifier.height(4.dp))
        DangerButton("Simulate upload failure") {
            mgr.simulateUploadFailure.value = true
        }
    }
}

// ── Section E — Drive Integrity ───────────────────────────────────────────────

@Composable
private fun SectionE(
    mgr: DeveloperOptionsManager,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val running by mgr.integrityCheckRunning.collectAsState()
    val result by mgr.integrityCheckResult.collectAsState()

    ExpandableSection(title = "E — Drive Integrity") {
        Button(
            onClick = {
                scope.launch {
                    mgr.integrityCheckRunning.value = true
                    mgr.integrityCheckResult.value = null
                    val output = runIntegrityCheck(context)
                    mgr.integrityCheckResult.value = output
                    mgr.integrityCheckRunning.value = false
                }
            },
            enabled = !running,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
        ) {
            Text(if (running) "Running…" else "Run integrity check")
        }
        result?.let { text ->
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D), shape = MaterialTheme.shapes.small)
                    .padding(8.dp)
            )
        }
    }
}

// ── Section F — In-App Log Viewer ─────────────────────────────────────────────

@Composable
private fun SectionF(mgr: DeveloperOptionsManager, context: Context) {
    val logs by mgr.capturedLogs.collectAsState()
    val scope = rememberCoroutineScope()
    var autoScroll by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    LaunchedEffect(logs, autoScroll) {
        if (autoScroll) scrollState.animateScrollTo(scrollState.maxValue)
    }

    ExpandableSection(title = "F — In-App Log Viewer") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        val captured = captureLogcat()
                        mgr.capturedLogs.value = captured
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
            ) { Text("Refresh logs") }

            OutlinedButton(onClick = { autoScroll = !autoScroll }) {
                Text(if (autoScroll) "Auto-scroll ON" else "Auto-scroll OFF")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("darkVault logs", logs))
            }) { Text("Copy all") }
            OutlinedButton(onClick = { mgr.capturedLogs.value = "" }) {
                Text("Clear")
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color(0xFF0A0A0A), shape = MaterialTheme.shapes.small)
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = if (logs.isBlank()) "(no logs captured yet — tap Refresh)" else logs,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF88FF88)
            )
        }
    }
}

// ── Section G — Screenshot Toggle ─────────────────────────────────────────────

@Composable
private fun SectionG(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    val prefs = remember { com.darkvault.app.data.PreferencesManager(context) }
    val screenshotEnabled by prefs.screenshotEnabled.collectAsState(initial = false)
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    ExpandableSection(title = "G — Screenshot Toggle") {
        DiagRow("FLAG_SECURE", if (!screenshotEnabled) "ACTIVE (screenshots blocked)" else "DISABLED (debug mode)")
        Spacer(Modifier.height(8.dp))
        if (screenshotEnabled) {
            Button(
                onClick = {
                    scope.launch {
                        prefs.saveScreenshotEnabled(false)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
            ) {
                Text("Restore FLAG_SECURE")
            }
        } else {
            DangerButton("Allow screenshots (disable FLAG_SECURE)") {
                passwordInput = ""
                passwordError = null
                showPasswordDialog = true
            }
        }

        if (showPasswordDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                containerColor = VaultSurfaceVariant,
                title = { Text("Confirm master password", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter your master password to disable screenshot protection (debug only).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.material3.OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; passwordError = null },
                            label = { Text("Master password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        passwordError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            scope.launch {
                                val storedPrefs = com.darkvault.app.data.PreferencesManager(context)
                                val stored = storedPrefs.getPasswordHashAndSalt()
                                val valid = stored != null && com.darkvault.app.crypto.CryptoManager.verifyPassword(
                                    passwordInput, stored.first, stored.second
                                )
                                if (valid) {
                                    storedPrefs.saveScreenshotEnabled(true)
                                    showPasswordDialog = false
                                } else {
                                    passwordError = "Incorrect password"
                                }
                            }
                        }
                    ) { Text("Confirm", color = CyanPrimary) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

// ── Section H — NFC Diagnostics ───────────────────────────────────────────────

@Composable
private fun SectionH(context: Context, scope: kotlinx.coroutines.CoroutineScope, mgr: DeveloperOptionsManager) {
    val prefs = remember { com.darkvault.app.data.PreferencesManager(context) }
    val enrolled by prefs.nfcEnabled.collectAsState(initial = false)
    val lastId by mgr.nfcLastTagId.collectAsState()

    // Read mode/tagType from DataStore when enrolled state changes
    var mode by remember { mutableStateOf("—") }
    var tagType by remember { mutableStateOf("—") }
    LaunchedEffect(enrolled) {
        if (enrolled) {
            val creds = prefs.getNfcCredentials()
            mode = creds?.mode ?: "—"
            tagType = creds?.tagType ?: "—"
        } else {
            mode = "—"; tagType = "—"
        }
    }

    ExpandableSection(title = "H — NFC Unlock") {
        DiagRow("Enrolled", if (enrolled) "YES" else "NO", if (enrolled) Color(0xFF00FF88) else MaterialTheme.colorScheme.error)
        DiagRow("Mode", mode)
        DiagRow("Tag type", tagType)
        DiagRow("Last tag UID prefix", lastId)
        if (enrolled) {
            DangerButton("Clear NFC credentials (debug)") {
                scope.launch {
                    prefs.clearNfcCredentials()
                    mgr.onNfcCleared()
                }
            }
        }
    }
}

// ── Shared UI helpers ─────────────────────────────────────────────────────────

@Composable
private fun ExpandableSection(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = VaultOutline
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider(color = VaultOutline, thickness = 0.5.dp)
                    Spacer(Modifier.height(4.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text(label)
    }
}

// ── Utility functions ─────────────────────────────────────────────────────────

private fun formatEpoch(ms: Long): String {
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    return "${fmt.format(Date(ms))} ($ms)"
}

private suspend fun captureLogcat(): String = withContext(Dispatchers.IO) {
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "200", "-s", "darkVault:V"))
        proc.inputStream.bufferedReader().readText()
    } catch (e: Exception) {
        "Error reading logcat: ${e.message}"
    }
}

@Suppress("DEPRECATION")
private suspend fun runIntegrityCheck(context: Context): String = withContext(Dispatchers.IO) {
    val account = GoogleSignIn.getLastSignedInAccount(context)
        ?: return@withContext "Not signed in — cannot run integrity check."
    val session = VaultSession
    val folderId = session.signedInAccount?.let { null } // resolved below via Drive
    val sb = StringBuilder()

    try {
        val client = DriveApiClient(context, account)
        // Find root folder
        val rootFolderIdFromSession = runCatching {
            client.ensureVaultFolder(null)
        }.getOrElse { return@withContext "Failed to find darkVault/ folder: ${it.message}" }

        sb.appendLine("=== darkVault Integrity Check ===")
        sb.appendLine("Root folder ID: $rootFolderIdFromSession")

        // List all items
        val items = runCatching { client.listItems(rootFolderIdFromSession) }
            .getOrElse { return@withContext "Failed to list items: ${it.message}" }

        val vaultFiles = items.filter { !it.isFolder && it.name.endsWith(".vault") }
        val folders = items.filter { it.isFolder }
        val otherFiles = items.filter { !it.isFolder && !it.name.endsWith(".vault") }

        sb.appendLine()
        sb.appendLine("--- Files ---")
        sb.appendLine("Total items: ${items.size}")
        sb.appendLine(".vault files: ${vaultFiles.size}")
        sb.appendLine("Folders: ${folders.size}")
        sb.appendLine("Other files: ${otherFiles.size}")
        sb.appendLine()

        // Check vault.key
        val vaultKey = runCatching { client.downloadVaultKey(rootFolderIdFromSession) }.getOrNull()
        sb.appendLine("vault.key present: ${if (vaultKey != null) "YES" else "NO"}")
        DeveloperOptionsManager.setVaultKeyPresent(vaultKey != null)
        sb.appendLine()

        // Per-file check
        sb.appendLine("--- Per-file appProperties check ---")
        var orphanCount = 0
        vaultFiles.forEach { file ->
            val hasOrigName = file.originalName.isNotBlank()
            val hasOrigMime = file.originalMimeType.isNotBlank()
            if (!hasOrigName || !hasOrigMime) {
                orphanCount++
                sb.appendLine("[ORPHAN] ${file.name} (id=${file.id}) missing: ${if (!hasOrigName) "originalName " else ""}${if (!hasOrigMime) "originalMimeType" else ""}")
            } else {
                sb.appendLine("[OK] ${file.name} → ${file.originalName} (${file.originalMimeType})")
            }
        }
        sb.appendLine()
        sb.appendLine("Orphans found: $orphanCount")
        sb.appendLine()
        sb.appendLine("Check complete at ${java.util.Date()}")
    } catch (e: Exception) {
        sb.appendLine("Integrity check error: ${e.message}")
    }

    sb.toString()
}
