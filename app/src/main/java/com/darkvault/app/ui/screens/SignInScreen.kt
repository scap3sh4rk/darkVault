package com.darkvault.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkvault.app.R
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.VaultLogo
import com.darkvault.app.ui.theme.CyanPrimary
import com.darkvault.app.viewmodel.AuthState
import com.darkvault.app.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

@Suppress("DEPRECATION")
@Composable
fun SignInScreen(viewModel: AuthViewModel) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()

    var signInError by remember { mutableStateOf<String?>(null) }

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
                    signInError = null
                    viewModel.onGoogleSignInCompleted(account)
                }
                .addOnFailureListener { e ->
                    signInError = "Sign in failed: ${e.message}"
                }
        } else {
            signInError = "Sign in cancelled. Please try again and grant Drive access when prompted."
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            signInError = null
            viewModel.retryAfterConsent()
        } else {
            signInError = "Drive access is required. darkVault cannot work without it."
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.NeedsConsent) {
            consentLauncher.launch((authState as AuthState.NeedsConsent).consentIntent)
        }
    }

    val isChecking   = authState == AuthState.Init || authState == AuthState.CheckingVault
    val needsConsent = authState is AuthState.NeedsConsent

    // ── Animations ─────────────────────────────────────────────────────────────
    val anim = rememberInfiniteTransition(label = "signin_bg")
    val gridAlpha by anim.animateFloat(
        initialValue = 0.025f, targetValue = 0.045f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "grid_alpha"
    )
    val radialAlpha by anim.animateFloat(
        initialValue = 0.04f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "radial_alpha"
    )
    val spinnerAlpha by anim.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "spinner_alpha"
    )

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // ── Geometric grid background ─────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 52.dp.toPx()
            val lineColor = CyanPrimary.copy(gridAlpha)

            var x = 0f
            while (x <= size.width + gridStep) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
                x += gridStep
            }
            var y = 0f
            while (y <= size.height + gridStep) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
                y += gridStep
            }

            // Radial ambient glow at center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyanPrimary.copy(radialAlpha), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.38f),
                    radius = size.minDimension * 0.65f
                ),
                radius = size.minDimension * 0.65f,
                center = Offset(size.width / 2f, size.height * 0.38f)
            )

            // Vignette — pull edges toward background so grid reads as subtle depth
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, bgColor.copy(0.85f)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.72f
                )
            )
        }

        // ── Content ───────────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 56.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.32f))

            VaultLogo()

            Spacer(modifier = Modifier.height(64.dp))

            when {
                isChecking -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = CyanPrimary.copy(alpha = spinnerAlpha),
                            strokeCap = StrokeCap.Round,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        if (authState == AuthState.CheckingVault) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "CHECKING VAULT",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                                color = CyanPrimary.copy(0.5f)
                            )
                        }
                    }
                }

                needsConsent -> {
                    VaultConsentBanner()
                    Spacer(modifier = Modifier.height(20.dp))
                    CyberButton(
                        text = "Grant Drive Access",
                        onClick = {
                            signInError = null
                            consentLauncher.launch((authState as AuthState.NeedsConsent).consentIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberButton(
                        text = "Use a different account",
                        onClick = {
                            signInError = null
                            signInLauncher.launch(googleClient.signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    signInError?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }

                else -> {
                    CyberButton(
                        text = "Sign in with Google",
                        onClick = {
                            signInError = null
                            signInLauncher.launch(googleClient.signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    signInError?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.68f))

            // Footer security spec
            Text(
                "AES-256-GCM  ·  DRIVE-BACKED  ·  ZERO-KNOWLEDGE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.28f)
            )
        }
    }
}

@Composable
private fun VaultConsentBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(0.8f))
            .drawBehind {
                drawLine(Color.White.copy(0.06f), Offset(12f, 1f), Offset(size.width - 12f, 1f), 1f)
            }
            .border(1.dp, MaterialTheme.colorScheme.error.copy(0.4f), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp).padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Drive access required",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "darkVault stores encrypted files in Google Drive. " +
                        "Grant access when prompted — without it the app cannot function.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(0.8f)
                )
            }
        }
    }
}
