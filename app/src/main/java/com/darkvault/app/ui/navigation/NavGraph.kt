package com.darkvault.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.darkvault.app.ui.screens.HomeScreen
import com.darkvault.app.ui.screens.SetupScreen
import com.darkvault.app.ui.screens.SettingsScreen
import com.darkvault.app.ui.screens.UnlockScreen
import com.darkvault.app.viewmodel.AuthViewModel

private const val ROUTE_SETUP = "setup"
private const val ROUTE_UNLOCK = "unlock"
private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun DarkVaultNavGraph(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val isSetupDone by authViewModel.isSetupDone.collectAsState()
    val masterPassword by authViewModel.masterPassword.collectAsState()
    val currentBackStack by navController.currentBackStackEntryAsState()

    val setupDone = isSetupDone ?: return

    val startDestination = remember { if (setupDone) ROUTE_UNLOCK else ROUTE_SETUP }

    // When the vault auto-locks while the user is on home/settings, navigate back to unlock
    LaunchedEffect(masterPassword, setupDone) {
        if (masterPassword == null && setupDone) {
            val route = currentBackStack?.destination?.route
            if (route == ROUTE_HOME || route == ROUTE_SETTINGS) {
                navController.navigate(ROUTE_UNLOCK) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_SETUP) {
            SetupScreen(
                viewModel = authViewModel,
                onSetupComplete = {
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(ROUTE_SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_UNLOCK) {
            UnlockScreen(
                viewModel = authViewModel,
                onUnlocked = {
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(ROUTE_UNLOCK) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_HOME) {
            HomeScreen(
                authViewModel = authViewModel,
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
