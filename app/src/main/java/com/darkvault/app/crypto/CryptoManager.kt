package com.darkvault.app.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val ALGORITHM = "AES/GCM/NoPadding"

    // Version bytes written as the first byte of every vault file
    private const val VERSION_LEGACY: Byte = 0x00  // old format: [salt][iv][ciphertext] (no version byte)
    private const val VERSION_GZIP: Byte = 0x02    // per-file key + gzip compression
    private const val VERSION_DEK_GCM: Byte = 0x03 // envelope encryption: DEK-wrapped, gzip+AES-GCM

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun hashPassword(password: String): Pair<ByteArray, ByteArray> {
        val salt = SecureRandom().generateSeed(SALT_BYTES)
        val key = deriveKey(password, salt)
        return key.encoded to salt
    }

    fun verifyPassword(password: String, storedHash: ByteArray, salt: ByteArray): Boolean {
        return deriveKey(password, salt).encoded.contentEquals(storedHash)
    }

    /**
     * Legacy encrypt (per-file key derivation, no version byte).
     * Kept for reference — new uploads should use [encryptWithDek].
     */
    fun encrypt(input: InputStream, output: OutputStream, password: String) {
        val salt = SecureRandom().generateSeed(SALT_BYTES)
        val iv = SecureRandom().generateSeed(IV_BYTES)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        output.write(salt)
        output.write(iv)

        val buf = ByteArray(8192)
        var read: Int
        while (input.read(buf).also { read = it } != -1) {
            val chunk = cipher.update(buf, 0, read)
            if (chunk != null) output.write(chunk)
        }
        output.write(cipher.doFinal())
        output.flush()
    }

    /**
     * Encrypts [input] with the vault DEK (version 0x03).
     * Format: [0x03][12-byte GCM IV][gzip-compressed ciphertext + 16-byte GCM tag]
     *
     * @param dek  The vault Data Encryption Key (256-bit AES key bytes).
     */
    fun encryptWithDek(input: InputStream, output: OutputStream, dek: ByteArray) {
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { gz -> input.copyTo(gz) }
        val compressedBytes = compressed.toByteArray()
        try {
            val payload = VaultKeyManager.encryptWithDek(compressedBytes, dek)
            output.write(VERSION_DEK_GCM.toInt())
            output.write(payload)
            output.flush()
        } finally {
            Arrays.fill(compressedBytes, 0)
        }
    }

    /**
     * Decrypts a vault file. Handles all format versions:
     *  - Legacy (no version byte): [16-byte salt][12-byte IV][ciphertext]
     *  - 0x02: [0x02][16-byte salt][12-byte IV][gzip ciphertext]
     *  - 0x03: [0x03][12-byte IV][DEK-encrypted gzip ciphertext]  — requires [dek]
     *
     * @param password  Master password; used to derive key for legacy and 0x02 files.
     * @param dek       The vault DEK; required for 0x03 files. Pass null for old files.
     */
    fun decrypt(input: InputStream, output: OutputStream, password: String, dek: ByteArray? = null) {
        val firstByte = input.read()
        if (firstByte == -1) throw IOException("Empty vault file")

        when (firstByte.toByte()) {
            VERSION_DEK_GCM -> {
                // v0.03: DEK-wrapped, gzip-compressed
                if (dek == null) throw IOException("DEK required for v0.3 vault files but none provided")
                val payload = input.readBytes()
                val decrypted = VaultKeyManager.decryptWithDek(payload, dek)
                try {
                    GZIPInputStream(ByteArrayInputStream(decrypted)).use { it.copyTo(output) }
                    output.flush()
                } finally {
                    Arrays.fill(decrypted, 0)
                }
            }

            VERSION_GZIP -> {
                // v0.02: per-file key from PBKDF2 + gzip
                val salt = input.readExact(SALT_BYTES)
                val iv = input.readExact(IV_BYTES)
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                val ciphertext = input.readBytes()
                val decompressed = cipher.doFinal(ciphertext)
                GZIPInputStream(ByteArrayInputStream(decompressed)).use { it.copyTo(output) }
                output.flush()
            }

            else -> {
                // Legacy format: first byte is the first byte of the 16-byte salt
                // Re-assemble: [firstByte] + remaining 15 bytes = full salt
                val remainingSalt = input.readExact(SALT_BYTES - 1)
                val salt = byteArrayOf(firstByte.toByte()) + remainingSalt
                val iv = input.readExact(IV_BYTES)
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                val ciphertext = input.readBytes()
                output.write(cipher.doFinal(ciphertext))
                output.flush()
            }
        }
    }

    private fun InputStream.readExact(n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = read(buf, offset, n - offset)
            if (read == -1) throw IOException("Truncated vault file")
            offset += read
        }
        return buf
    }
}
