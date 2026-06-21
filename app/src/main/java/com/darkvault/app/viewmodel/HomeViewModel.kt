package com.darkvault.app.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.drive.DriveApiClient
import com.darkvault.app.model.VaultFile
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

sealed class HomeUiState {
    object Loading : HomeUiState()
    object NotSignedIn : HomeUiState()
    data class Success(val files: List<VaultFile>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

sealed class OperationState {
    object Idle : OperationState()
    data class InProgress(val fileName: String, val stage: String) : OperationState()
    data class Done(val message: String) : OperationState()
    data class Failed(val message: String) : OperationState()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState

    private var cachedFolderId: String? = null

    fun loadFiles(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            runCatching {
                val client = DriveApiClient(getApplication(), account)
                val folderId = ensureFolder(client)
                client.listFiles(folderId)
            }.onSuccess { files ->
                _uiState.value = HomeUiState.Success(files)
            }.onFailure { e ->
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load files")
            }
        }
    }

    fun uploadFile(
        uri: Uri,
        password: String,
        account: GoogleSignInAccount,
        contentResolver: ContentResolver
    ) {
        viewModelScope.launch {
            val fileName = resolveFileName(uri, contentResolver)
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            _operationState.value = OperationState.InProgress(fileName, "Encrypting…")

            runCatching {
                val encryptedBytes = withContext(Dispatchers.Default) {
                    val plain = contentResolver.openInputStream(uri)!!
                    val out = ByteArrayOutputStream()
                    CryptoManager.encrypt(plain, out, password)
                    out.toByteArray()
                }

                _operationState.value = OperationState.InProgress(fileName, "Uploading…")

                val client = DriveApiClient(getApplication(), account)
                val folderId = ensureFolder(client)
                client.uploadFile(
                    fileName = "$fileName.vault",
                    encryptedBytes = encryptedBytes,
                    originalName = fileName,
                    originalMimeType = mimeType,
                    folderId = folderId
                )
            }.onSuccess {
                _operationState.value = OperationState.Done("$fileName uploaded")
                loadFiles(account)
            }.onFailure { e ->
                _operationState.value = OperationState.Failed(e.message ?: "Upload failed")
            }
        }
    }

    fun downloadAndDecrypt(
        file: VaultFile,
        password: String,
        account: GoogleSignInAccount
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.InProgress(file.originalName, "Downloading…")

            runCatching {
                val client = DriveApiClient(getApplication(), account)
                val encryptedBytes = client.downloadFile(file.id)

                _operationState.value = OperationState.InProgress(file.originalName, "Decrypting…")

                val decrypted = withContext(Dispatchers.Default) {
                    val out = ByteArrayOutputStream()
                    CryptoManager.decrypt(ByteArrayInputStream(encryptedBytes), out, password)
                    out.toByteArray()
                }

                val destDir = File(getApplication<Application>().getExternalFilesDir(null), "decrypted")
                destDir.mkdirs()
                val destFile = File(destDir, file.originalName)
                destFile.writeBytes(decrypted)
                destFile.absolutePath
            }.onSuccess { path ->
                _operationState.value = OperationState.Done("Saved to $path")
            }.onFailure { e ->
                _operationState.value = OperationState.Failed(
                    if (e.message?.contains("Tag mismatch") == true || e.message?.contains("mac check") == true)
                        "Wrong password — decryption failed"
                    else e.message ?: "Download failed"
                )
            }
        }
    }

    fun deleteFile(file: VaultFile, account: GoogleSignInAccount) {
        viewModelScope.launch {
            _operationState.value = OperationState.InProgress(file.originalName, "Deleting…")
            runCatching {
                val client = DriveApiClient(getApplication(), account)
                client.deleteFile(file.id)
            }.onSuccess {
                _operationState.value = OperationState.Done("${file.originalName} deleted")
                val current = _uiState.value
                if (current is HomeUiState.Success) {
                    _uiState.value = HomeUiState.Success(current.files.filter { it.id != file.id })
                }
            }.onFailure { e ->
                _operationState.value = OperationState.Failed(e.message ?: "Delete failed")
            }
        }
    }

    fun clearOperationState() { _operationState.value = OperationState.Idle }

    private suspend fun ensureFolder(client: DriveApiClient): String {
        val saved = cachedFolderId ?: prefs.vaultFolderId.first()
        val folderId = client.ensureVaultFolder(saved)
        if (folderId != saved) {
            prefs.saveVaultFolderId(folderId)
        }
        cachedFolderId = folderId
        return folderId
    }

    private fun resolveFileName(uri: Uri, contentResolver: ContentResolver): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (idx >= 0) it.getString(idx) else null
        } ?: uri.lastPathSegment ?: "file"
    }
}
