package com.darkvault.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.components.fileTypeIcon
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultBackground
import com.darkvault.app.ui.theme.VaultOutline
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.viewmodel.HomeViewModel
import com.darkvault.app.viewmodel.OperationState
import com.google.android.gms.auth.api.signin.GoogleSignIn

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val currentAccount = remember { GoogleSignIn.getLastSignedInAccount(context) }
    val trashedItems by homeViewModel.trashedItems.collectAsState()
    val trashLoading by homeViewModel.trashLoading.collectAsState()
    val opState by homeViewModel.operationState.collectAsState()

    var fileToRestore by remember { mutableStateOf<VaultFile?>(null) }
    var fileToPermDelete by remember { mutableStateOf<VaultFile?>(null) }

    LaunchedEffect(Unit) {
        currentAccount?.let { homeViewModel.loadTrashedFiles(it) }
    }

    LaunchedEffect(opState) {
        when (val s = opState) {
            is OperationState.Done -> {
                snackbarHost.showSnackbar(s.message)
                homeViewModel.clearOperationState()
                currentAccount?.let { homeViewModel.loadTrashedFiles(it) }
            }
            is OperationState.Failed -> {
                snackbarHost.showSnackbar(s.message)
                homeViewModel.clearOperationState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Trash", style = MaterialTheme.typography.titleLarge, color = CyanPrimary)
                        Text(
                            "${trashedItems.size} item(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultOutline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = CyanPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { currentAccount?.let { homeViewModel.loadTrashedFiles(it) } }) {
                        Icon(Icons.Outlined.Refresh, "Refresh", tint = CyanPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBackground)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = VaultBackground
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                trashLoading -> {
                    CircularProgressIndicator(
                        color = CyanPrimary,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.size(40.dp).align(Alignment.Center)
                    )
                }
                trashedItems.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(32.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, null, tint = VaultOutline, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Trash is empty", style = MaterialTheme.typography.bodyMedium, color = VaultOutline)
                        Text(
                            "Files moved to trash appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = VaultOutline.copy(alpha = 0.6f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashedItems, key = { it.id }) { file ->
                            TrashedFileCard(
                                file = file,
                                onRestore = { fileToRestore = file },
                                onDelete = { fileToPermDelete = file }
                            )
                        }
                    }
                }
            }
        }
    }

    // Restore dialog
    fileToRestore?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToRestore = null },
            containerColor = VaultSurfaceVariant,
            title = { Text("Restore file?") },
            text = { Text("\"${file.originalName}\" will be moved back to your vault.") },
            confirmButton = {
                TextButton(onClick = {
                    currentAccount?.let { homeViewModel.restoreFile(file, it) }
                    fileToRestore = null
                }) { Text("Restore", color = CyanPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { fileToRestore = null }) { Text("Cancel") }
            }
        )
    }

    // Permanent delete dialog
    fileToPermDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToPermDelete = null },
            containerColor = VaultSurfaceVariant,
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
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { fileToPermDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrashedFileCard(
    file: VaultFile,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VaultBackground)
            ) {
                Icon(
                    fileTypeIcon(file.originalName, file.originalMimeType),
                    null,
                    tint = VaultOutline,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.originalName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (file.modifiedTime.isNotEmpty()) {
                    Text(
                        "Trashed · ${file.modifiedTime.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Outlined.RestoreFromTrash, "Restore", tint = CyanPrimary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteForever, "Delete permanently", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
