package com.darkvault.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkvault.app.model.FilterType
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultBackground
import com.darkvault.app.ui.theme.VaultOutline
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.viewmodel.HomeViewModel

// ── Text field ─────────────────────────────────────────────────────────────────

@Composable
fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isError,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                focusedLabelColor = CyanPrimary,
                focusedLeadingIconColor = CyanPrimary,
                cursorColor = CyanPrimary,
                unfocusedBorderColor = VaultOutline,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (isError && errorMessage != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ── Button ─────────────────────────────────────────────────────────────────────

@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CyanPrimary,
            contentColor = Color(0xFF00363F),
            disabledContainerColor = VaultOutline,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier.height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF00363F),
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
        } else {
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
        }
    }
}

// ── Logo ───────────────────────────────────────────────────────────────────────

@Composable
fun VaultLogo(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "alpha"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.radialGradient(listOf(CyanPrimary.copy(alpha = pulse * 0.3f), VaultSurfaceVariant)))
                .border(1.dp, Brush.linearGradient(listOf(CyanPrimary.copy(alpha = pulse), VaultOutline)), RoundedCornerShape(20.dp))
        ) {
            Icon(Icons.Outlined.LockOpen, "darkVault", tint = CyanPrimary.copy(alpha = pulse), modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("darkVault", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace)
        Text("AES-256 ENCRYPTED", style = MaterialTheme.typography.labelSmall, color = CyanPrimary.copy(alpha = 0.7f), letterSpacing = 3.sp)
    }
}

// ── File / folder cards ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFileCard(
    file: VaultFile,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onPreview: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = false,
    thumbnailPassword: String? = null,
    thumbnailAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null
) {
    val borderColor = if (isSelected) CyanPrimary else VaultOutline
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyanPrimary.copy(alpha = 0.08f) else VaultSurfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .then(when {
                onToggleSelect != null -> Modifier.combinedClickable(
                    onClick = { onToggleSelect() },
                    onLongClick = onLongPress
                )
                onClick != null || onLongPress != null -> Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongPress
                )
                else -> Modifier
            })
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Selection indicator
            if (onToggleSelect != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(22.dp)
                ) {
                    if (isSelected) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
                    } else {
                        Box(Modifier.size(18.dp).border(1.5.dp, VaultOutline, CircleShape))
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            // Thumbnail or file type icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(VaultBackground)
            ) {
                if (showThumbnail && HomeViewModel.isImageMime(file.originalMimeType)) {
                    VaultThumbnailImage(
                        file = file,
                        password = thumbnailPassword,
                        account = thumbnailAccount,
                        showThumbnails = true,
                        iconSize = 20.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(fileTypeIcon(file.originalName, file.originalMimeType), null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                }
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
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        formatSize(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (file.modifiedTime.isNotEmpty()) {
                        Text(
                            " · ${formatDate(file.modifiedTime.ifEmpty { file.createdTime })}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (onPreview != null) {
                IconButton(onClick = onPreview) {
                    Icon(Icons.Outlined.Preview, "Preview", tint = CyanPrimary.copy(alpha = 0.8f))
                }
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Outlined.Download, "Decrypt and save", tint = CyanPrimary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun VaultFolderCard(
    folder: VaultFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) CyanPrimary else VaultOutline
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyanPrimary.copy(alpha = 0.08f) else VaultSurfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { if (onToggleSelect != null && isSelected) onToggleSelect() else onOpen() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (onToggleSelect != null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
                    if (isSelected) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
                    } else {
                        Box(Modifier.size(18.dp).border(1.5.dp, VaultOutline, CircleShape))
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(VaultBackground)
            ) {
                Icon(Icons.Outlined.Folder, null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.originalName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Folder",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Upload progress card ───────────────────────────────────────────────────────

@Composable
fun UploadProgressCard(
    fileName: String,
    stage: String,
    progress: Float,
    uploaded: Long,
    total: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    currentIndex: Int = 0,
    totalInBatch: Int = 0,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        modifier = modifier.fillMaxWidth().border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Multi-file queue line (Task 5)
            if (totalInBatch > 1 && currentIndex > 0) {
                Text(
                    "File $currentIndex of $totalInBatch",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPaused) {
                    Icon(Icons.Outlined.PauseCircleOutline, null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = CyanPrimary,
                        strokeWidth = 2.dp,
                        strokeCap = StrokeCap.Round
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isPaused) "Upload paused" else stage,
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (total > 0) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPrimary
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (total > 0) {
                    Text(
                        "${formatSize(uploaded)} / ${formatSize(total)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (progress >= 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = CyanPrimary,
                    trackColor = VaultOutline
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = CyanPrimary,
                    trackColor = VaultOutline
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                // Task 5: Pause / Resume button
                if (onPause != null && onResume != null) {
                    if (isPaused) {
                        androidx.compose.material3.TextButton(onClick = onResume) {
                            Text("Resume", color = CyanPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        androidx.compose.material3.TextButton(onClick = onPause) {
                            Text("Pause", color = CyanPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                androidx.compose.material3.TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Storage info bar ──────────────────────────────────────────────────────────

@Composable
fun StorageInfoCard(
    usedByVault: Long,
    driveTotalUsed: Long,
    driveLimit: Long,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceVariant),
        modifier = modifier.fillMaxWidth().border(1.dp, VaultOutline, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Vault: ${formatSize(usedByVault)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary
                )
                if (driveLimit > 0) {
                    val free = driveLimit - driveTotalUsed
                    Text(
                        "Drive free: ${formatSize(free)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (driveLimit > 0) {
                Spacer(Modifier.height(6.dp))
                val fraction = (driveTotalUsed.toFloat() / driveLimit).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = if (fraction > 0.9f) MaterialTheme.colorScheme.error else CyanPrimary.copy(alpha = 0.6f),
                    trackColor = VaultOutline
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatSize(driveTotalUsed)} of ${formatSize(driveLimit)} used",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Filter chips ───────────────────────────────────────────────────────────────

@Composable
fun FilterChipRow(
    selected: FilterType,
    onSelect: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(FilterType.entries.size) { i ->
            val type = FilterType.entries[i]
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPrimary.copy(alpha = 0.15f),
                    selectedLabelColor = CyanPrimary,
                    containerColor = VaultSurfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == type,
                    selectedBorderColor = CyanPrimary,
                    borderColor = VaultOutline,
                    selectedBorderWidth = 1.dp,
                    borderWidth = 1.dp
                )
            )
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
fun EmptyVaultState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp)
    ) {
        Icon(Icons.Outlined.FolderOpen, null, tint = VaultOutline, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Vault is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap + to upload and encrypt a file",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun EmptySearchState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp)
    ) {
        Icon(Icons.Outlined.Search, null, tint = VaultOutline, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("No files match your search", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "Try different keywords or clear the filter",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// ── Vault thumbnail image (Task 4) ────────────────────────────────────────────

/**
 * Displays a thumbnail for an encrypted vault file.
 * Decrypts in memory via Coil + [VaultThumbnailFetcher]; never writes plaintext to disk.
 * Shows a shimmer placeholder while loading.
 * Gated by [showThumbnails]; falls back to icon when false or on error.
 */
@Composable
fun VaultThumbnailImage(
    file: com.darkvault.app.model.VaultFile,
    password: String?,
    account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?,
    showThumbnails: Boolean,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val isImageOrVideo = HomeViewModel.isImageMime(file.originalMimeType) ||
            HomeViewModel.isVideoMime(file.originalMimeType)
    val canShowThumb = showThumbnails && isImageOrVideo && !file.isFolder &&
            password != null && account != null &&
            HomeViewModel.isImageMime(file.originalMimeType) &&
            com.darkvault.app.VaultSession.dek != null

    if (canShowThumb) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val imageLoader = androidx.compose.runtime.remember(context) { buildVaultImageLoader(context) }
        val request = VaultThumbnailRequest(
            file = file,
            password = password!!,
            account = account!!
        )

        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerAlpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "shimmerAlpha"
        )

        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(request)
                .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            loading = {
                // Shimmer placeholder while loading
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VaultBackground)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CyanPrimary.copy(alpha = shimmerAlpha))
                    )
                }
            },
            error = {
                // Fallback to file type icon on error
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        fileTypeIcon(file.originalName, file.originalMimeType),
                        null,
                        tint = CyanPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(iconSize)
                    )
                }
            },
            modifier = modifier
        )
    } else {
        // Fallback: plain icon when thumbnails disabled, file is a video, or DEK is null
        Icon(
            if (file.isFolder) Icons.Outlined.Folder
            else fileTypeIcon(file.originalName, file.originalMimeType),
            null,
            tint = if (file.isFolder) CyanPrimary else CyanPrimary.copy(alpha = 0.7f),
            modifier = Modifier.size(iconSize)
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

fun fileTypeIcon(name: String, mime: String = ""): ImageVector {
    if (HomeViewModel.isImageMime(mime)) return Icons.Outlined.Image
    if (HomeViewModel.isVideoMime(mime)) return Icons.Outlined.VideoFile
    if (HomeViewModel.isAudioMime(mime)) return Icons.Outlined.MusicNote
    if (mime == "application/pdf") return Icons.Outlined.PictureAsPdf
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> Icons.Outlined.Image
        "mp4", "mkv", "avi", "mov", "webm" -> Icons.Outlined.VideoFile
        "mp3", "aac", "flac", "wav", "ogg", "m4a" -> Icons.Outlined.MusicNote
        "pdf" -> Icons.Outlined.PictureAsPdf
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${"%.1f".format(bytes / 1024.0)} KB"
    bytes < 1024L * 1024L * 1024L -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
}

private fun formatDate(iso: String): String {
    return try {
        val inst = java.time.Instant.parse(iso)
        val ldt = java.time.LocalDateTime.ofInstant(inst, java.time.ZoneId.systemDefault())
        val now = java.time.LocalDate.now()
        val date = ldt.toLocalDate()
        when {
            date == now -> "Today ${"%02d:%02d".format(ldt.hour, ldt.minute)}"
            date == now.minusDays(1) -> "Yesterday"
            else -> "${date.dayOfMonth} ${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
        }
    } catch (_: Exception) {
        iso.take(10)
    }
}
