package com.darkvault.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoFile
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkvault.app.R
import com.darkvault.app.model.FilterType
import com.darkvault.app.model.VaultFile
import com.darkvault.app.ui.theme.AlertAmber
import com.darkvault.app.ui.theme.CyanGlow05
import com.darkvault.app.ui.theme.CyanGlow15
import com.darkvault.app.ui.theme.CyanGlow30
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.DepthPlane1
import com.darkvault.app.ui.theme.DepthPlane2
import com.darkvault.app.ui.theme.DepthPlane3
import com.darkvault.app.ui.theme.GlassHighlight
import com.darkvault.app.ui.theme.SecureGreen
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
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val glowElevation by animateFloatAsState(
        targetValue = if (isFocused) 8f else 0f,
        animationSpec = tween(200),
        label = "field_glow"
    )
    val primary        = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    // Auto-hide password when user types while visible
    val wrappedOnValueChange: (String) -> Unit = if (isPassword && passwordVisible) {
        { v -> passwordVisible = false; onValueChange(v) }
    } else onValueChange

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = glowElevation.dp,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = if (isError) MaterialTheme.colorScheme.error.copy(0.3f)
                                   else primary.copy(0.25f),
                    spotColor   = if (isError) MaterialTheme.colorScheme.error.copy(0.2f)
                                   else primary.copy(0.15f)
                )
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = wrappedOnValueChange,
                label = { Text(label) },
                isError = isError,
                singleLine = true,
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation()
                                       else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onDone   = { onImeAction() },
                    onNext   = { onImeAction() },
                    onSearch = { onImeAction() },
                    onGo     = { onImeAction() }
                ),
                leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
                trailingIcon = if (isPassword) {
                    {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Hide" else "Show"
                            )
                        }
                    }
                } else null,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor           = primary,
                    focusedLabelColor            = primary,
                    focusedLeadingIconColor      = primary,
                    cursorColor                  = primary,
                    unfocusedBorderColor         = MaterialTheme.colorScheme.outline.copy(0.6f),
                    unfocusedLabelColor          = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                    unfocusedLeadingIconColor    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                    errorBorderColor             = MaterialTheme.colorScheme.error,
                    errorLabelColor              = MaterialTheme.colorScheme.error,
                    focusedContainerColor        = containerColor,
                    unfocusedContainerColor      = containerColor,
                    errorContainerColor          = containerColor,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "btn_scale"
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.55f else if (enabled) 0.3f else 0f,
        animationSpec = tween(90), label = "btn_shadow"
    )
    val shadowElev by animateFloatAsState(
        targetValue = if (!enabled || isLoading) 0f else if (isPressed) 2f else 10f,
        animationSpec = spring(0.5f, Spring.StiffnessMedium), label = "btn_elev"
    )

    val primary      = MaterialTheme.colorScheme.primary
    val outline      = MaterialTheme.colorScheme.outline
    val surfaceTop   = MaterialTheme.colorScheme.surfaceVariant
    val surfaceBot   = MaterialTheme.colorScheme.surface
    val disabledTop  = MaterialTheme.colorScheme.surface
    val disabledBot  = MaterialTheme.colorScheme.background
    val edgeLine     = MaterialTheme.colorScheme.onSurface

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation    = shadowElev.dp,
                shape        = RoundedCornerShape(10.dp),
                ambientColor = primary.copy(shadowAlpha * 0.5f),
                spotColor    = primary.copy(shadowAlpha)
            )
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(surfaceTop, surfaceBot))
                else Brush.verticalGradient(listOf(disabledTop, disabledBot))
            )
            .drawBehind {
                if (enabled) {
                    // Top-edge hairline (white on dark, subtle dark on light)
                    drawLine(
                        edgeLine.copy(if (isPressed) 0.04f else 0.07f),
                        Offset(28f, 1f), Offset(size.width - 28f, 1f), 1f
                    )
                    // Bottom primary accent line
                    drawLine(
                        primary.copy(if (isPressed) 0.60f else 0.30f),
                        Offset(28f, size.height - 1f), Offset(size.width - 28f, size.height - 1f), 1f
                    )
                }
            }
            .border(
                width = 1.dp,
                brush = if (enabled) Brush.verticalGradient(
                    listOf(outline.copy(0.45f), primary.copy(0.40f))
                ) else Brush.linearGradient(
                    listOf(outline.copy(0.3f), outline.copy(0.3f))
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(horizontal = 24.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
        } else {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.2.sp),
                color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f)
            )
        }
    }
}

// ── Logo ───────────────────────────────────────────────────────────────────────

@Composable
fun VaultLogo(modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "logo_anim")

    val outerAlpha by anim.animateFloat(
        initialValue = 0.08f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Reverse),
        label = "outer_alpha"
    )
    val midRotation by anim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing)),
        label = "mid_rotate"
    )
    val innerAlpha by anim.animateFloat(
        initialValue = 0.45f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "inner_alpha"
    )
    val innerScale by anim.animateFloat(
        initialValue = 0.97f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "inner_scale"
    )
    val dotAlpha by anim.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    // Theme-aware icon container colors
    val iconContainerTop = MaterialTheme.colorScheme.surfaceVariant
    val iconContainerBot = MaterialTheme.colorScheme.surface
    val primary          = MaterialTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {

            Canvas(Modifier.size(128.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(primary.copy(outerAlpha * 0.18f), Color.Transparent),
                        center = center, radius = size.minDimension / 2f
                    ),
                    radius = size.minDimension / 2f
                )
                drawCircle(
                    color = primary.copy(outerAlpha),
                    radius = size.minDimension / 2f - 1f,
                    style = Stroke(1.dp.toPx())
                )
            }

            Canvas(
                Modifier
                    .size(96.dp)
                    .graphicsLayer { rotationZ = midRotation }
            ) {
                val r = size.minDimension / 2f - 1f
                listOf(0f, 90f, 180f, 270f).forEach { start ->
                    drawArc(
                        color = primary.copy(0.30f),
                        startAngle = start, sweepAngle = 55f, useCenter = false,
                        style = Stroke(1.dp.toPx()),
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2, r * 2)
                    )
                }
            }

            Canvas(
                Modifier
                    .size(72.dp)
                    .graphicsLayer { scaleX = innerScale; scaleY = innerScale }
            ) {
                drawCircle(
                    color = primary.copy(innerAlpha),
                    radius = size.minDimension / 2f - 1f,
                    style = Stroke(1.dp.toPx())
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .shadow(12.dp, RoundedCornerShape(14.dp),
                            ambientColor = primary.copy(0.22f), spotColor = primary.copy(0.12f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(iconContainerTop, iconContainerBot)))
                    .drawBehind {
                        drawLine(Color.White.copy(0.12f), Offset(8f, 1f), Offset(size.width - 8f, 1f), 1f)
                    }
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(GlassHighlight, primary.copy(0.25f))),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(9.dp)
            ) {
                ComposeImage(
                    painter = painterResource(R.drawable.icon),
                    contentDescription = "darkVault",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "darkVault",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Canvas(Modifier.size(4.dp)) { drawCircle(SecureGreen.copy(dotAlpha)) }
            Text(
                "SECURE CLOUD STORAGE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(0.55f),
                letterSpacing = 3.sp
            )
            Canvas(Modifier.size(4.dp)) { drawCircle(SecureGreen.copy(dotAlpha)) }
        }
    }
}

// ── File card ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFileCard(
    file: VaultFile,
    onDownload: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPreview: (() -> Unit)? = null,
    onMoreActions: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = false,
    thumbnailPassword: String? = null,
    thumbnailAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null
) {
    val breatheAnim = rememberInfiniteTransition(label = "card_breathe")
    val borderBreath by breatheAnim.animateFloat(
        initialValue = 0.18f, targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Reverse),
        label = "border_breath"
    )
    val tiltDeg by animateFloatAsState(
        targetValue = if (isSelected) -1.8f else 0f,
        animationSpec = spring(0.55f, 260f), label = "card_tilt"
    )
    val shadowElev by animateFloatAsState(
        targetValue = if (isSelected) 14f else 1f,
        animationSpec = spring(0.65f, 280f), label = "card_elev"
    )

    // Theme-aware colors — captured in composable scope, safe to use in lambdas
    val primary        = MaterialTheme.colorScheme.primary
    val surfaceTop     = MaterialTheme.colorScheme.surfaceVariant
    val surfaceBot     = MaterialTheme.colorScheme.surface
    val bgColor        = MaterialTheme.colorScheme.background
    val outlineColor   = MaterialTheme.colorScheme.outline

    val clickModifier = when {
        onToggleSelect != null -> Modifier.combinedClickable(
            onClick = { onToggleSelect() }, onLongClick = onLongPress
        )
        onClick != null || onLongPress != null -> Modifier.combinedClickable(
            onClick = { onClick?.invoke() }, onLongClick = onLongPress
        )
        else -> Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { rotationX = tiltDeg; cameraDistance = 8 * density }
            .shadow(
                elevation    = shadowElev.dp,
                shape        = RoundedCornerShape(12.dp),
                ambientColor = if (isSelected) primary.copy(0.22f) else Color.Black,
                spotColor    = if (isSelected) primary.copy(0.12f) else Color.Black
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) Brush.verticalGradient(listOf(surfaceTop, primary.copy(0.07f)))
                else Brush.verticalGradient(listOf(surfaceTop, surfaceBot))
            )
            .drawBehind {
                drawRect(
                    color = if (isSelected) primary else primary.copy(0.45f),
                    size = Size(3.dp.toPx(), size.height)
                )
                drawLine(Color.White.copy(0.07f),
                    Offset(3.dp.toPx() + 8f, 1f), Offset(size.width - 8f, 1f), 1f)
            }
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) primary else primary.copy(borderBreath),
                shape = RoundedCornerShape(12.dp)
            )
            .then(clickModifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp)
        ) {
            if (onToggleSelect != null) {
                Crossfade(targetState = isSelected, label = "sel_icon") { sel ->
                    if (sel) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = primary, modifier = Modifier.size(20.dp))
                    } else {
                        Box(Modifier.size(20.dp).border(1.dp, outlineColor.copy(0.55f), CircleShape))
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .border(1.dp, outlineColor.copy(0.35f), RoundedCornerShape(10.dp))
            ) {
                VaultThumbnailImage(
                    file = file, password = thumbnailPassword, account = thumbnailAccount,
                    showThumbnails = showThumbnail, iconSize = 18.dp, modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(file.originalName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(formatSize(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = primary.copy(0.55f))
                    if (file.modifiedTime.isNotEmpty()) {
                        Text("·", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                        Text(formatDate(file.modifiedTime.ifEmpty { file.createdTime }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f))
                    }
                }
            }

            if (onToggleSelect != null && onPreview != null) {
                IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Preview, "Preview",
                        tint = primary.copy(0.65f), modifier = Modifier.size(18.dp))
                }
            }
            if (onToggleSelect == null && onMoreActions != null) {
                IconButton(onClick = onMoreActions, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.MoreVert, "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Folder card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFolderCard(
    folder: VaultFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit = {},
    onMoreActions: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val breatheAnim = rememberInfiniteTransition(label = "folder_breathe")
    val borderBreath by breatheAnim.animateFloat(
        initialValue = 0.25f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "folder_border"
    )
    val tiltDeg by animateFloatAsState(
        targetValue = if (isSelected) -1.8f else 0f, animationSpec = spring(0.55f, 260f), label = "folder_tilt"
    )
    val shadowElev by animateFloatAsState(
        targetValue = if (isSelected) 14f else 1f, animationSpec = spring(0.65f, 280f), label = "folder_elev"
    )

    val primary      = MaterialTheme.colorScheme.primary
    val surfaceTop   = MaterialTheme.colorScheme.surfaceVariant
    val surfaceBot   = MaterialTheme.colorScheme.surface
    val bgColor      = MaterialTheme.colorScheme.background
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { rotationX = tiltDeg; cameraDistance = 8 * density }
            .shadow(shadowElev.dp, RoundedCornerShape(12.dp),
                    ambientColor = if (isSelected) primary.copy(0.25f) else Color.Black,
                    spotColor    = if (isSelected) primary.copy(0.15f) else Color.Black)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) Brush.verticalGradient(listOf(surfaceTop, primary.copy(0.09f)))
                else Brush.verticalGradient(listOf(surfaceTop, surfaceBot))
            )
            .drawBehind {
                drawRect(if (isSelected) primary else primary.copy(0.65f), size = Size(3.dp.toPx(), size.height))
                drawLine(Color.White.copy(0.07f), Offset(3.dp.toPx() + 8f, 1f), Offset(size.width - 8f, 1f), 1f)
            }
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) primary else primary.copy(borderBreath),
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick    = { if (onToggleSelect != null) onToggleSelect() else onOpen() },
                onLongClick = { onLongPress?.invoke() }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp)
        ) {
            if (onToggleSelect != null) {
                Crossfade(targetState = isSelected, label = "folder_sel") { sel ->
                    if (sel) Icon(Icons.Outlined.CheckCircle, null, tint = primary, modifier = Modifier.size(20.dp))
                    else Box(Modifier.size(20.dp).border(1.dp, outlineColor.copy(0.55f), CircleShape))
                }
                Spacer(Modifier.width(10.dp))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .border(1.dp, primary.copy(0.25f), RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Outlined.Folder, null, tint = primary, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(folder.originalName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("CONTAINER",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = primary.copy(0.55f))
            }

            if (onToggleSelect == null && onMoreActions != null) {
                IconButton(onClick = onMoreActions, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.MoreVert, "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                        modifier = Modifier.size(18.dp))
                }
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
    val primary    = MaterialTheme.colorScheme.primary
    val surfaceTop = MaterialTheme.colorScheme.surfaceVariant
    val surfaceBot = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp),
                    ambientColor = primary.copy(0.2f), spotColor = primary.copy(0.1f))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(surfaceTop, surfaceBot)))
            .drawBehind {
                drawLine(primary.copy(0.6f),
                    Offset(0f, size.height - 1f),
                    Offset(size.width * progress.coerceIn(0f, 1f), size.height - 1f), 2f)
                drawLine(Color.White.copy(0.06f), Offset(12f, 1f), Offset(size.width - 12f, 1f), 1f)
            }
            .border(1.dp, primary.copy(0.35f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (totalInBatch > 1 && currentIndex > 0) {
                Text("FILE $currentIndex OF $totalInBatch",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPaused) {
                    Icon(Icons.Outlined.PauseCircleOutline, null, tint = primary, modifier = Modifier.size(16.dp))
                } else {
                    CircularProgressIndicator(Modifier.size(16.dp), color = primary,
                        strokeWidth = 2.dp, strokeCap = StrokeCap.Round)
                }
                Spacer(Modifier.width(10.dp))
                Text(if (isPaused) "UPLOAD PAUSED" else stage.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                    color = primary, modifier = Modifier.weight(1f))
                if (total > 0) Text("${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium, color = primary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (total > 0) Text("${formatSize(uploaded)} / ${formatSize(total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            }
            Spacer(Modifier.height(8.dp))
            if (progress >= 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = primary, trackColor = MaterialTheme.colorScheme.outline.copy(0.4f))
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = primary, trackColor = MaterialTheme.colorScheme.outline.copy(0.4f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (onPause != null && onResume != null) {
                    if (isPaused) {
                        androidx.compose.material3.TextButton(onClick = onResume) {
                            Text("RESUME", color = primary, style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        androidx.compose.material3.TextButton(onClick = onPause) {
                            Text("PAUSE", color = primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                androidx.compose.material3.TextButton(onClick = onCancel) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Storage info ───────────────────────────────────────────────────────────────

@Composable
fun StorageInfoCard(
    usedByVault: Long,
    driveTotalUsed: Long,
    driveLimit: Long,
    modifier: Modifier = Modifier
) {
    val fraction    = if (driveLimit > 0) (driveTotalUsed.toFloat() / driveLimit).coerceIn(0f, 1f) else 0f
    val statusColor = if (fraction > 0.90f) AlertAmber else SecureGreen

    val breatheAnim = rememberInfiniteTransition(label = "storage_anim")
    val dotPulse by breatheAnim.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "status_dot"
    )

    val primary    = MaterialTheme.colorScheme.primary
    val surfaceTop = MaterialTheme.colorScheme.surfaceVariant
    val surfaceBot = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(surfaceTop, surfaceBot)))
            .drawBehind {
                drawLine(Color.White.copy(0.05f), Offset(12f, 1f), Offset(size.width - 12f, 1f), 1f)
            }
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Canvas(Modifier.size(5.dp)) { drawCircle(statusColor.copy(dotPulse)) }
                    Text("VAULT",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f))
                    Text(formatSize(usedByVault),
                        style = MaterialTheme.typography.labelSmall, color = primary)
                }
                if (driveLimit > 0) {
                    Text("${(fraction * 100).toInt()}%  FREE ${formatSize(driveLimit - driveTotalUsed)}",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f))
                }
            }
            if (driveLimit > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (fraction > 0.90f) AlertAmber else primary.copy(0.7f),
                    trackColor = MaterialTheme.colorScheme.outline.copy(0.3f))
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
    val primary = MaterialTheme.colorScheme.primary
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        items(FilterType.entries.size) { i ->
            val type       = FilterType.entries[i]
            val isSelected = selected == type
            FilterChip(
                selected = isSelected,
                onClick  = { onSelect(type) },
                label = {
                    Text(type.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = if (isSelected) 0.8.sp else 0.sp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = primary.copy(0.12f),
                    selectedLabelColor     = primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = isSelected,
                    selectedBorderColor = primary,
                    borderColor         = MaterialTheme.colorScheme.outline.copy(0.5f),
                    selectedBorderWidth = 1.dp, borderWidth = 1.dp
                )
            )
        }
    }
}

// ── Empty states ───────────────────────────────────────────────────────────────

@Composable
fun EmptyVaultState(modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "empty_anim")
    val ringAlpha by anim.animateFloat(
        initialValue = 0.08f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "empty_ring"
    )
    val primary = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(primary.copy(ringAlpha), radius = size.minDimension / 2f, style = Stroke(1.dp.toPx()))
                drawCircle(primary.copy(ringAlpha * 0.5f), radius = size.minDimension / 2f * 0.7f, style = Stroke(1.dp.toPx()))
            }
            Icon(Icons.Outlined.FolderOpen, null,
                tint = MaterialTheme.colorScheme.outline.copy(0.5f), modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("VAULT EMPTY",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        Spacer(Modifier.height(6.dp))
        Text("Tap + to encrypt and upload a file",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
    }
}

@Composable
fun EmptySearchState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp)
    ) {
        Icon(Icons.Outlined.Search, null,
            tint = MaterialTheme.colorScheme.outline.copy(0.4f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("NO RESULTS",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f))
        Spacer(Modifier.height(6.dp))
        Text("Try different keywords or clear the filter",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
    }
}

// ── Vault thumbnail image ──────────────────────────────────────────────────────

@Composable
fun VaultThumbnailImage(
    file: com.darkvault.app.model.VaultFile,
    password: String?,
    account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?,
    showThumbnails: Boolean,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val mime = file.originalMimeType
    val canShowThumb = showThumbnails && !file.isFolder &&
            password != null && account != null &&
            com.darkvault.app.VaultSession.dek != null &&
            (HomeViewModel.isImageMime(mime) || HomeViewModel.isVideoMime(mime) || mime == "application/pdf")

    val primary = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    if (canShowThumb) {
        val context     = androidx.compose.ui.platform.LocalContext.current
        val imageLoader = remember(context) { buildVaultImageLoader(context) }
        val request     = VaultThumbnailRequest(file = file, password = password!!, account = account!!)

        val shimmerAnim = rememberInfiniteTransition(label = "shimmer")
        val shimmerAlpha by shimmerAnim.animateFloat(
            initialValue = 0.15f, targetValue = 0.40f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "shimmerAlpha"
        )

        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(request).diskCachePolicy(coil.request.CachePolicy.DISABLED).crossfade(true).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = {
                Box(Modifier.fillMaxSize().background(bgColor)) {
                    Box(Modifier.fillMaxSize().background(primary.copy(shimmerAlpha)))
                }
            },
            error = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(fileTypeIcon(file.originalName, file.originalMimeType), null,
                        tint = primary.copy(0.6f), modifier = Modifier.size(iconSize))
                }
            },
            modifier = modifier
        )
    } else {
        Icon(
            if (file.isFolder) Icons.Outlined.Folder else fileTypeIcon(file.originalName, file.originalMimeType),
            null,
            tint = if (file.isFolder) primary else primary.copy(0.65f),
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
    return when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> Icons.Outlined.Image
        "mp4", "mkv", "avi", "mov", "webm"                 -> Icons.Outlined.VideoFile
        "mp3", "aac", "flac", "wav", "ogg", "m4a"         -> Icons.Outlined.MusicNote
        "pdf"                                               -> Icons.Outlined.PictureAsPdf
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0L                    -> "—"
    bytes < 1024L                  -> "$bytes B"
    bytes < 1024L * 1024L         -> "${"%.1f".format(bytes / 1024.0)} KB"
    bytes < 1024L * 1024L * 1024L -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    else                           -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
}

private fun formatDate(iso: String): String {
    return try {
        val inst = java.time.Instant.parse(iso)
        val ldt  = java.time.LocalDateTime.ofInstant(inst, java.time.ZoneId.systemDefault())
        val now  = java.time.LocalDate.now()
        val date = ldt.toLocalDate()
        when {
            date == now              -> "Today ${"%02d:%02d".format(ldt.hour, ldt.minute)}"
            date == now.minusDays(1) -> "Yesterday"
            else -> "${date.dayOfMonth} ${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
        }
    } catch (_: Exception) { iso.take(10) }
}
