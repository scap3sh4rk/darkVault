package com.darkvault.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.darkvault.app.ui.screens.HomeScreen
import com.darkvault.app.ui.screens.SetupScreen
import com.darkvault.app.ui.screens.UnlockScreen
import com.darkvault.app.viewmodel.AuthViewModel

private const val ROUTE_SETUP = "setup"
private const val ROUTE_UNLOCK = "unlock"
private const val ROUTE_HOME = "home"

@Composable
fun DarkVaultNavGraph(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val isSetupDone by authViewModel.isSetupDone.collectAsState()
    val startDestination = if (isSetupDone) ROUTE_UNLOCK else ROUTE_SETUP

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
            HomeScreen(authViewModel = authViewModel)
        }
    }
}
