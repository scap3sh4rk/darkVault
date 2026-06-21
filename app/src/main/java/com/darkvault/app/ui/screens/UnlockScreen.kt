package com.darkvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkvault.app.ui.components.CyberButton
import com.darkvault.app.ui.components.VaultLogo
import com.darkvault.app.ui.components.VaultTextField
import com.darkvault.app.viewmodel.AuthViewModel

@Composable
fun UnlockScreen(viewModel: AuthViewModel, onUnlocked: () -> Unit) {
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    var password by remember { mutableStateOf("") }

    LaunchedEffect(authError) {
        if (authError != null) password = ""
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 56.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.4f))

        VaultLogo()

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Unlock Vault",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        VaultTextField(
            value = password,
            onValueChange = {
                password = it
                if (authError != null) viewModel.clearError()
            },
            label = "Master password",
            isPassword = true,
            isError = authError != null,
            errorMessage = authError,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        CyberButton(
            text = "Unlock",
            onClick = {
                if (password.isNotBlank()) {
                    viewModel.unlock(password) { onUnlocked() }
                }
            },
            enabled = password.isNotBlank(),
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(0.6f))
    }
}
