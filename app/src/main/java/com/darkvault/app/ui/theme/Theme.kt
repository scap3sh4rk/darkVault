package com.darkvault.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkVaultColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = PurpleSecondary,
    onSecondary = PurpleOnSecondary,
    secondaryContainer = PurpleContainer,
    onSecondaryContainer = PurpleOnContainer,
    error = VaultError,
    onError = VaultOnError,
    errorContainer = VaultErrorContainer,
    onErrorContainer = VaultOnErrorContainer,
    background = VaultBackground,
    onBackground = VaultOnBackground,
    surface = VaultSurface,
    onSurface = VaultOnSurface,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = VaultOnSurfaceVariant,
    outline = VaultOutline,
    outlineVariant = VaultOutlineVariant,
    surfaceTint = VaultSurfaceTint,
    inverseSurface = VaultInverseSurface,
    inverseOnSurface = VaultInverseOnSurface,
    inversePrimary = VaultInversePrimary,
    scrim = VaultScrim
)

private val LightVaultColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = LightOnBackground,
    secondary = PurpleSecondary,
    onSecondary = LightOnPrimary,
    secondaryContainer = PurpleContainer,
    onSecondaryContainer = PurpleOnContainer,
    error = LightError,
    onError = LightOnPrimary,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.5f),
    surfaceTint = LightPrimary,
    inverseSurface = VaultSurfaceVariant,
    inverseOnSurface = VaultOnSurface,
    inversePrimary = CyanPrimary,
    scrim = VaultScrim
)

@Composable
fun DarkVaultTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkVaultColorScheme else LightVaultColorScheme,
        typography = DarkVaultTypography,
        content = content
    )
}

// Helper: import Color to use in lightColorScheme above
private fun Color(value: Long) = androidx.compose.ui.graphics.Color(value.toULong())
