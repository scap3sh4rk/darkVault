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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.darkvault.app.BuildConfig
import com.darkvault.app.R
import com.darkvault.app.VaultSession
import com.darkvault.app.model.FilterType
import com.darkvault.app.service.ConflictResolution
import com.darkvault.app.model.SortOrder
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.EmptySearchState
import com.darkvault.app.ui.components.EmptyVaultState
import com.darkvault.app.ui.components.FilterChipRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween as animTween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darkvault.app.ui.theme.CyanPrimary as CyanAccent

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToOffline: () -> Unit = {},
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
    val canGoBack by homeViewModel.canGoBack.collectAsState()
    val isOffline by homeViewModel.isOffline.collectAsState()
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
    var showSearch by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showSearch) {
        if (showSearch) { delay(120); searchFocusRequester.requestFocus() }
    }
    var showDeleteSelected by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<VaultFile?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var longPressFile by remember { mutableStateOf<VaultFile?>(null) }
    var longPressFilePinned by remember { mutableStateOf(false) }
    var showFileInfo by remember { mutableStateOf<VaultFile?>(null) }
    var showRenameDialog by remember { mutableStateOf<VaultFile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var textPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var videoPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var audioPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var pdfPreviewFile by remember { mutableStateOf<VaultFile?>(null) }

    // Task 6 — breadcrumb expansion state
    var breadcrumbExpanded by remember { mutableStateOf(false) }

    // ── Vault-open iris animation (plays once per Home entry) ─────────────────
    var irisVisible by remember { mutableStateOf(true) }
    val ring1 = remember { Animatable(0f) }
    val ring2 = remember { Animatable(0f) }
    val ring3 = remember { Animatable(0f) }
    val irisAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(80L)
        launch { ring1.animateTo(1f, animTween(480, easing = FastOutSlowInEasing)) }
        delay(90L)
        launch { ring2.animateTo(1f, animTween(480, easing = FastOutSlowInEasing)) }
        delay(90L)
        launch { ring3.animateTo(1f, animTween(480, easing = FastOutSlowInEasing)) }
        delay(520L)
        irisAlpha.animateTo(0f, animTween(260))
        irisVisible = false
    }

    // Back intercept: clear selection first; if not selecting, go up one folder level
    BackHandler(enabled = isSelectionMode || canGoBack) {
        if (isSelectionMode) homeViewModel.clearSelection()
        else homeViewModel.navigateUp()
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

    LaunchedEffect(longPressFile) {
        val f = longPressFile
        longPressFilePinned = if (f != null && !f.isFolder)
            com.darkvault.app.cache.LocalVaultCache.isPinned(context, f.id)
        else false
    }

    // Recovery key first-time display
    recoveryKeyToShow?.let { key ->
        AlertDialog(
            onDismissRequest = { /* force user to acknowledge */ },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { authViewModel.clearRecoveryKey() }) {
                    Text("I have saved it", color = MaterialTheme.colorScheme.primary)
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
                    Text("Copy", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        // Task 9 — back arrow only when inside a sub-folder
                        if (canGoBack) {
                            IconButton(onClick = { homeViewModel.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Up", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    title = {
                        if (isSelectionMode) {
                            Text("${selectedIds.size} selected", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("darkVault", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                if (folderStack.size <= 1) {
                                    currentAccount?.email?.let { email ->
                                        Text(
                                            email,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
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
                                Icon(Icons.Outlined.SelectAll, "Select all", tint = MaterialTheme.colorScheme.primary)
                            }
                            // Task 3 — download button with count badge
                            BadgedBox(
                                badge = {
                                    if (downloadableCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
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
                                        tint = if (downloadableCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                                    )
                                }
                            }
                            IconButton(onClick = { showDeleteSelected = true }) {
                                Icon(Icons.Outlined.DeleteSweep, "Move selected to trash", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { homeViewModel.clearSelection() }) {
                                Icon(Icons.Outlined.Close, "Cancel selection", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search, "Search", tint = MaterialTheme.colorScheme.primary)
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Outlined.Sort, "Sort", tint = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    SortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    order.label,
                                                    color = if (sortOrder == order) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, "More options", tint = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                    // Layout options at top of menu
                                    DropdownMenuItem(
                                        text = { Text("List view", color = if (viewLayout == ViewLayout.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.ViewList, null, tint = if (viewLayout == ViewLayout.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                        onClick = { homeViewModel.setViewLayout(ViewLayout.LIST); showMoreMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Grid (2 columns)", color = if (viewLayout == ViewLayout.GRID2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.GridView, null, tint = if (viewLayout == ViewLayout.GRID2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                        onClick = { homeViewModel.setViewLayout(ViewLayout.GRID2); showMoreMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Grid (3 columns)", color = if (viewLayout == ViewLayout.GRID3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.GridOn, null, tint = if (viewLayout == ViewLayout.GRID3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                        onClick = { homeViewModel.setViewLayout(ViewLayout.GRID3); showMoreMenu = false }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                                        text = { Text("Offline files") },
                                        leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onNavigateToOffline()
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
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.outline) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { homeViewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Outlined.Close, "Clear", tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(searchFocusRequester)
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
                        containerColor = MaterialTheme.colorScheme.primary,
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        title = { Text("New Folder", style = MaterialTheme.typography.titleMedium) },
                        text = {
                            OutlinedTextField(
                                value = newFolderName,
                                onValueChange = { newFolderName = it.take(200) },
                                label = { Text("Folder name") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                            ) { Text("Create", color = MaterialTheme.colorScheme.primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
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
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Offline banner
            AnimatedVisibility(visible = isOffline, enter = expandVertically(), exit = shrinkVertically()) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Color(0xFFF59E0B).copy(alpha = 0.15f),
                            androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "You're offline — showing cached files",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFFF59E0B)
                        )
                    }
                }
            }

            val isRefreshing = uiState is HomeUiState.Loading
            val pullRefreshState = rememberPullToRefreshState()

            val showRecents = folderStack.size == 1 &&
                    searchQuery.isBlank() &&
                    filterType == FilterType.ALL &&
                    recentItems.isNotEmpty()

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
                                        if (showRecents) {
                                            item(key = "recents_header") {
                                                Text(
                                                    "Recents",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    contentPadding = PaddingValues(bottom = 8.dp)
                                                ) {
                                                    items(recentItems, key = { "recent_${it.id}" }) { file ->
                                                        RecentFileCard(
                                                            file = file,
                                                            isSelected = file.id in selectedIds,
                                                            showThumbnail = showThumbnails,
                                                            thumbnailPassword = password,
                                                            thumbnailAccount = currentAccount,
                                                            onClick = {
                                                                if (isSelectionMode) {
                                                                    homeViewModel.toggleSelection(file.id)
                                                                } else {
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
                                                            },
                                                            onLongPress = { homeViewModel.toggleSelection(file.id) }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        item(key = "all_files_header") {
                                            Text(
                                                "All Files",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                            )
                                        }

                                        items(displayItems, key = { it.id }) { item ->
                                            if (item.isFolder) {
                                                VaultFolderCard(
                                                    folder = item,
                                                    onOpen = { homeViewModel.openFolder(item) },
                                                    onDelete = { fileToDelete = item },
                                                    onMoreActions = { longPressFile = item },
                                                    isSelected = item.id in selectedIds,
                                                    onToggleSelect = if (isSelectionMode) ({ homeViewModel.toggleSelection(item.id) }) else null,
                                                    onLongPress = { homeViewModel.toggleSelection(item.id) }
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
                                                        PreviewKind.NONE -> longPressFile = item
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
                                                    onMoreActions = { longPressFile = item },
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            homeViewModel.toggleSelection(item.id)
                                                        } else {
                                                            openPreview()
                                                        }
                                                    },
                                                    onLongPress = { homeViewModel.toggleSelection(item.id) },
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
                                        if (showRecents) {
                                            item(span = { GridItemSpan(maxLineSpan) }, key = "recents_header") {
                                                Column {
                                                    Text(
                                                        "Recents",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                    )
                                                    LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        contentPadding = PaddingValues(bottom = 8.dp)
                                                    ) {
                                                        items(recentItems, key = { "recent_${it.id}" }) { file ->
                                                            RecentFileCard(
                                                                file = file,
                                                                isSelected = file.id in selectedIds,
                                                                showThumbnail = showThumbnails,
                                                                thumbnailPassword = password,
                                                                thumbnailAccount = currentAccount,
                                                                onClick = {
                                                                    if (isSelectionMode) {
                                                                        homeViewModel.toggleSelection(file.id)
                                                                    } else {
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
                                                                },
                                                                onLongPress = { homeViewModel.toggleSelection(file.id) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        item(span = { GridItemSpan(maxLineSpan) }, key = "all_files_header") {
                                            Text(
                                                "All Files",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                            )
                                        }

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
                                                            PreviewKind.NONE -> longPressFile = item
                                                        }
                                                    }
                                                },
                                                onLongPress = { homeViewModel.toggleSelection(item.id) },
                                                onMoreActions = { longPressFile = item },
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

                }
                else -> Unit
            }
            } // end inner Column
            } // end PullToRefreshBox

            // Storage bar with percentage indicator
            storageInfo?.let { info ->
                if (info.driveLimitBytes > 0) {
                    val driveFraction = (info.driveTotalUsedBytes.toFloat() / info.driveLimitBytes).coerceIn(0f, 1f)
                    val vaultFraction = (info.usedByVaultBytes.toFloat() / info.driveLimitBytes).coerceIn(0f, 1f)
                    val drivePercent = (driveFraction * 100).toInt()
                    Text(
                        "$drivePercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 40.dp, bottom = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 6.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(driveFraction)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(vaultFraction)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Column {
                    Text(
                        "\"${conflict.originalName}\" already exists",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
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
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                        Text("Confirm rename", color = MaterialTheme.colorScheme.primary)
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
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                            Text("Preview", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (!file.isFolder) {
                        TextButton(
                            onClick = {
                                longPressFile = null
                                val pwd = password; val acc = currentAccount
                                if (pwd != null && acc != null) homeViewModel.downloadAndDecrypt(file, pwd, acc)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Download", color = MaterialTheme.colorScheme.primary) }
                        TextButton(
                            onClick = {
                                val newPinned = !longPressFilePinned
                                homeViewModel.setOfflinePinned(file.id, newPinned)
                                longPressFile = null
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (newPinned) "Saving for offline…" else "Removed from offline storage"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (longPressFilePinned) "Remove from Offline" else "Make Available Offline",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TextButton(
                        onClick = { showFileInfo = file; longPressFile = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Info", color = MaterialTheme.colorScheme.primary) }
                    TextButton(
                        onClick = {
                            longPressFile = null
                            renameText = file.originalName
                            showRenameDialog = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Rename", color = MaterialTheme.colorScheme.primary) }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("File Info", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Name", file.originalName)
                    InfoRow("Type", if (file.isFolder) "Folder" else file.originalMimeType)
                    if (!file.isFolder) InfoRow("Size", com.darkvault.app.ui.components.formatSize(file.size))
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Rename", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(200) },
                    label = { Text("New name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                ) { Text("Rename", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") } }
        )
    }

    // ── Vault-open iris overlay ───────────────────────────────────────────────
    val irisColor = MaterialTheme.colorScheme.primary
    if (irisVisible) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = irisAlpha.value }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = maxOf(size.width, size.height)

            val r1 = ring1.value * maxR * 0.52f
            if (r1 > 0f) drawCircle(
                color = irisColor.copy(alpha = (1f - ring1.value) * 0.55f),
                radius = r1, center = Offset(cx, cy),
                style = Stroke(2.5.dp.toPx())
            )
            val r2 = ring2.value * maxR * 0.70f
            if (r2 > 0f) drawCircle(
                color = irisColor.copy(alpha = (1f - ring2.value) * 0.35f),
                radius = r2, center = Offset(cx, cy),
                style = Stroke(1.5.dp.toPx())
            )
            val r3 = ring3.value * maxR * 0.88f
            if (r3 > 0f) drawCircle(
                color = irisColor.copy(alpha = (1f - ring3.value) * 0.20f),
                radius = r3, center = Offset(cx, cy),
                style = Stroke(1.dp.toPx())
            )
            // Brief flash fill
            if (ring1.value < 0.35f) {
                drawRect(irisColor.copy(alpha = (0.35f - ring1.value) * 0.10f))
            }
        }
    }

    } // end outer Box

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
            .background(MaterialTheme.colorScheme.background)
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
                            color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ── Task 7 — Grid item composable ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VaultGridItem(
    item: VaultFile,
    columns: Int,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMoreActions: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = false,
    thumbnailPassword: String? = null,
    thumbnailAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null
) {
    val iconSize = if (columns == 2) 40.dp else 28.dp
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    // Fixed text area heights prevent uneven rows when file names differ in length
    val textAreaHeight = if (columns == 2) 52.dp else 20.dp

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(if (columns == 2) 8.dp else 4.dp)
            ) {
                // Thumbnail / icon area
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    VaultThumbnailImage(
                        file = item,
                        password = thumbnailPassword,
                        account = thumbnailAccount,
                        showThumbnails = showThumbnail,
                        iconSize = iconSize,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Fixed-height text area so every card in a row is the same height
                Box(
                    modifier = Modifier.fillMaxWidth().height(textAreaHeight),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column {
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
                }
            }
            // Selection overlay
            if (isSelectionMode) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    if (isSelected) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    } else {
                        Box(Modifier.size(20.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    }
                }
            } else if (onMoreActions != null) {
                // 3-dot action button — only visible outside selection mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(bottomStart = 8.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .clickable { onMoreActions() }
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        "More actions",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── Recent file card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentFileCard(
    file: VaultFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = false,
    thumbnailPassword: String? = null,
    thumbnailAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .width(100.dp)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
                VaultThumbnailImage(
                    file = file,
                    password = thumbnailPassword,
                    account = thumbnailAccount,
                    showThumbnails = showThumbnail,
                    iconSize = 24.dp,
                    modifier = Modifier.fillMaxSize()
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
internal fun ImagePreviewDialog(
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
                ) {
                    Text(
                        file.originalName,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (!loading && error == null) {
                        Text(
                            "${(scale * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.White)
                    }
                }

                // Image area
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeCap = StrokeCap.Round)
                        error != null -> Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
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
                                    scale = (scale * zoomChange).coerceIn(1f, 8f)
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
                                                if (scale > 1f) {
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                } else {
                                                    scale = 2.5f
                                                }
                                            })
                                        }
                                )
                            } else {
                                Text("Cannot decode image", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Zoom controls — only when image is loaded
                if (!loading && error == null && imageBytes != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            scale = (scale / 1.4f).coerceAtLeast(1f)
                            if (scale <= 1f) { scale = 1f; offset = Offset.Zero }
                        }) {
                            Icon(Icons.Outlined.ZoomOut, "Zoom out", tint = Color.White)
                        }
                        TextButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                            Text("Reset", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { scale = (scale * 1.4f).coerceAtMost(8f) }) {
                            Icon(Icons.Outlined.ZoomIn, "Zoom in", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = alpha * 0.5f), RoundedCornerShape(12.dp))
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

internal fun isTextMime(mime: String): Boolean =
    mime.startsWith("text/") ||
    mime in setOf(
        "application/json", "application/xml", "application/xhtml+xml",
        "application/javascript", "application/x-javascript",
        "application/typescript", "application/x-sh", "application/x-python-code",
        "application/x-yaml", "application/yaml", "application/toml",
        "application/x-httpd-php", "application/x-ruby", "application/graphql",
        "application/ld+json", "application/manifest+json"
    )

internal enum class PreviewKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, NONE }

internal fun previewKind(mime: String): PreviewKind = when {
    HomeViewModel.isImageMime(mime) -> PreviewKind.IMAGE
    mime.startsWith("video/") -> PreviewKind.VIDEO
    mime.startsWith("audio/") -> PreviewKind.AUDIO
    mime == "application/pdf" -> PreviewKind.PDF
    isTextMime(mime) -> PreviewKind.TEXT
    else -> PreviewKind.NONE
}

// ── Text preview dialog ───────────────────────────────────────────────────────

@Composable
internal fun TextPreviewDialog(
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
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                    loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeCap = StrokeCap.Round)
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
