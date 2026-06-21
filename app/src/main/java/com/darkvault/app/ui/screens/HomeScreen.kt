package com.darkvault.app.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.EmptyVaultState
import com.darkvault.app.ui.components.UploadProgressCard
import com.darkvault.app.ui.components.VaultFileCard
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultBackground
import com.darkvault.app.viewmodel.AuthViewModel
import com.darkvault.app.viewmodel.HomeUiState
import com.darkvault.app.viewmodel.HomeViewModel
import com.darkvault.app.viewmodel.OperationState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val password by authViewModel.masterPassword.collectAsState()
    val uiState by homeViewModel.uiState.collectAsState()
    val opState by homeViewModel.operationState.collectAsState()

    var currentAccount by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }
    var fileToDelete by remember { mutableStateOf<VaultFile?>(null) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .addOnSuccessListener { account ->
                    currentAccount = account
                    homeViewModel.loadFiles(account)
                }
                .addOnFailureListener { e ->
                    scope.launch { snackbarHostState.showSnackbar("Sign-in failed: ${e.message}") }
                }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val acc = currentAccount
        val pwd = password
        if (uri != null && acc != null && pwd != null) {
            homeViewModel.uploadFile(uri, pwd, acc, context.contentResolver)
        }
    }

    LaunchedEffect(currentAccount) {
        currentAccount?.let { homeViewModel.loadFiles(it) }
            ?: run { homeViewModel.let { /* stays NotSignedIn */ } }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "darkVault",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyanPrimary
                    )
                },
                actions = {
                    if (currentAccount != null) {
                        IconButton(onClick = {
                            currentAccount?.let { homeViewModel.loadFiles(it) }
                        }) {
                            Icon(Icons.Outlined.Refresh, "Refresh", tint = CyanPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultBackground
                )
            )
        },
        floatingActionButton = {
            if (currentAccount != null) {
                FloatingActionButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    containerColor = CyanPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Outlined.Add, "Upload file")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = VaultBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val s = opState) {
                is OperationState.InProgress -> {
                    UploadProgressCard(
                        fileName = s.fileName,
                        message = s.stage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                else -> Unit
            }

            if (currentAccount == null) {
                ConnectDriveSection(
                    onSignIn = { signInLauncher.launch(googleClient.signInIntent) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                )
            } else {
                when (val s = uiState) {
                    is HomeUiState.Loading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                color = CyanPrimary,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    is HomeUiState.Success -> {
                        if (s.files.isEmpty()) {
                            EmptyVaultState(modifier = Modifier.fillMaxSize())
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(s.files, key = { it.id }) { file ->
                                    VaultFileCard(
                                        file = file,
                                        onDownload = {
                                            val pwd = password
                                            val acc = currentAccount
                                            if (pwd != null && acc != null) {
                                                homeViewModel.downloadAndDecrypt(file, pwd, acc)
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Vault is locked")
                                                }
                                            }
                                        },
                                        onDelete = { fileToDelete = file }
                                    )
                                }
                            }
                        }
                    }
                    is HomeUiState.Error -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    s.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(16.dp))
                                CyberButton("Retry", onClick = {
                                    currentAccount?.let { homeViewModel.loadFiles(it) }
                                })
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete file?") },
            text = {
                Text(
                    "\"${file.originalName}\" will be permanently deleted from your Drive vault.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentAccount?.let { homeViewModel.deleteFile(file, it) }
                        fileToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun ConnectDriveSection(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Text(
            "Connect Google Drive",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "darkVault stores encrypted files in your Drive.\nOnly you can decrypt them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        CyberButton(
            text = "Connect Google Drive",
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
