package com.darkvault.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.components.VaultFileCard
import com.darkvault.app.viewmodel.HomeViewModel
import com.darkvault.app.viewmodel.ViewLayout
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OfflineFilesScreen(
    homeViewModel: HomeViewModel,
    password: String?,
    account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?,
    onBack: () -> Unit,
    onDownloadFile: (VaultFile) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val offlineFiles by homeViewModel.offlineFiles.collectAsState()
    var viewLayout by remember { mutableStateOf(ViewLayout.LIST) }
    var actionFile by remember { mutableStateOf<VaultFile?>(null) }
    var fileToUnpin by remember { mutableStateOf<VaultFile?>(null) }

    var previewFile by remember { mutableStateOf<VaultFile?>(null) }
    var textPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var videoPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var audioPreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    var pdfPreviewFile by remember { mutableStateOf<VaultFile?>(null) }

    fun openPreview(file: VaultFile) = when (previewKind(file.originalMimeType)) {
        PreviewKind.IMAGE -> previewFile = file
        PreviewKind.VIDEO -> videoPreviewFile = file
        PreviewKind.AUDIO -> audioPreviewFile = file
        PreviewKind.PDF   -> pdfPreviewFile = file
        PreviewKind.TEXT  -> textPreviewFile = file
        PreviewKind.NONE  -> actionFile = file
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Offline Files",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewLayout = when (viewLayout) {
                            ViewLayout.LIST  -> ViewLayout.GRID2
                            ViewLayout.GRID2 -> ViewLayout.GRID3
                            ViewLayout.GRID3 -> ViewLayout.LIST
                        }
                    }) {
                        Icon(
                            imageVector = when (viewLayout) {
                                ViewLayout.LIST  -> Icons.Outlined.GridView
                                ViewLayout.GRID2 -> Icons.Outlined.GridOn
                                ViewLayout.GRID3 -> Icons.AutoMirrored.Outlined.ViewList
                            },
                            contentDescription = "Change layout",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (offlineFiles.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        "No offline files",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Long-press any file and tap \"Make Available Offline\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            AnimatedContent(targetState = viewLayout, label = "layout") { layout ->
                when (layout) {
                    ViewLayout.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().padding(padding)
                        ) {
                            item {
                                Text(
                                    "${offlineFiles.size} file${if (offlineFiles.size != 1) "s" else ""} saved for offline access",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                            items(offlineFiles, key = { it.id }) { file ->
                                val kind = previewKind(file.originalMimeType)
                                VaultFileCard(
                                    file             = file,
                                    onClick          = { openPreview(file) },
                                    onPreview        = if (kind != PreviewKind.NONE) ({ openPreview(file) }) else null,
                                    onDownload       = {
                                        onDownloadFile(file)
                                        scope.launch { snackbarHost.showSnackbar("Saving \"${file.originalName}\" to Downloads…") }
                                    },
                                    onDelete         = { fileToUnpin = file },
                                    onMoreActions    = { actionFile = file },
                                    onLongPress      = { actionFile = file },
                                    showThumbnail    = true,
                                    thumbnailPassword = password,
                                    thumbnailAccount  = account
                                )
                            }
                        }
                    }

                    ViewLayout.GRID2, ViewLayout.GRID3 -> {
                        val columns = if (layout == ViewLayout.GRID2) 2 else 3
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().padding(padding)
                        ) {
                            item(span = { GridItemSpan(columns) }) {
                                Text(
                                    "${offlineFiles.size} file${if (offlineFiles.size != 1) "s" else ""} saved for offline access",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                            items(offlineFiles, key = { it.id }) { file ->
                                VaultGridItem(
                                    item             = file,
                                    columns          = columns,
                                    isSelected       = false,
                                    isSelectionMode  = false,
                                    onTap            = { openPreview(file) },
                                    onLongPress      = { actionFile = file },
                                    onMoreActions    = { actionFile = file },
                                    showThumbnail    = true,
                                    thumbnailPassword = password,
                                    thumbnailAccount  = account
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Action dialog: preview, save, unpin
    actionFile?.let { file ->
        val kind = previewKind(file.originalMimeType)
        AlertDialog(
            onDismissRequest = { actionFile = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text(
                    file.originalName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (kind != PreviewKind.NONE) {
                        TextButton(
                            onClick = { openPreview(file); actionFile = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Preview", color = MaterialTheme.colorScheme.primary) }
                    }
                    TextButton(
                        onClick = {
                            actionFile = null
                            onDownloadFile(file)
                            scope.launch { snackbarHost.showSnackbar("Saving \"${file.originalName}\" to Downloads…") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save to Downloads", color = MaterialTheme.colorScheme.primary) }
                    TextButton(
                        onClick = { fileToUnpin = file; actionFile = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Remove from Offline", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { actionFile = null }) { Text("Cancel") } }
        )
    }

    // Unpin confirmation
    fileToUnpin?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToUnpin = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Remove from Offline?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "\"${file.originalName}\" will no longer be cached on device. It will still be available on Google Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.setOfflinePinned(file.id, false)
                    fileToUnpin = null
                    scope.launch { snackbarHost.showSnackbar("Removed from offline storage") }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { fileToUnpin = null }) { Text("Cancel") } }
        )
    }

    // Preview dialogs — all work offline because decryptToMemory checks LocalVaultCache first
    previewFile?.let { ImagePreviewDialog(it, homeViewModel, password, account, onDismiss = { previewFile = null }) }
    textPreviewFile?.let { TextPreviewDialog(it, homeViewModel, password, account, onDismiss = { textPreviewFile = null }) }
    videoPreviewFile?.let { VideoPreviewDialog(it, homeViewModel, password, account, onDismiss = { videoPreviewFile = null }) }
    audioPreviewFile?.let { AudioPreviewDialog(it, homeViewModel, password, account, onDismiss = { audioPreviewFile = null }) }
    pdfPreviewFile?.let { PdfPreviewDialog(it, homeViewModel, password, account, onDismiss = { pdfPreviewFile = null }) }
}
