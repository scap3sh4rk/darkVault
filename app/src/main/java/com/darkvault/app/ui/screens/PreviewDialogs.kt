package com.darkvault.app.ui.screens

import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.VaultSurfaceVariant
import com.darkvault.app.viewmodel.HomeViewModel
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import java.util.UUID

// ── Video preview dialog ───────────────────────────────────────────────────────

@Composable
internal fun VideoPreviewDialog(
    file: VaultFile,
    homeViewModel: HomeViewModel,
    password: String?,
    account: GoogleSignInAccount?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    val SIZE_LIMIT = 500_000_000L

    LaunchedEffect(file.id) {
        if (password == null || account == null) {
            error = "Vault is locked"; loading = false; return@LaunchedEffect
        }
        if (file.size > SIZE_LIMIT) {
            error = "File too large for in-app preview (${formatFileSize(file.size)}). Download to view."
            loading = false; return@LaunchedEffect
        }
        try {
            val bytes = homeViewModel.decryptToMemory(file, password, account)
            if (bytes == null) {
                error = "Decryption failed"; loading = false; return@LaunchedEffect
            }
            val ext = mimeToExt(file.originalMimeType)
            val tmp = File(context.cacheDir, "preview_${UUID.randomUUID()}.$ext")
            FileOutputStream(tmp).use { fos -> fos.write(bytes) }
            Arrays.fill(bytes, 0.toByte())
            tempFile = tmp
        } catch (e: Exception) {
            error = "Preview failed: ${e.message}"
        }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose { tempFile?.delete() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black
        ) {
            Column {
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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.White)
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    when {
                        loading -> CircularProgressIndicator(color = CyanPrimary)
                        error != null -> Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                        tempFile != null -> {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val mc = MediaController(ctx)
                                        mc.setAnchorView(this)
                                        setMediaController(mc)
                                        setVideoPath(tempFile!!.absolutePath)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = false
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Audio preview dialog ───────────────────────────────────────────────────────

@Composable
internal fun AudioPreviewDialog(
    file: VaultFile,
    homeViewModel: HomeViewModel,
    password: String?,
    account: GoogleSignInAccount?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    val playerHolder = remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(file.id) {
        if (password == null || account == null) {
            error = "Vault is locked"; loading = false; return@LaunchedEffect
        }
        if (file.size > 200_000_000L) {
            error = "File too large for preview. Download to listen."
            loading = false; return@LaunchedEffect
        }
        try {
            val bytes = homeViewModel.decryptToMemory(file, password, account)
            if (bytes == null) {
                error = "Decryption failed"; loading = false; return@LaunchedEffect
            }
            val ext = mimeToExt(file.originalMimeType)
            val tmp = File(context.cacheDir, "preview_${UUID.randomUUID()}.$ext")
            FileOutputStream(tmp).use { fos -> fos.write(bytes) }
            Arrays.fill(bytes, 0.toByte())
            tempFile = tmp

            val player = MediaPlayer()
            player.setDataSource(tmp.absolutePath)
            player.prepare()
            durationMs = player.duration.toLong()
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = 0L
            }
            playerHolder.value = player
            player.start()
            isPlaying = true
        } catch (e: Exception) {
            error = "Preview failed: ${e.message}"
        }
        loading = false
    }

    // Position ticker — updates every 500ms while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val p = playerHolder.value
            if (p != null && p.isPlaying) positionMs = p.currentPosition.toLong()
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerHolder.value?.release()
            playerHolder.value = null
            tempFile?.delete()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurfaceVariant,
        title = {
            Text(
                file.originalName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                when {
                    loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyanPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Loading audio…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { fraction ->
                                val player = playerHolder.value
                                if (player != null && durationMs > 0) {
                                    val seekTo = (fraction * durationMs).toInt()
                                    player.seekTo(seekTo)
                                    positionMs = seekTo.toLong()
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = CyanPrimary,
                                activeTrackColor = CyanPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        IconButton(
                            onClick = {
                                val player = playerHolder.value ?: return@IconButton
                                if (isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    if (positionMs >= durationMs && durationMs > 0) player.seekTo(0)
                                    player.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.PauseCircleOutline
                                              else Icons.Outlined.PlayCircleOutline,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = CyanPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// ── PDF preview dialog ────────────────────────────────────────────────────────

@Composable
internal fun PdfPreviewDialog(
    file: VaultFile,
    homeViewModel: HomeViewModel,
    password: String?,
    account: GoogleSignInAccount?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }
    var pages by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var totalPageCount by remember { mutableStateOf(0) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.id) {
        if (password == null || account == null) {
            error = "Vault is locked"; loading = false; return@LaunchedEffect
        }
        if (file.size > 100_000_000L) {
            error = "File too large for preview. Download to view."
            loading = false; return@LaunchedEffect
        }
        try {
            val bytes = homeViewModel.decryptToMemory(file, password, account)
            if (bytes == null) {
                error = "Decryption failed"; loading = false; return@LaunchedEffect
            }
            val tmp = File(context.cacheDir, "preview_${UUID.randomUUID()}.pdf")
            FileOutputStream(tmp).use { fos -> fos.write(bytes) }
            Arrays.fill(bytes, 0.toByte())
            tempFile = tmp

            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            totalPageCount = renderer.pageCount
            val rendered = mutableListOf<android.graphics.Bitmap>()
            val renderWidth = 800
            val maxPages = minOf(renderer.pageCount, 20)
            for (i in 0 until maxPages) {
                val page = renderer.openPage(i)
                val pageScale = renderWidth.toFloat() / page.width
                val bmp = android.graphics.Bitmap.createBitmap(
                    renderWidth,
                    (page.height * pageScale).toInt(),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                rendered.add(bmp)
            }
            renderer.close()
            pfd.close()
            pages = rendered
        } catch (e: Exception) {
            error = "PDF preview failed: ${e.message}"
        }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            pages.forEach { it.recycle() }
            tempFile?.delete()
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset += panChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 4.dp)
                ) {
                    Text(
                        file.originalName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!loading && error == null && totalPageCount > 0) {
                        Text(
                            "${pages.size}/${totalPageCount}p  ${(scale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close")
                    }
                }
                HorizontalDivider()

                // Zoomable content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CyanPrimary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Rendering PDF…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        error != null -> Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                        pages.isEmpty() -> Text(
                            "No pages",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                                },
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Spacer(Modifier.height(8.dp))
                            pages.forEachIndexed { i, bmp ->
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${i + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (totalPageCount > pages.size) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Showing first ${pages.size} of $totalPageCount pages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // Zoom controls — only when pages are loaded
                if (!loading && error == null && pages.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            scale = (scale / 1.3f).coerceAtLeast(1f)
                            if (scale <= 1f) { scale = 1f; offset = Offset.Zero }
                        }) {
                            Icon(Icons.Outlined.ZoomOut, "Zoom out", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                            Text("Reset", color = CyanPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { scale = (scale * 1.3f).coerceAtMost(4f) }) {
                            Icon(Icons.Outlined.ZoomIn, "Zoom in", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// ── Helper functions ──────────────────────────────────────────────────────────

internal fun mimeToExt(mime: String): String = when (mime) {
    "video/mp4" -> "mp4"
    "video/x-matroska" -> "mkv"
    "video/quicktime" -> "mov"
    "video/webm" -> "webm"
    "video/x-msvideo" -> "avi"
    "video/3gpp" -> "3gp"
    "video/3gpp2" -> "3g2"
    "video/mpeg" -> "mpeg"
    "audio/mpeg" -> "mp3"
    "audio/aac" -> "aac"
    "audio/flac" -> "flac"
    "audio/ogg" -> "ogg"
    "audio/wav", "audio/x-wav" -> "wav"
    "audio/mp4" -> "m4a"
    "audio/opus" -> "opus"
    "audio/x-ms-wma" -> "wma"
    "application/pdf" -> "pdf"
    else -> mime.substringAfter("/").replace("+", "_").take(10)
}

internal fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
}
