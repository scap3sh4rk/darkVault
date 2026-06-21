package com.darkvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun SetupScreen(viewModel: AuthViewModel, onSetupComplete: () -> Unit) {
    val isLoading by viewModel.isLoading.collectAsState()

    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        passwordError = when {
            password.length < 8 -> "Minimum 8 characters"
            password.length > 128 -> "Maximum 128 characters"
            else -> null
        }
        confirmError = if (password != confirm) "Passwords do not match" else null
        return passwordError == null && confirmError == null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 56.dp)
    ) {
        VaultLogo()

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Create Your Vault",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your master password encrypts all files.\nIt never leaves this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(36.dp))

        VaultTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = "Master password",
            isPassword = true,
            isError = passwordError != null,
            errorMessage = passwordError,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        VaultTextField(
            value = confirm,
            onValueChange = { confirm = it; confirmError = null },
            label = "Confirm password",
            isPassword = true,
            isError = confirmError != null,
            errorMessage = confirmError,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        CyberButton(
            text = "Create Vault",
            onClick = {
                if (validate()) {
                    viewModel.setup(password, onSetupComplete)
                }
            },
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "AES-256-GCM · PBKDF2 · 100k iterations",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
