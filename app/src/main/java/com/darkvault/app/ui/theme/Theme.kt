package com.darkvault.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun DarkVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkVaultColorScheme,
        typography = DarkVaultTypography,
        content = content
    )
}
