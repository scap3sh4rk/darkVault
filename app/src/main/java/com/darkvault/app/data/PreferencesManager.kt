package com.darkvault.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_SETUP_DONE = booleanPreferencesKey("setup_done")
        private val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
        private val KEY_PASSWORD_SALT = stringPreferencesKey("password_salt")
        private val KEY_VAULT_FOLDER_ID = stringPreferencesKey("vault_folder_id")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_BIOMETRIC_IV = stringPreferencesKey("biometric_iv")
        private val KEY_BIOMETRIC_CT = stringPreferencesKey("biometric_ct")
        private val KEY_IMAGE_PREVIEW = booleanPreferencesKey("image_preview_enabled")
        private val KEY_VIDEO_PREVIEW = booleanPreferencesKey("video_preview_enabled")
        private val KEY_AUTO_LOCK_MINUTES = stringPreferencesKey("auto_lock_minutes")
        private val KEY_FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        private val KEY_LOCKOUT_UNTIL_MS = longPreferencesKey("lockout_until_ms")
        private val KEY_HAS_VAULT_KEY = booleanPreferencesKey("has_vault_key")
        private val KEY_VAULT_KEY_FOLDER_ID = stringPreferencesKey("vault_key_folder_id")
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    val isSetupDone: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_DONE] ?: false }

    suspend fun savePasswordHash(hash: ByteArray, salt: ByteArray) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PASSWORD_HASH] = Base64.encodeToString(hash, Base64.DEFAULT)
            prefs[KEY_PASSWORD_SALT] = Base64.encodeToString(salt, Base64.DEFAULT)
            prefs[KEY_SETUP_DONE] = true
        }
    }

    suspend fun getPasswordHashAndSalt(): Pair<ByteArray, ByteArray>? {
        val prefs = context.dataStore.data.first()
        val hash = prefs[KEY_PASSWORD_HASH]?.let { Base64.decode(it, Base64.DEFAULT) } ?: return null
        val salt = prefs[KEY_PASSWORD_SALT]?.let { Base64.decode(it, Base64.DEFAULT) } ?: return null
        return hash to salt
    }

    // ── Drive folder ──────────────────────────────────────────────────────

    val vaultFolderId: Flow<String?> = context.dataStore.data.map { it[KEY_VAULT_FOLDER_ID] }

    suspend fun saveVaultFolderId(folderId: String) {
        context.dataStore.edit { it[KEY_VAULT_FOLDER_ID] = folderId }
    }

    // ── Biometric ─────────────────────────────────────────────────────────

    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_ENABLED] ?: false }

    suspend fun saveBiometricCredentials(iv: ByteArray, ciphertext: ByteArray) {
        context.dataStore.edit {
            it[KEY_BIOMETRIC_IV] = Base64.encodeToString(iv, Base64.DEFAULT)
            it[KEY_BIOMETRIC_CT] = Base64.encodeToString(ciphertext, Base64.DEFAULT)
            it[KEY_BIOMETRIC_ENABLED] = true
        }
    }

    suspend fun getBiometricCredentials(): Pair<ByteArray, ByteArray>? {
        val prefs = context.dataStore.data.first()
        val iv = prefs[KEY_BIOMETRIC_IV]?.let { Base64.decode(it, Base64.DEFAULT) } ?: return null
        val ct = prefs[KEY_BIOMETRIC_CT]?.let { Base64.decode(it, Base64.DEFAULT) } ?: return null
        return iv to ct
    }

    suspend fun clearBiometricCredentials() {
        context.dataStore.edit {
            it.remove(KEY_BIOMETRIC_ENABLED)
            it.remove(KEY_BIOMETRIC_IV)
            it.remove(KEY_BIOMETRIC_CT)
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────

    val imagePreviewEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_IMAGE_PREVIEW] ?: true }
    val videoPreviewEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_VIDEO_PREVIEW] ?: false }
    val autoLockMinutes: Flow<Int> = context.dataStore.data.map { it[KEY_AUTO_LOCK_MINUTES]?.toIntOrNull() ?: 0 }

    suspend fun setImagePreview(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IMAGE_PREVIEW] = enabled }
    }

    suspend fun setVideoPreview(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIDEO_PREVIEW] = enabled }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_AUTO_LOCK_MINUTES] = minutes.toString() }
    }

    // ── Brute-force protection ────────────────────────────────────────────

    val failedAttempts: Flow<Int> = context.dataStore.data.map { it[KEY_FAILED_ATTEMPTS] ?: 0 }
    val lockoutUntilMs: Flow<Long> = context.dataStore.data.map { it[KEY_LOCKOUT_UNTIL_MS] ?: 0L }

    suspend fun recordFailedAttempt(attempts: Int, lockoutUntilMs: Long) {
        context.dataStore.edit {
            it[KEY_FAILED_ATTEMPTS] = attempts
            it[KEY_LOCKOUT_UNTIL_MS] = lockoutUntilMs
        }
    }

    suspend fun clearFailedAttempts() {
        context.dataStore.edit {
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    // ── Vault key (envelope encryption) ──────────────────────────────────

    val hasVaultKey: Flow<Boolean> = context.dataStore.data.map { it[KEY_HAS_VAULT_KEY] ?: false }
    val vaultKeyFolderId: Flow<String?> = context.dataStore.data.map { it[KEY_VAULT_KEY_FOLDER_ID] }

    suspend fun setHasVaultKey(folderId: String) {
        context.dataStore.edit {
            it[KEY_HAS_VAULT_KEY] = true
            it[KEY_VAULT_KEY_FOLDER_ID] = folderId
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
