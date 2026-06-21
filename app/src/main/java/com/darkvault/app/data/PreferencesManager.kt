package com.darkvault.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_SETUP_DONE = booleanPreferencesKey("setup_done")
        private val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
        private val KEY_PASSWORD_SALT = stringPreferencesKey("password_salt")
        private val KEY_VAULT_FOLDER_ID = stringPreferencesKey("vault_folder_id")
    }

    val isSetupDone: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_DONE] ?: false }

    val vaultFolderId: Flow<String?> = context.dataStore.data.map { it[KEY_VAULT_FOLDER_ID] }

    suspend fun savePasswordHash(hash: ByteArray, salt: ByteArray) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PASSWORD_HASH] = Base64.encodeToString(hash, Base64.DEFAULT)
            prefs[KEY_PASSWORD_SALT] = Base64.encodeToString(salt, Base64.DEFAULT)
            prefs[KEY_SETUP_DONE] = true
        }
    }

    suspend fun getPasswordHashAndSalt(): Pair<ByteArray, ByteArray>? {
        val prefs = context.dataStore.data.map { it }.let { flow ->
            var result: Preferences? = null
            flow.collect { result = it }
            result
        } ?: return null

        val hash = prefs[KEY_PASSWORD_HASH]?.let { Base64.decode(it, Base64.DEFAULT) } ?: return null
        val salt = prefs[KEY_PASSWORD_SALT]?.let { Base64.decode(it, Base64.DEFAULT) } ?: return null
        return hash to salt
    }

    suspend fun saveVaultFolderId(folderId: String) {
        context.dataStore.edit { it[KEY_VAULT_FOLDER_ID] = folderId }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
