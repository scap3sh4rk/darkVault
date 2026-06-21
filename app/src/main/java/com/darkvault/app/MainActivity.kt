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
import androidx.core.content.ContextCompat
import com.darkvault.app.service.UploadForegroundService
import com.darkvault.app.ui.navigation.DarkVaultNavGraph
import com.darkvault.app.ui.theme.DarkVaultTheme
import com.darkvault.app.viewmodel.AuthViewModel

class MainActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()

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

        setContent {
            DarkVaultTheme {
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
