package com.darkvault.app.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages a Keystore-backed AES key protected by biometric authentication.
 * The key encrypts the master password so it can be restored without re-entry.
 */
object BiometricKeyManager {

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "darkvault_bio_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun getCipherForEncryption(): Cipher {
        ensureKey()
        val key = loadKey()
        return Cipher.getInstance(TRANSFORMATION).also { it.init(Cipher.ENCRYPT_MODE, key) }
    }

    fun getCipherForDecryption(iv: ByteArray): Cipher {
        val key = loadKey()
        return Cipher.getInstance(TRANSFORMATION).also {
            it.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
    }

    fun keyExists(): Boolean {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        return ks.containsAlias(ALIAS)
    }

    fun deleteKey() {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
    }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        return ks.getKey(ALIAS, null) as SecretKey
    }

    @Suppress("DEPRECATION")
    private fun ensureKey() {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        if (ks.containsAlias(ALIAS)) return

        val specBuilder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }

        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(specBuilder.build()) }
            .generateKey()
    }
}
