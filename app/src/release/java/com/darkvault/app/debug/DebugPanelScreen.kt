package com.darkvault.app.debug

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * No-op stub for release builds.
 * The real implementation lives in src/debug/ and is only compiled into debug builds.
 * The composable is only added to the nav graph behind a BuildConfig.DEBUG guard,
 * so it is never called in release — but the type must be resolvable at compile time.
 */
@Composable
fun DebugPanelScreen(navController: NavController) {
    // No-op in release
}
