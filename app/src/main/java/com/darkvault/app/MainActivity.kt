package com.darkvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.darkvault.app.ui.navigation.DarkVaultNavGraph
import com.darkvault.app.ui.theme.DarkVaultTheme
import com.darkvault.app.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DarkVaultTheme {
                DarkVaultNavGraph(authViewModel)
            }
        }
    }
}
