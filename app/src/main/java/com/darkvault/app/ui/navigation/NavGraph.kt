package com.darkvault.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.darkvault.app.BuildConfig
import com.darkvault.app.ui.screens.HomeScreen
import com.darkvault.app.ui.screens.OfflineFilesScreen
import com.darkvault.app.ui.screens.SetupScreen
import com.darkvault.app.ui.screens.SettingsScreen
import com.darkvault.app.ui.screens.SignInScreen
import com.darkvault.app.ui.screens.TrashScreen
import com.darkvault.app.ui.screens.UnlockScreen
import com.darkvault.app.viewmodel.AuthState
import com.darkvault.app.viewmodel.AuthViewModel
import com.darkvault.app.viewmodel.HomeViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn

private const val ROUTE_SIGNIN   = "signin"
private const val ROUTE_SETUP    = "setup"
private const val ROUTE_UNLOCK   = "unlock"
private const val ROUTE_HOME     = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TRASH    = "trash"
private const val ROUTE_OFFLINE  = "offline"

// ── Transition presets ─────────────────────────────────────────────────────────

private val depthEnter: EnterTransition =
    fadeIn(tween(380, easing = FastOutSlowInEasing)) +
    scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.93f)

private val depthExit: ExitTransition =
    fadeOut(tween(240, easing = FastOutSlowInEasing)) +
    scaleOut(tween(240, easing = FastOutSlowInEasing), targetScale = 0.97f)

// Vault opening — the signature Home enter
private val vaultOpenEnter: EnterTransition =
    fadeIn(tween(550, easing = FastOutSlowInEasing)) +
    scaleIn(tween(550, easing = FastOutSlowInEasing), initialScale = 0.88f)

private val vaultOpenExit: ExitTransition =
    fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.03f)

// Sub-screen: slides up from depth
private val slideUpEnter: EnterTransition =
    fadeIn(tween(320)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 12 }

private val slideDownExit: ExitTransition =
    fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 12 }

// ── Nav graph ──────────────────────────────────────────────────────────────────

@Suppress("DEPRECATION")
@Composable
fun DarkVaultNavGraph(authViewModel: AuthViewModel) {
    val homeViewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val navController = rememberNavController()
    val authState     by authViewModel.authState.collectAsState()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { authViewModel.initializeAuth() }

    LaunchedEffect(authState) {
        val targetRoute = when (authState) {
            is AuthState.Init,
            is AuthState.CheckingVault,
            is AuthState.SignIn,
            is AuthState.NeedsConsent -> ROUTE_SIGNIN
            is AuthState.Setup        -> ROUTE_SETUP
            is AuthState.Unlock,
            is AuthState.AppLocked    -> ROUTE_UNLOCK
            is AuthState.Home         -> ROUTE_HOME
        }
        if (currentBackStack?.destination?.route != targetRoute) {
            navController.navigate(targetRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = ROUTE_SIGNIN,
        enterTransition  = { depthEnter },
        exitTransition   = { depthExit }
    ) {
        composable(
            ROUTE_SIGNIN,
            enterTransition = { depthEnter },
            exitTransition  = { depthExit }
        ) {
            SignInScreen(viewModel = authViewModel)
        }

        composable(
            ROUTE_SETUP,
            enterTransition = { depthEnter },
            exitTransition  = { depthExit }
        ) {
            SetupScreen(viewModel = authViewModel)
        }

        composable(
            ROUTE_UNLOCK,
            enterTransition = { depthEnter },
            exitTransition  = { vaultOpenExit }
        ) {
            UnlockScreen(viewModel = authViewModel)
        }

        composable(
            ROUTE_HOME,
            enterTransition = { vaultOpenEnter },
            exitTransition  = { depthExit }
        ) {
            HomeScreen(
                authViewModel          = authViewModel,
                homeViewModel          = homeViewModel,
                onNavigateToSettings   = { navController.navigate(ROUTE_SETTINGS) },
                onNavigateToTrash      = { navController.navigate(ROUTE_TRASH) },
                onNavigateToOffline    = { navController.navigate(ROUTE_OFFLINE) },
                onNavigateToDebugPanel = {
                    if (BuildConfig.DEBUG) navController.navigate("debug_panel")
                }
            )
        }

        composable(
            ROUTE_SETTINGS,
            enterTransition = { slideUpEnter },
            exitTransition  = { slideDownExit }
        ) {
            val password       by authViewModel.masterPassword.collectAsState()
            val currentAccount  = remember { GoogleSignIn.getLastSignedInAccount(context) }
            SettingsScreen(
                authViewModel        = authViewModel,
                onBack               = { navController.popBackStack() },
                homeViewModel        = homeViewModel,
                password             = password,
                account              = currentAccount,
                onNavigateToDebugPanel = {
                    if (BuildConfig.DEBUG) navController.navigate("debug_panel")
                },
                onPasswordChanged = {
                    navController.navigate(ROUTE_UNLOCK) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(
            ROUTE_TRASH,
            enterTransition = { slideUpEnter },
            exitTransition  = { slideDownExit }
        ) {
            TrashScreen(
                homeViewModel = homeViewModel,
                onBack        = { navController.popBackStack() }
            )
        }

        composable(
            ROUTE_OFFLINE,
            enterTransition = { slideUpEnter },
            exitTransition  = { slideDownExit }
        ) {
            OfflineFilesScreen(
                homeViewModel = homeViewModel,
                onBack        = { navController.popBackStack() }
            )
        }

        if (BuildConfig.DEBUG) {
            composable("debug_panel") {
                com.darkvault.app.debug.DebugPanelScreen(navController)
            }
        }
    }
}
