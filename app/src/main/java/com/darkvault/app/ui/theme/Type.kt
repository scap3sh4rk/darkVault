package com.darkvault.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.darkvault.app.R

// ── Bundled font families ──────────────────────────────────────────────────────

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_bold, FontWeight.SemiBold),
)

val NunitoFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_bold, FontWeight.SemiBold),
)

val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_bold, FontWeight.SemiBold),
)

// ── Font option enum ──────────────────────────────────────────────────────────

enum class AppFont(val key: String, val label: String, val family: FontFamily) {
    INTER("inter", "Inter", InterFontFamily),
    NUNITO("nunito", "Nunito", NunitoFontFamily),
    POPPINS("poppins", "Poppins", PoppinsFontFamily),
    SYSTEM("system", "System default", FontFamily.Default),
}

fun fontFromKey(key: String): AppFont =
    AppFont.entries.firstOrNull { it.key == key } ?: AppFont.INTER

// ── Typography builder ────────────────────────────────────────────────────────

fun buildTypography(family: FontFamily = InterFontFamily) = Typography(
    displayLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 1.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.5.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 1.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)

// Keep for any callers that reference the old singleton
val DarkVaultTypography = buildTypography(InterFontFamily)
