package com.darkvault.app.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.darkvault.app.BuildConfig
import com.darkvault.app.VaultSession
import com.darkvault.app.model.FilterType
import com.darkvault.app.service.ConflictResolution
import com.darkvault.app.model.SortOrder
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.EmptySearchState
import com.darkvault.app.ui.components.EmptyVaultState
import com.darkvault.app.ui.components.FilterChipRow
import com.darkvault.app.ui.components.StorageInfoCard
import com.darkvault.app.ui.components.UploadProgressCard
import com.darkvault.app.ui.components.VaultFileCard
import com.darkvault.app.ui.components.VaultFolderCard
import com.darkvault.app.ui.components.VaultThumbnailImage
import com.darkvault.app.ui.components.fileTypeIcon
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultBackground
import com.darkvault.app.ui.theme.VaultOutline
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.viewmodel.AuthViewModel
import com.darkvault.app.viewmodel.FolderEntry
import com.darkvault.app.viewmodel.HomeUiState
import com.darkvault.app.viewmodel.HomeViewModel
import com.darkvault.app.viewmodel.OperationState
import com.darkvault.app.viewmodel.ViewLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
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
    val recentItems by homeViewModel.recentItems.collectAsState()
    val lastSynced by homeViewModel.lastSyncedMs.collectAsState()
    val recoveryKeyToShow by authViewModel.recoveryKey.collectAsState()
    val viewLayout by homeViewModel.viewLayout.collectAsState()
    val pendingConflict by homeViewModel.pendingConflict.collectAsState()
    val uploadIsPaused by homeViewModel.uploadIsPaused.collectAsState()
    // Task 4 — thumbnail gating flags
    val thumbnailsEnabled by homeViewModel.thumbnailsEnabled.collectAsState()
    val imagePreviewEnabled by homeViewModel.imagePreviewEnabled.collectAsState()
    val showThumbnails = thumbnailsEnabled && imagePreviewEnabled

    val isSelectionMode = selectedIds.isNotEmpty()
    val vaultFolderId by authViewModel.vaultFolderId.collectAsState()

    val currentAccount = remember { GoogleSignIn.getLastSignedInAccount(context) }
    var fileToDelete by remember { mutableStateOf<VaultFile?>(null) }
    var fileToPermDelete by remember { mutableStateOf<VaultFile?>(null) }
    var showUploadMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showDeleteSelected by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<VaultFile?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var longPressFile by remember { mutableStateOf<VaultFile?>(null) }
    var showFileInfo by remember { mutableStateOf<VaultFile?>(null) }
    var showRenameDialog by remember { mutableStateOf<VaultFile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var textPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var videoPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var audioPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var pdfPreviewFile by remember { mutableStateOf<VaultFile?>(null) }

    // Task 6 — breadcrumb expansion state
    var breadcrumbExpanded by remember { mutableStateOf(false) }

    // Task 9 — system back intercept for sub-folder navigation
    BackHandler(enabled = homeViewModel.canGoBack) {
        homeViewModel.navigateUp()
    }

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
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                        ),
                        color = CyanPrimary,
                        modifier = Modifier.fillMaxWidth()
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
                    clipboardManager.setText(AnnotatedString(key))
                    scope.launch {
                        snackbarHostState.showSnackbar("Recovery key copied to clipboard")
                        delay(60_000L)
                        clipboardManager.setText(AnnotatedString(""))
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
                        // Task 9 — back arrow only when inside a sub-folder
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
                                if (folderStack.size <= 1) {
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
                            // Count non-folder selected items for download badge
                            val downloadableCount = displayItems.count { it.id in selectedIds && !it.isFolder }
                            IconButton(onClick = { homeViewModel.selectAll() }) {
                                Icon(Icons.Outlined.SelectAll, "Select all", tint = CyanPrimary)
                            }
                            // Task 3 — download button with count badge
                            BadgedBox(
                                badge = {
                                    if (downloadableCount > 0) {
                                        Badge(containerColor = CyanPrimary, contentColor = Color(0xFF00363F)) {
                                            Text("$downloadableCount")
                                        }
                                    }
                                }
                            ) {
                                IconButton(
                                    onClick = {
                                        if (downloadableCount > 0) {
                                            val pwd = password; val acc = currentAccount
                                            if (pwd != null && acc != null) homeViewModel.downloadSelected(pwd, acc)
                                        }
                                    },
                                    enabled = downloadableCount > 0
                                ) {
                                    Icon(
                                        Icons.Outlined.Download, "Download selected",
                                        tint = if (downloadableCount > 0) CyanPrimary else CyanPrimary.copy(alpha = 0.38f)
                                    )
                                }
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
                            // Task 7 — layout toggle
                            Box {
                                IconButton(onClick = { showLayoutMenu = true }) {
                                    Icon(
                                        when (viewLayout) {
                                            ViewLayout.LIST -> Icons.Outlined.ViewList
                                            ViewLayout.GRID2 -> Icons.Outlined.GridView
                                            ViewLayout.GRID3 -> Icons.Outlined.GridOn
                                        },
                                        "View layout", tint = CyanPrimary
                                    )
                                }
                                DropdownMenu(expanded = showLayoutMenu, onDismissRequest = { showLayoutMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("List view", color = if (viewLayout == ViewLayout.LIST) CyanPrimary else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.ViewList, null, tint = if (viewLayout == ViewLayout.LIST) CyanPrimary else MaterialTheme.colorScheme.onSurface) },
                                        onClick = { homeViewModel.setViewLayout(ViewLayout.LIST); showLayoutMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Grid (2 columns)", color = if (viewLayout == ViewLayout.GRID2) CyanPrimary else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.GridView, null, tint = if (viewLayout == ViewLayout.GRID2) CyanPrimary else MaterialTheme.colorScheme.onSurface) },
                                        onClick = { homeViewModel.setViewLayout(ViewLayout.GRID2); showLayoutMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Grid (3 columns)", color = if (viewLayout == ViewLayout.GRID3) CyanPrimary else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.GridOn, null, tint = if (viewLayout == ViewLayout.GRID3) CyanPrimary else MaterialTheme.colorScheme.onSurface) },
                                        onClick = { homeViewModel.setViewLayout(ViewLayout.GRID3); showLayoutMenu = false }
                                    )
                                }
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

                // Task 6 — Breadcrumb row (only when inside a sub-folder)
                if (folderStack.size > 1) {
                    BreadcrumbRow(
                        folderStack = folderStack,
                        expanded = breadcrumbExpanded,
                        onExpand = { breadcrumbExpanded = true },
                        onNavigateTo = { entry -> homeViewModel.navigateTo(entry); breadcrumbExpanded = false }
                    )
                }

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
                var showNewFolderDialog by remember { mutableStateOf(false) }
                var newFolderName by remember { mutableStateOf("") }

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
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                            onClick = {
                                showUploadMenu = false
                                newFolderName = ""
                                showNewFolderDialog = true
                            }
                        )
                    }
                }

                if (showNewFolderDialog) {
                    AlertDialog(
                        onDismissRequest = { showNewFolderDialog = false },
                        containerColor = VaultSurfaceVariant,
                        title = { Text("New Folder", style = MaterialTheme.typography.titleMedium) },
                        text = {
                            OutlinedTextField(
                                value = newFolderName,
                                onValueChange = { newFolderName = it.take(200) },
                                label = { Text("Folder name") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanPrimary,
                                    unfocusedBorderColor = VaultOutline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = newFolderName.isNotBlank(),
                                onClick = {
                                    val acc = currentAccount
                                    val parentId = homeViewModel.folderStack.value.lastOrNull()?.id
                                    if (acc != null && parentId != null) {
                                        homeViewModel.createFolder(newFolderName, parentId, acc)
                                    }
                                    showNewFolderDialog = false
                                }
                            ) { Text("Create", color = CyanPrimary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = VaultBackground
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // Active upload card (Task 5: includes pause/resume)
            activeUpload?.let { upload ->
                UploadProgressCard(
                    fileName = upload.fileName,
                    stage = upload.stage,
                    progress = upload.progress,
                    uploaded = upload.uploaded,
                    total = upload.total,
                    isPaused = uploadIsPaused,
                    currentIndex = upload.currentIndex,
                    totalInBatch = upload.totalInBatch,
                    onPause = { homeViewModel.pauseAllUploads() },
                    onResume = { homeViewModel.resumeAllUploads() },
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

            // Last synced indicator
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

            val isRefreshing = uiState is HomeUiState.Loading
            val pullRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { currentAccount?.let { homeViewModel.loadFiles(it) } },
                state = pullRefreshState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
            when (uiState) {
                is HomeUiState.Loading -> {
                    // Show shimmer skeleton cards instead of spinner
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(8) {
                            ShimmerFileCard()
                        }
                    }
                }
                is HomeUiState.Success, is HomeUiState.Error -> {
                    if (displayItems.isEmpty() && uiState is HomeUiState.Success) {
                        if (searchQuery.isNotBlank() || filterType != FilterType.ALL) {
                            EmptySearchState(modifier = Modifier.weight(1f).fillMaxWidth())
                        } else {
                            EmptyVaultState(modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    } else if (uiState is HomeUiState.Error) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).padding(24.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text((uiState as HomeUiState.Error).message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(16.dp))
                                CyberButton("Retry", onClick = { currentAccount?.let { homeViewModel.loadFiles(it) } })
                            }
                        }
                    } else {
                        // Task 7 — animated layout switch
                        AnimatedContent(
                            targetState = viewLayout,
                            modifier = Modifier.weight(1f)
                        ) { layout ->
                            when (layout) {
                                ViewLayout.LIST -> {
                                    LazyColumn(
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        // Recents section — only at root with no active search/filter
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
                                                                when (previewKind(file.originalMimeType)) {
                                                                    PreviewKind.IMAGE -> previewFile = file
                                                                    PreviewKind.VIDEO -> videoPreviewFile = file
                                                                    PreviewKind.AUDIO -> audioPreviewFile = file
                                                                    PreviewKind.PDF -> pdfPreviewFile = file
                                                                    PreviewKind.TEXT -> textPreviewFile = file
                                                                    PreviewKind.NONE -> {
                                                                        val pwd = password; val acc = currentAccount
                                                                        if (pwd != null && acc != null) homeViewModel.downloadAndDecrypt(file, pwd, acc)
                                                                        else scope.launch { snackbarHostState.showSnackbar("Vault is locked") }
                                                                    }
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
                                                val kind = previewKind(item.originalMimeType)
                                                val openPreview: () -> Unit = {
                                                    when (kind) {
                                                        PreviewKind.IMAGE -> previewFile = item
                                                        PreviewKind.VIDEO -> videoPreviewFile = item
                                                        PreviewKind.AUDIO -> audioPreviewFile = item
                                                        PreviewKind.PDF -> pdfPreviewFile = item
                                                        PreviewKind.TEXT -> textPreviewFile = item
                                                        PreviewKind.NONE -> showFileInfo = item
                                                    }
                                                }
                                                VaultFileCard(
                                                    file = item,
                                                    onDownload = {
                                                        val pwd = password; val acc = currentAccount
                                                        if (pwd != null && acc != null) homeViewModel.downloadAndDecrypt(item, pwd, acc)
                                                        else scope.launch { snackbarHostState.showSnackbar("Vault is locked") }
                                                    },
                                                    onDelete = { fileToDelete = item },
                                                    onPreview = if (kind != PreviewKind.NONE) openPreview else null,
                                                    onClick = if (!isSelectionMode) openPreview else null,
                                                    onLongPress = if (!isSelectionMode) ({ longPressFile = item }) else null,
                                                    isSelected = item.id in selectedIds,
                                                    onToggleSelect = if (isSelectionMode) ({ homeViewModel.toggleSelection(item.id) }) else null,
                                                    showThumbnail = showThumbnails,
                                                    thumbnailPassword = password,
                                                    thumbnailAccount = currentAccount
                                                )
                                            }
                                        }
                                    }
                                }
                                ViewLayout.GRID2, ViewLayout.GRID3 -> {
                                    val columns = if (layout == ViewLayout.GRID2) 2 else 3
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(columns),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(displayItems, key = { it.id }) { item ->
                                            VaultGridItem(
                                                item = item,
                                                columns = columns,
                                                isSelected = item.id in selectedIds,
                                                isSelectionMode = isSelectionMode,
                                                onTap = {
                                                    when {
                                                        isSelectionMode -> homeViewModel.toggleSelection(item.id)
                                                        item.isFolder -> homeViewModel.openFolder(item)
                                                        else -> when (previewKind(item.originalMimeType)) {
                                                            PreviewKind.IMAGE -> previewFile = item
                                                            PreviewKind.VIDEO -> videoPreviewFile = item
                                                            PreviewKind.AUDIO -> audioPreviewFile = item
                                                            PreviewKind.PDF -> pdfPreviewFile = item
                                                            PreviewKind.TEXT -> textPreviewFile = item
                                                            PreviewKind.NONE -> showFileInfo = item
                                                        }
                                                    }
                                                },
                                                onLongPress = { homeViewModel.toggleSelection(item.id) },
                                                showThumbnail = showThumbnails,
                                                thumbnailPassword = password,
                                                thumbnailAccount = currentAccount
                                            )
                                        }
                                    }
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
            } // end inner Column
            } // end PullToRefreshBox
        }
    }

    // ── Move to trash / permanent delete dialog ────────────────────────────────

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

    // ── Conflict dialog ────────────────────────────────────────────────────────

    var showReplaceConfirmDialog by remember { mutableStateOf(false) }
    var conflictRenameText by remember(pendingConflict) { mutableStateOf(pendingConflict?.suggestedName ?: "") }

    pendingConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { homeViewModel.resolveConflict(ConflictResolution.Skip) },
            containerColor = VaultSurfaceVariant,
            title = {
                Column {
                    Text(
                        "\"${conflict.originalName}\" already exists",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (conflict.totalConflicts > 1) {
                        Text(
                            "File ${conflict.conflictIndex} of ${conflict.totalConflicts}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    // Editable rename field
                    OutlinedTextField(
                        value = conflictRenameText,
                        onValueChange = { conflictRenameText = it.take(200) },
                        label = { Text("Rename to") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = VaultOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            val safeName = conflictRenameText
                                .replace(Regex("[/\\\\:*?\"<>|\\p{Cntrl}]"), "_")
                                .take(200)
                                .ifBlank { conflict.suggestedName }
                            homeViewModel.resolveConflict(ConflictResolution.RenameAs(safeName))
                        },
                        enabled = conflictRenameText.isNotBlank()
                    ) {
                        Text("Confirm rename", color = CyanPrimary)
                    }
                    OutlinedButton(
                        onClick = { showReplaceConfirmDialog = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Replace existing file", color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = { homeViewModel.resolveConflict(ConflictResolution.Skip) },
                        border = BorderStroke(1.dp, VaultOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skip this file", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (showReplaceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirmDialog = false },
            containerColor = VaultSurfaceVariant,
            title = { Text("Replace existing file?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("The existing file will be moved to Trash and replaced. This cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceConfirmDialog = false
                    homeViewModel.resolveConflict(ConflictResolution.Replace)
                }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Image preview dialog ───────────────────────────────────────────────────

    previewFile?.let { file ->
        ImagePreviewDialog(
            file = file,
            homeViewModel = homeViewModel,
            password = password,
            account = currentAccount,
            onDismiss = { previewFile = null }
        )
    }

    // ── Text preview dialog ────────────────────────────────────────────────────

    textPreviewFile?.let { file ->
        TextPreviewDialog(
            file = file,
            homeViewModel = homeViewModel,
            password = password,
            account = currentAccount,
            onDismiss = { textPreviewFile = null }
        )
    }

    // ── Video preview dialog ───────────────────────────────────────────────────

    videoPreviewFile?.let { file ->
        VideoPreviewDialog(
            file = file,
            homeViewModel = homeViewModel,
            password = password,
            account = currentAccount,
            onDismiss = { videoPreviewFile = null }
        )
    }

    // ── Audio preview dialog ───────────────────────────────────────────────────

    audioPreviewFile?.let { file ->
        AudioPreviewDialog(
            file = file,
            homeViewModel = homeViewModel,
            password = password,
            account = currentAccount,
            onDismiss = { audioPreviewFile = null }
        )
    }

    // ── PDF preview dialog ─────────────────────────────────────────────────────

    pdfPreviewFile?.let { file ->
        PdfPreviewDialog(
            file = file,
            homeViewModel = homeViewModel,
            password = password,
            account = currentAccount,
            onDismiss = { pdfPreviewFile = null }
        )
    }

    // ── Long-press quick action bottom sheet ──────────────────────────────────────
    longPressFile?.let { file ->
        AlertDialog(
            onDismissRequest = { longPressFile = null },
            containerColor = VaultSurfaceVariant,
            title = { Text(file.originalName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val previewAction: (() -> Unit)? = when (previewKind(file.originalMimeType)) {
                        PreviewKind.IMAGE -> { { previewFile = file; longPressFile = null } }
                        PreviewKind.VIDEO -> { { videoPreviewFile = file; longPressFile = null } }
                        PreviewKind.AUDIO -> { { audioPreviewFile = file; longPressFile = null } }
                        PreviewKind.PDF -> { { pdfPreviewFile = file; longPressFile = null } }
                        PreviewKind.TEXT -> { { textPreviewFile = file; longPressFile = null } }
                        PreviewKind.NONE -> null
                    }
                    previewAction?.let { action ->
                        TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                            Text("Preview", color = CyanPrimary)
                        }
                    }
                    TextButton(
                        onClick = {
                            longPressFile = null
                            val pwd = password; val acc = currentAccount
                            if (pwd != null && acc != null) homeViewModel.downloadAndDecrypt(file, pwd, acc)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Download", color = CyanPrimary) }
                    TextButton(
                        onClick = { showFileInfo = file; longPressFile = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Info", color = CyanPrimary) }
                    TextButton(
                        onClick = {
                            longPressFile = null
                            renameText = file.originalName
                            showRenameDialog = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Rename", color = CyanPrimary) }
                    TextButton(
                        onClick = {
                            fileToDelete = file
                            longPressFile = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { longPressFile = null }) { Text("Cancel") } }
        )
    }

    // ── File info sheet ────────────────────────────────────────────────────────────
    showFileInfo?.let { file ->
        AlertDialog(
            onDismissRequest = { showFileInfo = null },
            containerColor = VaultSurfaceVariant,
            title = { Text("File Info", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Name", file.originalName)
                    InfoRow("Type", file.originalMimeType)
                    InfoRow("Size", com.darkvault.app.ui.components.formatSize(file.size))
                    if (file.modifiedTime.isNotEmpty()) InfoRow("Modified", file.modifiedTime.take(10))
                    if (file.createdTime.isNotEmpty()) InfoRow("Uploaded", file.createdTime.take(10))
                    InfoRow("Drive ID", file.id)
                }
            },
            confirmButton = { TextButton(onClick = { showFileInfo = null }) { Text("Close") } }
        )
    }

    // ── Rename dialog ──────────────────────────────────────────────────────────────
    showRenameDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            containerColor = VaultSurfaceVariant,
            title = { Text("Rename", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(200) },
                    label = { Text("New name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = VaultOutline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        val acc = currentAccount
                        if (acc != null) homeViewModel.renameFile(file, renameText, acc)
                        showRenameDialog = null
                    }
                ) { Text("Rename", color = CyanPrimary) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") } }
        )
    }
}

// ── Task 6 — Breadcrumb Row ───────────────────────────────────────────────────

@Composable
private fun BreadcrumbRow(
    folderStack: List<FolderEntry>,
    expanded: Boolean,
    onExpand: () -> Unit,
    onNavigateTo: (FolderEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayStack: List<Any> = when {
        folderStack.size <= 3 || expanded -> folderStack
        else -> buildList {
            add(folderStack.first())
            add("…")
            addAll(folderStack.takeLast(2))
        }
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(VaultBackground)
    ) {
        items(displayStack.size) { idx ->
            val item = displayStack[idx]
            val isLast = idx == displayStack.size - 1

            when (item) {
                is FolderEntry -> {
                    TextButton(
                        onClick = { if (!isLast) onNavigateTo(item) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        enabled = !isLast
                    ) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLast) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isLast) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                "…" -> {
                    TextButton(
                        onClick = onExpand,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (!isLast) {
                Text(
                    " / ",
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultOutline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ── Task 7 — Grid item composable ─────────────────────────────────────────────

@Composable
private fun VaultGridItem(
    item: VaultFile,
    columns: Int,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = false,
    thumbnailPassword: String? = null,
    thumbnailAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null
) {
    val iconSize = if (columns == 2) 40.dp else 28.dp
    val borderColor = if (isSelected) CyanPrimary else VaultOutline
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyanPrimary.copy(alpha = 0.08f) else VaultSurfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(if (columns == 2) 8.dp else 4.dp)
            ) {
                // Thumbnail / icon area (Task 4)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VaultBackground)
                ) {
                    if (showThumbnail && !item.isFolder && HomeViewModel.isImageMime(item.originalMimeType)) {
                        VaultThumbnailImage(
                            file = item,
                            password = thumbnailPassword,
                            account = thumbnailAccount,
                            showThumbnails = true,
                            iconSize = iconSize,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            if (item.isFolder) Icons.Outlined.Folder
                            else fileTypeIcon(item.originalName, item.originalMimeType),
                            null,
                            tint = if (item.isFolder) CyanPrimary else CyanPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.originalName,
                    style = if (columns == 2) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (columns == 2) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (columns == 2 && !item.isFolder) {
                    Text(
                        com.darkvault.app.ui.components.formatSize(item.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Selection overlay
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Recent file card ──────────────────────────────────────────────────────────

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

// ── Image preview dialog with pinch-to-zoom ────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
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
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(350.dp)) {
                when {
                    loading -> CircularProgressIndicator(color = CyanPrimary, strokeCap = StrokeCap.Round)
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    imageBytes != null -> {
                        val bmp = remember(imageBytes) {
                            val bytes = imageBytes ?: return@remember null
                            val mime = file.originalMimeType.lowercase()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
                                (mime == "image/heic" || mime == "image/heif" || mime == "image/avif")) {
                                try {
                                    val source = android.graphics.ImageDecoder.createSource(
                                        java.nio.ByteBuffer.wrap(bytes)
                                    )
                                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                    }
                                } catch (_: Exception) {
                                    try { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                                    catch (_: Exception) { null }
                                }
                            } else {
                                try { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                                catch (_: Exception) { null }
                            }
                        }
                        if (bmp != null) {
                            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                offset += panChange
                            }
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = file.originalName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                                    .transformable(state = transformState)
                                    .pointerInput(Unit) {
                                        detectTapGestures(onDoubleTap = {
                                            scale = 1f
                                            offset = Offset.Zero
                                        })
                                    }
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

// ── Shimmer skeleton card ─────────────────────────────────────────────────────

@Composable
private fun ShimmerFileCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VaultSurfaceVariant.copy(alpha = alpha))
            .border(1.dp, VaultOutline.copy(alpha = alpha * 0.5f), RoundedCornerShape(12.dp))
    )
}

// ── Info row helper ───────────────────────────────────────────────────────────

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

// ── MIME helpers ──────────────────────────────────────────────────────────────

private fun isTextMime(mime: String): Boolean =
    mime.startsWith("text/") ||
    mime in setOf(
        "application/json", "application/xml", "application/xhtml+xml",
        "application/javascript", "application/x-javascript",
        "application/typescript", "application/x-sh", "application/x-python-code",
        "application/x-yaml", "application/yaml", "application/toml",
        "application/x-httpd-php", "application/x-ruby", "application/graphql",
        "application/ld+json", "application/manifest+json"
    )

private enum class PreviewKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, NONE }

private fun previewKind(mime: String): PreviewKind = when {
    HomeViewModel.isImageMime(mime) -> PreviewKind.IMAGE
    mime.startsWith("video/") -> PreviewKind.VIDEO
    mime.startsWith("audio/") -> PreviewKind.AUDIO
    mime == "application/pdf" -> PreviewKind.PDF
    isTextMime(mime) -> PreviewKind.TEXT
    else -> PreviewKind.NONE
}

// ── Text preview dialog ───────────────────────────────────────────────────────

@Composable
private fun TextPreviewDialog(
    file: VaultFile,
    homeViewModel: HomeViewModel,
    password: String?,
    account: GoogleSignInAccount?,
    onDismiss: () -> Unit
) {
    val maxPreviewBytes = 512 * 1024 // 512 KB
    var text by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.id) {
        if (password == null || account == null) {
            error = "Vault is locked"; loading = false; return@LaunchedEffect
        }
        val bytes = homeViewModel.decryptToMemory(file, password, account)
        when {
            bytes == null -> error = "Preview failed"
            bytes.size > maxPreviewBytes -> error = "File too large for preview (max 512 KB)"
            else -> text = bytes.toString(Charsets.UTF_8)
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurfaceVariant,
        title = {
            Text(
                file.originalName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(350.dp)
            ) {
                when {
                    loading -> CircularProgressIndicator(color = CyanPrimary, strokeCap = StrokeCap.Round)
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    text != null -> {
                        LazyColumn(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                            item {
                                SelectionContainer {
                                    Text(
                                        text!!,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
