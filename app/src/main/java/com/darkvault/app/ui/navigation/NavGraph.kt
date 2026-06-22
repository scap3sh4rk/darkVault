package com.darkvault.app.ui.navigation

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
import com.darkvault.app.ui.screens.SetupScreen
import com.darkvault.app.ui.screens.SettingsScreen
import com.darkvault.app.ui.screens.SignInScreen
import com.darkvault.app.ui.screens.TrashScreen
import com.darkvault.app.ui.screens.UnlockScreen
import com.darkvault.app.viewmodel.AuthState
import com.darkvault.app.viewmodel.AuthViewModel
import com.darkvault.app.viewmodel.HomeViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn

private const val ROUTE_SIGNIN = "signin"
private const val ROUTE_SETUP = "setup"
private const val ROUTE_UNLOCK = "unlock"
private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TRASH = "trash"

@Suppress("DEPRECATION")
@Composable
fun DarkVaultNavGraph(authViewModel: AuthViewModel) {
    val homeViewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val context = LocalContext.current

    // Kick off startup auth check exactly once.
    LaunchedEffect(Unit) {
        authViewModel.initializeAuth()
    }

    // Drive all top-level navigation from authState changes.
    LaunchedEffect(authState) {
        val targetRoute = when (authState) {
            is AuthState.Init,
            is AuthState.CheckingVault,
            is AuthState.SignIn,
            is AuthState.NeedsConsent -> ROUTE_SIGNIN
            is AuthState.Setup -> ROUTE_SETUP
            is AuthState.Unlock,
            is AuthState.AppLocked -> ROUTE_UNLOCK
            is AuthState.Home -> ROUTE_HOME
        }
        if (currentBackStack?.destination?.route != targetRoute) {
            navController.navigate(targetRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = ROUTE_SIGNIN) {
        composable(ROUTE_SIGNIN) {
            SignInScreen(viewModel = authViewModel)
        }
        composable(ROUTE_SETUP) {
            SetupScreen(viewModel = authViewModel)
        }
        composable(ROUTE_UNLOCK) {
            UnlockScreen(viewModel = authViewModel)
        }
        composable(ROUTE_HOME) {
            HomeScreen(
                authViewModel = authViewModel,
                homeViewModel = homeViewModel,
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                onNavigateToTrash = { navController.navigate(ROUTE_TRASH) },
                onNavigateToDebugPanel = {
                    if (BuildConfig.DEBUG) navController.navigate("debug_panel")
                }
            )
        }
        composable(ROUTE_SETTINGS) {
            val password by authViewModel.masterPassword.collectAsState()
            val currentAccount = remember { GoogleSignIn.getLastSignedInAccount(context) }
            SettingsScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() },
                homeViewModel = homeViewModel,
                password = password,
                account = currentAccount,
                onNavigateToDebugPanel = {
                    if (BuildConfig.DEBUG) navController.navigate("debug_panel")
                },
                onPasswordChanged = {
                    navController.navigate(ROUTE_UNLOCK) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(ROUTE_TRASH) {
            TrashScreen(
                homeViewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        if (BuildConfig.DEBUG) {
            composable("debug_panel") {
                com.darkvault.app.debug.DebugPanelScreen(navController)
            }
        }
    }
}
