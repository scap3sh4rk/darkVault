package com.darkvault.app.model

data class VaultFile(
    val id: String,
    val name: String,             // Drive file name (e.g. "photo.jpg.vault") or folder name
    val originalName: String,     // From appProperties; equals name for folders
    val originalMimeType: String, // From appProperties; "application/vnd.google-apps.folder" for folders
    val size: Long,               // Encrypted size on Drive (0 for folders)
    val createdTime: String,      // ISO 8601
    val modifiedTime: String = "",
    val isFolder: Boolean = false,
    val thumbnailFileId: String? = null  // Drive ID of the companion _thumb.vault file, if any
)

data class StorageInfo(
    val usedByVaultBytes: Long,
    val driveTotalUsedBytes: Long,
    val driveLimitBytes: Long  // -1 if unlimited
)
