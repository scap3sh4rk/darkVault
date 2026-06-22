package com.darkvault.app.crypto

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the vault DEK (Data Encryption Key).
 *
 * The DEK is a random 256-bit AES key. It is wrapped (encrypted) in two ways:
 *  1. With the PBKDF2-derived KEK from the user's master password
 *  2. With a random 256-bit Recovery Key (shown once at setup)
 *
 * The wrapped DEK blobs are stored in `vault.key` on Drive.
 *
 * Format of a wrapped DEK blob: [12-byte GCM IV][GCM ciphertext (32 bytes + 16-byte tag)]
 * Total: 60 bytes, Base64-encoded for storage.
 */
object VaultKeyManager {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32  // 256-bit

    /** Generates a fresh random DEK. */
    // Fix: LOW-001 — use nextBytes() instead of generateSeed() for cryptographic material
    fun generateDek(): ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }

    /** Generates a fresh random Recovery Key (256-bit). */
    // Fix: LOW-001 — use nextBytes() instead of generateSeed() for cryptographic material
    fun generateRecoveryKey(): ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }

    /**
     * Formats a recovery key as a human-readable hex string (64 hex chars, groups of 8).
     * E.g. "A1B2C3D4-E5F6A7B8-..."
     */
    fun formatRecoveryKey(key: ByteArray): String {
        val hex = key.joinToString("") { "%02X".format(it) }
        return hex.chunked(8).joinToString("-")
    }

    /**
     * Parses a recovery key from the formatted hex string (strips dashes, decodes hex).
     */
    fun parseRecoveryKey(formatted: String): ByteArray {
        val hex = formatted.replace("-", "").replace(" ", "")
        if (hex.length != KEY_BYTES * 2) throw IllegalArgumentException("Invalid recovery key length")
        return ByteArray(KEY_BYTES) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /**
     * Wraps the DEK with a wrapping key (KEK or Recovery Key).
     * Returns: [12-byte IV][32-byte ciphertext + 16-byte GCM tag] = 60 bytes total.
     */
    fun wrapDek(dek: ByteArray, wrappingKey: ByteArray): ByteArray {
        // Fix: LOW-001 — use nextBytes() instead of generateSeed() for cryptographic material
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val wrapped = cipher.doFinal(dek)
        return iv + wrapped  // 12 + 48 = 60 bytes
    }

    /**
     * Unwraps (decrypts) a wrapped DEK blob.
     * Throws if the wrapping key is wrong (GCM tag mismatch).
     */
    fun unwrapDek(wrapped: ByteArray, wrappingKey: ByteArray): ByteArray {
        val iv = wrapped.copyOfRange(0, IV_BYTES)
        val ciphertext = wrapped.copyOfRange(IV_BYTES, wrapped.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(ciphertext)
        } finally {
            Arrays.fill(iv, 0)
        }
    }

    /**
     * Encrypts plaintext with the DEK using AES-256-GCM.
     * Returns the vault payload for version 0x03: [12-byte IV][ciphertext + 16-byte GCM tag].
     */
    fun encryptWithDek(plaintext: ByteArray, dek: ByteArray): ByteArray {
        // Fix: LOW-001 — use nextBytes() instead of generateSeed() for cryptographic material
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct  // 12 + (plaintext.size + 16) bytes
    }

    /**
     * Decrypts a version 0x03 payload (everything after the version byte) with the DEK.
     */
    fun decryptWithDek(payload: ByteArray, dek: ByteArray): ByteArray {
        val iv = payload.copyOfRange(0, IV_BYTES)
        val ct = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(ct)
        } finally {
            Arrays.fill(iv, 0)
        }
    }
}
