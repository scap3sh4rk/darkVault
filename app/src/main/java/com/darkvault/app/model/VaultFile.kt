package com.darkvault.app.model

data class VaultFile(
    val id: String,
    val name: String,
    val originalName: String,
    val originalMimeType: String,
    val size: Long,
    val createdTime: String
)
