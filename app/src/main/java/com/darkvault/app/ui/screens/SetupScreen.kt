package com.darkvault.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.VaultLogo
import com.darkvault.app.ui.components.VaultTextField
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.ui.theme.SecureGreen
import com.darkvault.app.viewmodel.AuthViewModel

@Composable
fun SetupScreen(viewModel: AuthViewModel) {
    val isLoading by viewModel.isLoading.collectAsState()

    var password      by remember { mutableStateOf("") }
    var confirm       by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError  by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        passwordError = when {
            password.length < 8   -> "Minimum 8 characters"
            password.length > 128 -> "Maximum 128 characters"
            else                  -> null
        }
        confirmError = if (password != confirm) "Passwords do not match" else null
        return passwordError == null && confirmError == null
    }

    // Password strength indicator (0..4)
    val strength = when {
        password.isEmpty()  -> 0
        password.length < 8 -> 1
        password.length < 12 && !password.any { it.isDigit() } -> 2
        password.length >= 16 && password.any { it.isDigit() } &&
            password.any { !it.isLetterOrDigit() } -> 4
        else -> 3
    }

    // Animations
    val anim = rememberInfiniteTransition(label = "setup_anim")
    val bgGlow by anim.animateFloat(
        initialValue = 0.03f, targetValue = 0.07f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bg_glow"
    )
    val dotPulse by anim.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot"
    )

    val strengthColor = when (strength) {
        1    -> MaterialTheme.colorScheme.error
        2    -> MaterialTheme.colorScheme.error.copy(0.7f)
        3    -> CyanPrimary.copy(0.7f)
        4    -> SecureGreen
        else -> Color.Transparent
    }
    val strengthLabel = when (strength) {
        1 -> "WEAK"
        2 -> "FAIR"
        3 -> "STRONG"
        4 -> "EXCELLENT"
        else -> ""
    }

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Ambient background
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CyanPrimary.copy(bgGlow), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.3f),
                    radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width / 2f, size.height * 0.3f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 56.dp)
        ) {
            VaultLogo()

            Spacer(modifier = Modifier.height(52.dp))

            Text(
                "CREATE VAULT",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Your master password encrypts all files.\nIt never leaves this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            VaultTextField(
                value = password,
                onValueChange = { password = it; passwordError = null },
                label = "Master password",
                isPassword = true,
                isError = passwordError != null,
                errorMessage = passwordError,
                modifier = Modifier.fillMaxWidth()
            )

            // Password strength indicator
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Segmented strength bar
                    repeat(4) { i ->
                        val segColor = if (i < strength) strengthColor else CyanPrimary.copy(0.12f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .padding(horizontal = 1.5.dp)
                                .background(segColor, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        strengthLabel,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = strengthColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            VaultTextField(
                value = confirm,
                onValueChange = { confirm = it; confirmError = null },
                label = "Confirm password",
                isPassword = true,
                isError = confirmError != null,
                errorMessage = confirmError,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            CyberButton(
                text = "Initialize Vault",
                onClick = { if (validate()) viewModel.setup(password) },
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Security spec footer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            CyanPrimary.copy(0.15f),
                            Offset(size.width * 0.25f, 0f),
                            Offset(size.width * 0.75f, 0f),
                            1f
                        )
                    }
                    .padding(top = 16.dp)
            ) {
                Text(
                    "AES-256-GCM  ·  PBKDF2  ·  100K ITERATIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.28f)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)
                ) {
                    Canvas(Modifier.size(4.dp)) {
                        drawCircle(SecureGreen.copy(dotPulse))
                    }
                    Text(
                        "ZERO-KNOWLEDGE ENCRYPTION",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = SecureGreen.copy(0.55f)
                    )
                }
            }
        }
    }
}
