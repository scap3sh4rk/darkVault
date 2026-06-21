package com.darkvault.app.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkvault.app.VaultSession
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.drive.DriveApiClient
import com.darkvault.app.model.FilterType
import com.darkvault.app.model.SortOrder
import com.darkvault.app.model.StorageInfo
import com.darkvault.app.model.VaultFile
import com.darkvault.app.service.ActiveUpload
import com.darkvault.app.service.UploadEvent
import com.darkvault.app.service.UploadForegroundService
import com.darkvault.app.service.UploadJob
import com.darkvault.app.service.UploadState
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

// ── UI state models ───────────────────────────────────────────────────────────

sealed class HomeUiState {
    object Loading : HomeUiState()
    object NotSignedIn : HomeUiState()
    data class Success(val items: List<VaultFile>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

sealed class OperationState {
    object Idle : OperationState()
    data class InProgress(val fileName: String, val stage: String, val progress: Float = -1f) : OperationState()
    data class Done(val message: String) : OperationState()
    data class Failed(val message: String) : OperationState()
}

data class FolderEntry(val id: String, val name: String)

// ─────────────────────────────────────────────────────────────────────────────

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _rawItems = MutableStateFlow<List<VaultFile>>(emptyList())
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState

    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo

    // Task 7: Last synced timestamp
    private val _lastSyncedMs = MutableStateFlow<Long>(0L)
    val lastSyncedMs: StateFlow<Long> = _lastSyncedMs

    // ── Folder navigation ──────────────────────────────────────────────────
    /** Stack of (folderId, folderName). Root = vault root folder. */
    private val _folderStack = MutableStateFlow<List<FolderEntry>>(emptyList())
    val folderStack: StateFlow<List<FolderEntry>> = _folderStack

    val currentFolderName: String
        get() = _folderStack.value.lastOrNull()?.name ?: "darkVault"

    val canGoBack: Boolean
        get() = _folderStack.value.size > 1

    // ── Search / filter / sort ─────────────────────────────────────────────
    val searchQuery = MutableStateFlow("")
    val filterType = MutableStateFlow(FilterType.ALL)
    val sortOrder = MutableStateFlow(SortOrder.NAME_ASC)

    // Derived: apply search, filter and sort to raw items
    val displayItems: StateFlow<List<VaultFile>> = combine(
        _rawItems, searchQuery, filterType, sortOrder
    ) { items, query, filter, sort ->
        var result = items

        if (query.isNotBlank()) {
            result = result.filter {
                it.originalName.contains(query, ignoreCase = true) ||
                        it.name.contains(query, ignoreCase = true)
            }
        }

        result = when (filter) {
            FilterType.ALL -> result
            FilterType.FOLDERS -> result.filter { it.isFolder }
            FilterType.IMAGES -> result.filter { !it.isFolder && isImageMime(it.originalMimeType) }
            FilterType.VIDEOS -> result.filter { !it.isFolder && isVideoMime(it.originalMimeType) }
            FilterType.AUDIO -> result.filter { !it.isFolder && isAudioMime(it.originalMimeType) }
            FilterType.DOCUMENTS -> result.filter {
                !it.isFolder && !isImageMime(it.originalMimeType) &&
                        !isVideoMime(it.originalMimeType) && !isAudioMime(it.originalMimeType)
            }
        }

        result = when (sort) {
            SortOrder.NAME_ASC -> result.sortedWith(compareBy({ !it.isFolder }, { it.originalName.lowercase() }))
            SortOrder.NAME_DESC -> result.sortedWith(compareBy({ !it.isFolder }, { it.originalName.lowercase() })).reversed()
            SortOrder.DATE_DESC -> result.sortedWith(compareByDescending { it.modifiedTime.ifEmpty { it.createdTime } })
            SortOrder.DATE_ASC -> result.sortedWith(compareBy { it.modifiedTime.ifEmpty { it.createdTime } })
            SortOrder.SIZE_DESC -> result.sortedWith(compareBy({ !it.isFolder }, { -it.size }))
            SortOrder.SIZE_ASC -> result.sortedWith(compareBy({ !it.isFolder }, { it.size }))
            SortOrder.TYPE_ASC -> result.sortedWith(compareBy({ !it.isFolder }, { it.originalMimeType }, { it.originalName.lowercase() }))
        }

        result
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Task 4: Recents — 8 most recently modified non-folder files
    val recentItems: StateFlow<List<VaultFile>> = _rawItems.map { items ->
        items.filter { !it.isFolder && it.modifiedTime.isNotEmpty() }
            .sortedByDescending { it.modifiedTime }
            .take(8)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Upload state (from foreground service) ─────────────────────────────
    val activeUpload: StateFlow<ActiveUpload?> = UploadState.active

    // ── Selected items for batch ops ───────────────────────────────────────
    val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    private var cachedFolderId: String? = null
    private var cachedAccount: GoogleSignInAccount? = null

    init {
        // Observe upload events from the foreground service
        viewModelScope.launch {
            UploadState.events.collect { event ->
                when (event) {
                    is UploadEvent.Renamed ->
                        _operationState.value = OperationState.Done(
                            "\"${event.originalName}\" already exists — uploaded as \"${event.newName}\""
                        )
                    is UploadEvent.Completed -> {
                        val count = UploadState.queue.size + 1
                        if (count <= 1) {
                            _operationState.value = OperationState.Done("\"${event.fileName}\" uploaded")
                            cachedAccount?.let { loadFiles(it, refreshStorage = false) }
                        }
                    }
                    is UploadEvent.Failed ->
                        _operationState.value = OperationState.Failed("Failed to upload \"${event.fileName}\": ${event.reason}")
                    is UploadEvent.Cancelled ->
                        _operationState.value = OperationState.Done("Upload of \"${event.fileName}\" cancelled")
                    // UploadEvent.Duplicate is kept for backward compat but no longer emitted
                    is UploadEvent.Duplicate ->
                        _operationState.value = OperationState.Done("\"${event.fileName}\" already exists — skipped")
                    else -> Unit
                }
            }
        }
    }

    // ── File loading ───────────────────────────────────────────────────────

    fun loadFiles(account: GoogleSignInAccount, refreshStorage: Boolean = true) {
        cachedAccount = account
        VaultSession.signedInAccount = account
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            runCatching {
                val client = DriveApiClient(getApplication(), account)
                val folderId = ensureFolder(client)

                // Navigate to root on first load
                if (_folderStack.value.isEmpty()) {
                    _folderStack.value = listOf(FolderEntry(folderId, "darkVault"))
                }

                val currentId = _folderStack.value.last().id
                client.listItems(currentId)
            }.onSuccess { items ->
                _rawItems.value = items
                _uiState.value = HomeUiState.Success(items)
                _lastSyncedMs.value = System.currentTimeMillis() // Task 7
            }.onFailure { e ->
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load files")
            }

            if (refreshStorage) loadStorageInfo(account)
        }
    }

    private fun loadStorageInfo(account: GoogleSignInAccount) {
        viewModelScope.launch {
            runCatching {
                val client = DriveApiClient(getApplication(), account)
                val rootFolderId = _folderStack.value.firstOrNull()?.id ?: return@runCatching null
                client.getStorageInfo(rootFolderId)
            }.onSuccess { info ->
                _storageInfo.value = info
            }
        }
    }

    // ── Folder navigation ──────────────────────────────────────────────────

    fun openFolder(folder: VaultFile) {
        val acc = cachedAccount ?: return
        _folderStack.value = _folderStack.value + FolderEntry(folder.id, folder.originalName)
        loadItemsForCurrentFolder(acc)
    }

    fun navigateUp() {
        if (_folderStack.value.size <= 1) return
        _folderStack.value = _folderStack.value.dropLast(1)
        cachedAccount?.let { loadItemsForCurrentFolder(it) }
    }

    fun navigateTo(entry: FolderEntry) {
        val stack = _folderStack.value
        val idx = stack.indexOfFirst { it.id == entry.id }
        if (idx < 0) return
        _folderStack.value = stack.take(idx + 1)
        cachedAccount?.let { loadItemsForCurrentFolder(it) }
    }

    private fun loadItemsForCurrentFolder(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val currentId = _folderStack.value.last().id
            runCatching {
                DriveApiClient(getApplication(), account).listItems(currentId)
            }.onSuccess { items ->
                _rawItems.value = items
                _uiState.value = HomeUiState.Success(items)
                _lastSyncedMs.value = System.currentTimeMillis() // Task 7
            }.onFailure { e ->
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load folder")
            }
        }
    }

    // ── Upload ─────────────────────────────────────────────────────────────

    fun uploadFiles(
        uris: List<Uri>,
        password: String,
        account: GoogleSignInAccount,
        contentResolver: ContentResolver
    ) {
        val folderId = _folderStack.value.lastOrNull()?.id ?: return
        VaultSession.masterPassword = password
        VaultSession.signedInAccount = account

        viewModelScope.launch {
            for (uri in uris) {
                val name = resolveFileName(uri, contentResolver)
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                val size = resolveFileSize(uri, contentResolver)

                // Take persistable permission so service can read it
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) { }

                UploadState.queue.add(
                    UploadJob(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        originalName = name,
                        mimeType = mime,
                        fileSize = size,
                        folderId = folderId
                    )
                )
            }
            startUploadService()
        }
    }

    fun uploadFolder(
        folderUri: Uri,
        password: String,
        account: GoogleSignInAccount,
        contentResolver: ContentResolver
    ) {
        val folderId = _folderStack.value.lastOrNull()?.id ?: return
        VaultSession.masterPassword = password
        VaultSession.signedInAccount = account

        viewModelScope.launch {
            val folderDoc = DocumentFile.fromTreeUri(getApplication(), folderUri) ?: return@launch
            val folderName = folderDoc.name ?: "folder"
            _operationState.value = OperationState.InProgress(folderName, "Creating folder…")

            runCatching {
                val client = DriveApiClient(getApplication(), account)
                val subFolderId = client.ensureSubFolder(folderName, folderId)
                enqueueDocumentTree(folderDoc, subFolderId, contentResolver)
            }.onSuccess {
                startUploadService()
            }.onFailure { e ->
                _operationState.value = OperationState.Failed(e.message ?: "Folder upload failed")
            }
        }
    }

    private suspend fun enqueueDocumentTree(
        parentDoc: DocumentFile,
        driveFolderId: String,
        contentResolver: ContentResolver
    ) {
        val account = cachedAccount ?: return
        for (child in parentDoc.listFiles()) {
            if (child.isDirectory) {
                val client = DriveApiClient(getApplication(), account)
                val subId = client.ensureSubFolder(child.name ?: "folder", driveFolderId)
                enqueueDocumentTree(child, subId, contentResolver)
            } else if (child.isFile) {
                val name = child.name ?: continue
                val mime = child.type ?: "application/octet-stream"
                try {
                    contentResolver.takePersistableUriPermission(child.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) { }

                UploadState.queue.add(
                    UploadJob(
                        id = UUID.randomUUID().toString(),
                        uri = child.uri,
                        originalName = name,
                        mimeType = mime,
                        fileSize = child.length(),
                        folderId = driveFolderId
                    )
                )
            }
        }
    }

    private fun startUploadService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, UploadForegroundService::class.java)
        ctx.startForegroundService(intent)
    }

    fun cancelAllUploads() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, UploadForegroundService::class.java).apply {
                action = UploadForegroundService.ACTION_CANCEL_ALL
            }
        )
    }

    // ── Download ───────────────────────────────────────────────────────────

    fun downloadAndDecrypt(
        file: VaultFile,
        password: String,
        account: GoogleSignInAccount
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.InProgress(file.originalName, "Downloading…")
            runCatching {
                val client = DriveApiClient(getApplication(), account)

                val encBytes = client.downloadFileWithProgress(file.id) { dl, total ->
                    val pct = if (total > 0) dl.toFloat() / total else 0f
                    _operationState.value = OperationState.InProgress(
                        file.originalName,
                        "Downloading… ${(pct * 100).toInt()}%",
                        pct
                    )
                }

                _operationState.value = OperationState.InProgress(file.originalName, "Decrypting…")
                val decrypted = withContext(Dispatchers.Default) {
                    val out = ByteArrayOutputStream()
                    CryptoManager.decrypt(java.io.ByteArrayInputStream(encBytes), out, password, VaultSession.dek)
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

    /** Download and decrypt a file into memory (for preview). Returns null on failure. */
    suspend fun decryptToMemory(file: VaultFile, password: String, account: GoogleSignInAccount): ByteArray? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val client = DriveApiClient(getApplication(), account)
                val encBytes = client.downloadFile(file.id)
                withContext(Dispatchers.Default) {
                    val out = ByteArrayOutputStream()
                    CryptoManager.decrypt(java.io.ByteArrayInputStream(encBytes), out, password, VaultSession.dek)
                    out.toByteArray()
                }
            }.getOrNull()
        }
    }

    // Task 5: Batch download selected files
    fun downloadSelected(password: String, account: GoogleSignInAccount) {
        val ids = selectedIds.value.toList()
        val items = _rawItems.value.filter { it.id in ids && !it.isFolder }
        selectedIds.value = emptySet()
        val dek = VaultSession.dek
        viewModelScope.launch {
            _operationState.value = OperationState.InProgress("Batch download", "Downloading ${items.size} files…")
            val client = DriveApiClient(getApplication(), account)
            var done = 0
            for (file in items) {
                runCatching {
                    val encBytes = client.downloadFile(file.id)
                    val out = ByteArrayOutputStream()
                    CryptoManager.decrypt(java.io.ByteArrayInputStream(encBytes), out, password, dek)
                    val destDir = File(getApplication<Application>().getExternalFilesDir(null), "decrypted")
                    destDir.mkdirs()
                    File(destDir, file.originalName).writeBytes(out.toByteArray())
                    done++
                }
            }
            _operationState.value = OperationState.Done("Downloaded $done of ${items.size} file(s)")
        }
    }

    // Task 6: Export vault backup — decrypt all files to external storage
    fun exportVaultBackup(password: String, account: GoogleSignInAccount) {
        val dek = VaultSession.dek
        viewModelScope.launch {
            val allFiles = _rawItems.value.filter { !it.isFolder }
            if (allFiles.isEmpty()) {
                _operationState.value = OperationState.Done("Vault is empty — nothing to export")
                return@launch
            }
            val exportDir = File(
                getApplication<Application>().getExternalFilesDir(null),
                "darkVault_export_${System.currentTimeMillis()}"
            )
            exportDir.mkdirs()
            val client = DriveApiClient(getApplication(), account)
            var done = 0
            _operationState.value = OperationState.InProgress("Export", "Exporting 0/${allFiles.size}…")
            for (file in allFiles) {
                runCatching {
                    val enc = client.downloadFile(file.id)
                    val out = ByteArrayOutputStream()
                    CryptoManager.decrypt(java.io.ByteArrayInputStream(enc), out, password, dek)
                    File(exportDir, file.originalName).writeBytes(out.toByteArray())
                    done++
                    _operationState.value = OperationState.InProgress("Export", "Exporting $done/${allFiles.size}…")
                }
            }
            _operationState.value = OperationState.Done("Exported $done file(s) to ${exportDir.absolutePath}")
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    /** Moves file to Drive trash (soft delete). */
    fun deleteFile(file: VaultFile, account: GoogleSignInAccount) {
        viewModelScope.launch {
            _operationState.value = OperationState.InProgress(file.originalName, "Moving to trash…")
            runCatching {
                DriveApiClient(getApplication(), account).trashFile(file.id)
            }.onSuccess {
                _operationState.value = OperationState.Done("\"${file.originalName}\" moved to trash")
                _rawItems.value = _rawItems.value.filter { it.id != file.id }
                _uiState.value = HomeUiState.Success(_rawItems.value)
            }.onFailure { e ->
                _operationState.value = OperationState.Failed(e.message ?: "Trash failed")
            }
        }
    }

    /** Permanently deletes a file from Drive. */
    fun permanentDeleteFile(file: VaultFile, account: GoogleSignInAccount) {
        viewModelScope.launch {
            _operationState.value = OperationState.InProgress(file.originalName, "Deleting permanently…")
            runCatching {
                DriveApiClient(getApplication(), account).deleteFile(file.id)
            }.onSuccess {
                _operationState.value = OperationState.Done("\"${file.originalName}\" permanently deleted")
                _rawItems.value = _rawItems.value.filter { it.id != file.id }
                _uiState.value = HomeUiState.Success(_rawItems.value)
            }.onFailure { e ->
                _operationState.value = OperationState.Failed(e.message ?: "Delete failed")
            }
        }
    }

    /** Restores a trashed file. */
    fun restoreFile(file: VaultFile, account: GoogleSignInAccount) {
        viewModelScope.launch {
            _operationState.value = OperationState.InProgress(file.originalName, "Restoring…")
            runCatching {
                DriveApiClient(getApplication(), account).restoreFile(file.id)
            }.onSuccess {
                _operationState.value = OperationState.Done("\"${file.originalName}\" restored")
                cachedAccount?.let { loadFiles(it, refreshStorage = false) }
            }.onFailure { e ->
                _operationState.value = OperationState.Failed(e.message ?: "Restore failed")
            }
        }
    }

    /** Moves selected items to trash (soft delete). */
    fun deleteSelected(account: GoogleSignInAccount) {
        val ids = selectedIds.value.toList()
        val items = _rawItems.value.filter { it.id in ids }
        selectedIds.value = emptySet()
        viewModelScope.launch {
            val client = DriveApiClient(getApplication(), account)
            var trashed = 0
            items.forEach { file ->
                runCatching { client.trashFile(file.id) }.onSuccess { trashed++ }
            }
            _rawItems.value = _rawItems.value.filter { it.id !in ids }
            _uiState.value = HomeUiState.Success(_rawItems.value)
            _operationState.value = OperationState.Done("$trashed item(s) moved to trash")
        }
    }

    // ── Selection ──────────────────────────────────────────────────────────

    fun toggleSelection(id: String) {
        val current = selectedIds.value.toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        selectedIds.value = current
    }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun selectAll() {
        selectedIds.value = displayItems.value.map { it.id }.toSet()
    }

    // ── Misc ───────────────────────────────────────────────────────────────

    fun clearOperationState() { _operationState.value = OperationState.Idle }

    private suspend fun ensureFolder(client: DriveApiClient): String {
        val saved = cachedFolderId ?: prefs.vaultFolderId.first()
        val folderId = client.ensureVaultFolder(saved)
        if (folderId != saved) prefs.saveVaultFolderId(folderId)
        cachedFolderId = folderId
        return folderId
    }

    private fun resolveFileName(uri: Uri, cr: ContentResolver): String {
        val cursor = cr.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (idx >= 0) it.getString(idx) else null
        } ?: uri.lastPathSegment ?: "file"
    }

    private fun resolveFileSize(uri: Uri, cr: ContentResolver): Long {
        val cursor = cr.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            it.moveToFirst()
            if (idx >= 0) it.getLong(idx) else 0L
        } ?: 0L
    }

    // ── MIME type helpers ──────────────────────────────────────────────────

    companion object {
        fun isImageMime(mime: String) = mime.startsWith("image/")
        fun isVideoMime(mime: String) = mime.startsWith("video/")
        fun isAudioMime(mime: String) = mime.startsWith("audio/")
    }
}
