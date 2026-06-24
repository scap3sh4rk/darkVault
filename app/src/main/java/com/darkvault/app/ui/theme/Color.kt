package com.darkvault.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary accent ─────────────────────────────────────────────────────────────
val CyanPrimary       = Color(0xFF00D4FF)
val CyanOnPrimary     = Color(0xFF00363F)
val CyanContainer     = Color(0xFF004F5C)
val CyanOnContainer   = Color(0xFF9EEEFF)

// ── Spatial depth planes ───────────────────────────────────────────────────────
val DepthPlane0 = Color(0xFF020207)   // Plane 0 — wallpaper / environment
val DepthPlane1 = Color(0xFF070711)   // Plane 1 — background surfaces
val DepthPlane2 = Color(0xFF0C0C1A)   // Plane 2 — content surfaces
val DepthPlane3 = Color(0xFF121224)   // Plane 3 — interactive surfaces
val DepthPlane4 = Color(0xFF181832)   // Plane 4 — modal surfaces
val DepthPlane5 = Color(0xFF000000)   // Plane 5 — critical security overlays

// ── Glass material layers ──────────────────────────────────────────────────────
val GlassHighlight = Color(0x12FFFFFF)   // 7% white — top-edge refraction line
val GlassMid       = Color(0x08FFFFFF)   // 3% white — ambient glass sheen

// ── Glow helpers ───────────────────────────────────────────────────────────────
val CyanGlow30 = Color(0x4D00D4FF)    // 30% opacity glow
val CyanGlow15 = Color(0x2600D4FF)    // 15% opacity subtle glow
val CyanGlow05 = Color(0x0D00D4FF)    // 5%  opacity ambient

// ── Security status ────────────────────────────────────────────────────────────
val SecureGreen = Color(0xFF00E896)
val AlertAmber  = Color(0xFFFFB547)

val PurpleSecondary   = Color(0xFFCE93D8)
val PurpleOnSecondary = Color(0xFF4A148C)
val PurpleContainer   = Color(0xFF6A1B9A)
val PurpleOnContainer = Color(0xFFF3E5F5)

val VaultError        = Color(0xFFFF5370)
val VaultOnError      = Color(0xFF680014)
val VaultErrorContainer = Color(0xFF93000A)
val VaultOnErrorContainer = Color(0xFFFFDAD6)

val VaultBackground   = Color(0xFF070711)
val VaultOnBackground = Color(0xFFE4E1EC)
val VaultSurface      = Color(0xFF0F0F1E)
val VaultOnSurface    = Color(0xFFE4E1EC)
val VaultSurfaceVariant  = Color(0xFF1A1A2E)
val VaultOnSurfaceVariant = Color(0xFF9E9BB4)
val VaultOutline      = Color(0xFF2D2D50)
val VaultOutlineVariant = Color(0xFF1E1E3F)

val VaultSurfaceTint  = Color(0xFF00D4FF)
val VaultInverseSurface = Color(0xFFE4E1EC)
val VaultInverseOnSurface = Color(0xFF1A1826)
val VaultInversePrimary = Color(0xFF006E84)

val VaultScrim        = Color(0xFF000000)

// Light theme colors (Task 8)
val LightBackground      = Color(0xFFF5FAFF)
val LightSurface         = Color(0xFFFFFFFF)
val LightSurfaceVariant  = Color(0xFFEDF3FB)
val LightPrimary         = Color(0xFF006B80)
val LightOnPrimary       = Color(0xFFFFFFFF)
val LightOnBackground    = Color(0xFF0A0A0F)
val LightOnSurface       = Color(0xFF0A0A0F)
val LightOnSurfaceVariant= Color(0xFF4A4A6A)
val LightOutline         = Color(0xFFC8C8D8)
val LightError           = Color(0xFFB3001B)
