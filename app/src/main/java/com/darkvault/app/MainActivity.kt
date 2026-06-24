package com.darkvault.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.darkvault.app.data.PreferencesManager
import com.darkvault.app.service.UploadForegroundService
import com.darkvault.app.ui.navigation.DarkVaultNavGraph
import com.darkvault.app.ui.theme.DarkVaultTheme
import com.darkvault.app.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var prefs: PreferencesManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission granted or denied — uploads still work, just no notifications */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        observeAppLifecycle()

        prefs = PreferencesManager(this)

        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "SYSTEM")
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            val fontKey by prefs.appFont.collectAsState(initial = "inter")
            val screenshotEnabled by prefs.screenshotEnabled.collectAsState(initial = false)
            androidx.compose.runtime.LaunchedEffect(screenshotEnabled) {
                if (screenshotEnabled) {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            DarkVaultTheme(darkTheme = isDark, fontKey = fontKey) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val controller = WindowInsetsControllerCompat(window, view)
                        controller.isAppearanceLightStatusBars = !isDark
                        controller.isAppearanceLightNavigationBars = !isDark
                    }
                }
                DarkVaultNavGraph(authViewModel)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            UploadForegroundService.CHANNEL_ID,
            "Upload Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows darkVault file upload progress"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        authViewModel.onAppBackground()
    }

    override fun onResume() {
        super.onResume()
        authViewModel.onAppForeground()
    }

    private fun observeAppLifecycle() { /* handled via onPause/onResume */ }
}
