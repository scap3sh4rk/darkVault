package com.darkvault.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkvault.app.crypto.CryptoManager
import com.darkvault.app.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    val isSetupDone: StateFlow<Boolean> = prefs.isSetupDone.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false
    )

    private val _masterPassword = MutableStateFlow<String?>(null)
    val masterPassword: StateFlow<String?> = _masterPassword

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun setup(password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val (hash, salt) = withContext(Dispatchers.Default) {
                CryptoManager.hashPassword(password)
            }
            prefs.savePasswordHash(hash, salt)
            _masterPassword.value = password
            _isLoading.value = false
            onDone()
        }
    }

    fun unlock(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            val stored = prefs.getPasswordHashAndSalt()
            val valid = if (stored != null) {
                withContext(Dispatchers.Default) {
                    CryptoManager.verifyPassword(password, stored.first, stored.second)
                }
            } else false

            if (valid) {
                _masterPassword.value = password
                onSuccess()
            } else {
                _authError.value = "Incorrect password"
            }
            _isLoading.value = false
        }
    }

    fun clearError() { _authError.value = null }

    fun lockVault() { _masterPassword.value = null }
}
