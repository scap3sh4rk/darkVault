package com.darkvault.app.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkvault.app.model.FilterType
import com.darkvault.app.model.SortOrder
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.EmptyVaultState
import com.darkvault.app.ui.components.FilterChipRow
import com.darkvault.app.ui.components.StorageInfoCard
import com.darkvault.app.ui.components.UploadProgressCard
import com.darkvault.app.ui.components.VaultFileCard
import com.darkvault.app.ui.components.VaultFolderCard
import com.darkvault.app.ui.components.fileTypeIcon
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultBackground
import com.darkvault.app.ui.theme.VaultOutline
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.BuildConfig
import com.darkvault.app.VaultSession
import com.darkvault.app.viewmodel.AuthViewModel
import com.darkvault.app.viewmodel.HomeUiState
import com.darkvault.app.viewmodel.HomeViewModel
import com.darkvault.app.viewmodel.OperationState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel = viewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToDebugPanel: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    val password by authViewModel.masterPassword.collectAsState()
    val uiState by homeViewModel.uiState.collectAsState()
    val displayItems by homeViewModel.displayItems.collectAsState()
    val opState by homeViewModel.operationState.collectAsState()
    val storageInfo by homeViewModel.storageInfo.collectAsState()
    val folderStack by homeViewModel.folderStack.collectAsState()
    val activeUpload by homeViewModel.activeUpload.collectAsState()
    val searchQuery by homeViewModel.searchQuery.collectAsState()
    val filterType by homeViewModel.filterType.collectAsState()
    val sortOrder by homeViewModel.sortOrder.collectAsState()
    val selectedIds by homeViewModel.selectedIds.collectAsState()
    val recentItems by homeViewModel.recentItems.collectAsState() // Task 4
    val lastSynced by homeViewModel.lastSyncedMs.collectAsState() // Task 7
    val recoveryKeyToShow by authViewModel.recoveryKey.collectAsState()

    val isSelectionMode = selectedIds.isNotEmpty()
    val vaultFolderId by authViewModel.vaultFolderId.collectAsState()

    val currentAccount = remember { GoogleSignIn.getLastSignedInAccount(context) }
    var fileToDelete by remember { mutableStateOf<VaultFile?>(null) }
    var fileToPermDelete by remember { mutableStateOf<VaultFile?>(null) }
    var showUploadMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showDeleteSelected by remember { mutableStateOf(false) }

    var previewFile by remember { mutableStateOf<VaultFile?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val acc = currentAccount; val pwd = password
        if (uris.isNotEmpty() && acc != null && pwd != null) {
            homeViewModel.uploadFiles(uris, pwd, acc, context.contentResolver)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val acc = currentAccount; val pwd = password
        if (uri != null && acc != null && pwd != null) {
            homeViewModel.uploadFolder(uri, pwd, acc, context.contentResolver)
        }
    }

    LaunchedEffect(currentAccount) {
        currentAccount?.let { homeViewModel.loadFiles(it) }
    }

    // Retry DEK loading when online after an offline unlock (DEK not loaded from vault.key).
    // Fix: LOW-003 — DEK is null when Drive was unavailable at unlock time; attempt to load it
    // on HomeScreen entry. Upload FAB is still shown during the brief window between arrival on
    // HomeScreen and successful DEK load; UploadForegroundService falls back to per-file PBKDF2
    // in that window (v0.02 encryption), resulting in version-mixed vaults.
    // TODO LOW-003: Disable the upload FAB while VaultSession.dek == null to prevent version mixing.
    LaunchedEffect(currentAccount, vaultFolderId) {
        val acc = currentAccount ?: return@LaunchedEffect
        val folderId = vaultFolderId ?: return@LaunchedEffect
        val pwd = password ?: return@LaunchedEffect
        if (VaultSession.dek == null) {
            authViewModel.loadOrCreateDek(pwd, folderId, acc)
        }
    }

    LaunchedEffect(opState) {
        when (val s = opState) {
            is OperationState.Done -> {
                snackbarHostState.showSnackbar(s.message)
                homeViewModel.clearOperationState()
            }
            is OperationState.Failed -> {
                snackbarHostState.showSnackbar(s.message)
                homeViewModel.clearOperationState()
            }
            else -> Unit
        }
    }

    // Recovery key first-time display
    recoveryKeyToShow?.let { key ->
        AlertDialog(
            onDismissRequest = { /* force user to acknowledge */ },
            containerColor = VaultSurfaceVariant,
            title = { Text("Save Your Recovery Key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This key can recover your vault if you forget your master password. It will never be shown again — write it down and store it safely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        key,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                        ),
                        color = CyanPrimary,
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { authViewModel.clearRecoveryKey() }) {
                    Text("I have saved it", color = CyanPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Fix: MEDIUM-005 — schedule clipboard clearing after 60 seconds
                    clipboardManager.setText(AnnotatedString(key))
                    scope.launch {
                        snackbarHostState.showSnackbar("Recovery key copied to clipboard")
                        delay(60_000L)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            @Suppress("DEPRECATION")
                            clipboardManager.setText(AnnotatedString(""))
                        } else {
                            clipboardManager.setText(AnnotatedString(""))
                        }
                    }
                }) {
                    Text("Copy", color = CyanPrimary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        if (homeViewModel.canGoBack) {
                            IconButton(onClick = { homeViewModel.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Up", tint = CyanPrimary)
                            }
                        }
                    },
                    title = {
                        if (isSelectionMode) {
                            Text("${selectedIds.size} selected", style = MaterialTheme.typography.titleLarge, color = CyanPrimary)
                        } else {
                            Column {
                                Text("darkVault", style = MaterialTheme.typography.titleLarge, color = CyanPrimary)
                                if (folderStack.size > 1) {
                                    // Breadcrumb
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(folderStack) { entry ->
                                            val isLast = entry == folderStack.last()
                                            TextButton(
                                                onClick = { if (!isLast) homeViewModel.navigateTo(entry) },
                                                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 0.dp)
                                            ) {
                                                Text(
                                                    entry.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isLast) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!isLast) {
                                                Text(" / ", style = MaterialTheme.typography.labelSmall, color = VaultOutline)
                                            }
                                        }
                                    }
                                } else {
                                    currentAccount?.email?.let { email ->
                                        Text(
                                            email,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VaultOutline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = { homeViewModel.selectAll() }) {
                                Icon(Icons.Outlined.SelectAll, "Select all", tint = CyanPrimary)
                            }
                            // Task 5: Batch download
                            IconButton(onClick = {
                                val pwd = password; val acc = currentAccount
                                if (pwd != null && acc != null) homeViewModel.downloadSelected(pwd, acc)
                            }) {
                                Icon(Icons.Outlined.Download, "Download selected", tint = CyanPrimary)
                            }
                            IconButton(onClick = { showDeleteSelected = true }) {
                                Icon(Icons.Outlined.DeleteSweep, "Move selected to trash", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { homeViewModel.clearSelection() }) {
                                Icon(Icons.Outlined.Close, "Cancel selection", tint = CyanPrimary)
                            }
                        } else {
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search, "Search", tint = CyanPrimary)
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Outlined.Sort, "Sort", tint = CyanPrimary)
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    SortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    order.label,
                                                    color = if (sortOrder == order) CyanPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                homeViewModel.sortOrder.value = order
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { currentAccount?.let { homeViewModel.loadFiles(it) } }) {
                                Icon(Icons.Outlined.Refresh, "Refresh", tint = CyanPrimary)
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, "More options", tint = CyanPrimary)
                                }
                                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Export backup") },
                                        leadingIcon = { Icon(Icons.Outlined.FolderZip, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            val pwd = password; val acc = currentAccount
                                            if (pwd != null && acc != null) homeViewModel.exportVaultBackup(pwd, acc)
                                            else scope.launch { snackbarHostState.showSnackbar("Vault is locked") }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("View trash") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onNavigateToTrash()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onNavigateToSettings()
                                        }
                                    )
                                    if (BuildConfig.DEBUG) {
                                        DropdownMenuItem(
                                            text = { Text("Developer Options") },
                                            onClick = {
                                                showMoreMenu = false
                                                onNavigateToDebugPanel()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBackground)
                )

                // Search bar
                AnimatedVisibility(visible = showSearch, enter = expandVertically(), exit = shrinkVertically()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { homeViewModel.searchQuery.value = it },
                        placeholder = { Text("Search files…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = VaultOutline) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { homeViewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Outlined.Close, "Clear", tint = VaultOutline)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = VaultOutline,
                            cursorColor = CyanPrimary,
                            focusedLeadingIconColor = CyanPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentAccount != null && !isSelectionMode) {
                Box {
                    FloatingActionButton(
                        onClick = { showUploadMenu = true },
                        containerColor = CyanPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Outlined.Add, "Upload")
                    }
                    DropdownMenu(expanded = showUploadMenu, onDismissRequest = { showUploadMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Upload files") },
                            leadingIcon = { Icon(Icons.Outlined.InsertDriveFile, null) },
                            onClick = {
                                showUploadMenu = false
                                authViewModel.suppressNextLock()
                                filePicker.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Upload folder") },
                            leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                            onClick = {
                                showUploadMenu = false
                                authViewModel.suppressNextLock()
                                folderPicker.launch(null)
                            }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = VaultBackground
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // Active upload card (from background service)
            activeUpload?.let { upload ->
                UploadProgressCard(
                    fileName = upload.fileName,
                    stage = upload.stage,
                    progress = upload.progress,
                    uploaded = upload.uploaded,
                    total = upload.total,
                    onCancel = { homeViewModel.cancelAllUploads() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // In-progress operation (download/decrypt)
            if (activeUpload == null) {
                when (val s = opState) {
                    is OperationState.InProgress -> {
                        UploadProgressCard(
                            fileName = s.fileName,
                            stage = s.stage,
                            progress = s.progress,
                            uploaded = 0L,
                            total = 0L,
                            onCancel = { homeViewModel.clearOperationState() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    else -> Unit
                }
            }

            // Filter chips
            FilterChipRow(
                selected = filterType,
                onSelect = { homeViewModel.filterType.value = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

                // Task 7: Last synced indicator
                if (lastSynced > 0) {
                    val elapsed = (System.currentTimeMillis() - lastSynced) / 1000
                    val label = when {
                        elapsed < 60 -> "Last synced: just now"
                        elapsed < 3600 -> "Last synced: ${elapsed / 60} min ago"
                        else -> "Last synced: ${elapsed / 3600}h ago"
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultOutline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                when (uiState) {
                    is HomeUiState.Loading -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(color = CyanPrimary, strokeCap = StrokeCap.Round, modifier = Modifier.size(40.dp))
                        }
                    }
                    is HomeUiState.Success, is HomeUiState.Error -> {
                        if (displayItems.isEmpty() && uiState is HomeUiState.Success) {
                            EmptyVaultState(modifier = Modifier.weight(1f).fillMaxWidth())
                        } else if (uiState is HomeUiState.Error) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).padding(24.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text((uiState as HomeUiState.Error).message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.height(16.dp))
                                    CyberButton("Retry", onClick = { currentAccount?.let { homeViewModel.loadFiles(it) } })
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Task 4: Recents section — only at root, no search/filter active
                                val showRecents = folderStack.size == 1 &&
                                    searchQuery.isBlank() &&
                                    filterType == FilterType.ALL &&
                                    recentItems.isNotEmpty()

                                if (showRecents) {
                                    item(key = "recents_header") {
                                        Text(
                                            "Recents",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CyanPrimary,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(bottom = 8.dp)
                                        ) {
                                            items(recentItems, key = { "recent_${it.id}" }) { file ->
                                                RecentFileCard(
                                                    file = file,
                                                    onClick = {
                                                        val pwd = password; val acc = currentAccount
                                                        if (HomeViewModel.isImageMime(file.originalMimeType)) {
                                                            previewFile = file
                                                        } else if (pwd != null && acc != null) {
                                                            homeViewModel.downloadAndDecrypt(file, pwd, acc)
                                                        } else {
                                                            scope.launch { snackbarHostState.showSnackbar("Vault is locked") }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                items(displayItems, key = { it.id }) { item ->
                                    if (item.isFolder) {
                                        VaultFolderCard(
                                            folder = item,
                                            onOpen = { homeViewModel.openFolder(item) },
                                            onDelete = { fileToDelete = item },
                                            isSelected = item.id in selectedIds,
                                            onToggleSelect = if (isSelectionMode) ({ homeViewModel.toggleSelection(item.id) }) else null
                                        )
                                    } else {
                                        val canPreview = HomeViewModel.isImageMime(item.originalMimeType)
                                        VaultFileCard(
                                            file = item,
                                            onDownload = {
                                                val pwd = password; val acc = currentAccount
                                                if (pwd != null && acc != null) homeViewModel.downloadAndDecrypt(item, pwd, acc)
                                                else scope.launch { snackbarHostState.showSnackbar("Vault is locked") }
                                            },
                                            onDelete = { fileToDelete = item },
                                            onPreview = if (canPreview) ({ previewFile = item }) else null,
                                            onClick = if (canPreview && !isSelectionMode) ({ previewFile = item }) else null,
                                            isSelected = item.id in selectedIds,
                                            onToggleSelect = if (isSelectionMode) ({ homeViewModel.toggleSelection(item.id) }) else null
                                        )
                                    }
                                }
                            }
                        }

                        // Storage info at bottom
                        storageInfo?.let { info ->
                            StorageInfoCard(
                                usedByVault = info.usedByVaultBytes,
                                driveTotalUsed = info.driveTotalUsedBytes,
                                driveLimit = info.driveLimitBytes,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    else -> Unit
                }
        }
    }

    // ── Task 3: Move to trash / permanent delete dialog ────────────────────

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Move to Trash?") },
            text = {
                Text(
                    "\"${file.originalName}\" will be moved to your Drive trash.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    currentAccount?.let { homeViewModel.deleteFile(file, it) }
                    fileToDelete = null
                }) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        fileToPermDelete = file
                        fileToDelete = null
                    }) { Text("Delete Permanently", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) }
                    TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    // Permanent delete confirmation
    fileToPermDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToPermDelete = null },
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    "\"${file.originalName}\" will be permanently deleted and cannot be recovered.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    currentAccount?.let { homeViewModel.permanentDeleteFile(file, it) }
                    fileToPermDelete = null
                }) { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { fileToPermDelete = null }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    // ── Delete selected dialog (Task 3: now moves to trash) ───────────────

    if (showDeleteSelected) {
        AlertDialog(
            onDismissRequest = { showDeleteSelected = false },
            title = { Text("Move ${selectedIds.size} item(s) to trash?") },
            text = { Text("Selected items will be moved to your Drive trash.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    currentAccount?.let { homeViewModel.deleteSelected(it) }
                    showDeleteSelected = false
                }) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteSelected = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    // ── Image preview dialog ───────────────────────────────────────────────

    previewFile?.let { file ->
        ImagePreviewDialog(
            file = file,
            homeViewModel = homeViewModel,
            password = password,
            account = currentAccount,
            onDismiss = { previewFile = null }
        )
    }
}

// ── Task 4: Compact recent file card ─────────────────────────────────────────

@Composable
private fun RecentFileCard(
    file: VaultFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        modifier = modifier
            .width(100.dp)
            .border(1.dp, VaultOutline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(VaultBackground, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    fileTypeIcon(file.originalName, file.originalMimeType),
                    null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                file.originalName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Image preview dialog ───────────────────────────────────────────────────────

@Composable
private fun ImagePreviewDialog(
    file: VaultFile,
    homeViewModel: HomeViewModel,
    password: String?,
    account: GoogleSignInAccount?,
    onDismiss: () -> Unit
) {
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.id) {
        if (password == null || account == null) {
            error = "Vault is locked"; loading = false; return@LaunchedEffect
        }
        val bytes = homeViewModel.decryptToMemory(file, password, account)
        if (bytes != null) imageBytes = bytes else error = "Preview failed"
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                file.originalName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(300.dp)) {
                when {
                    loading -> CircularProgressIndicator(color = CyanPrimary, strokeCap = StrokeCap.Round)
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    imageBytes != null -> {
                        val bmp = remember(imageBytes) {
                            try {
                                android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes!!.size)
                            } catch (_: Exception) { null }
                        }
                        if (bmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = file.originalName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Cannot decode image", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
