package com.darkvault.app.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
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

    // Version byte prepended to new vault files. Legacy files have no version byte.
    private const val VERSION_GZIP: Byte = 0x02

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun hashPassword(password: String): Pair<ByteArray, ByteArray> {
        val salt = SecureRandom().generateSeed(SALT_BYTES)
        val key = deriveKey(password, salt)
        val encoded = key.encoded.copyOf()
        return encoded to salt
    }

    fun verifyPassword(password: String, storedHash: ByteArray, salt: ByteArray): Boolean {
        val derived = deriveKey(password, salt).encoded
        return try {
            MessageDigest.isEqual(derived, storedHash)
        } finally {
            Arrays.fill(derived, 0)
        }
    }

    fun encrypt(input: InputStream, output: OutputStream, password: String) {
        val salt = SecureRandom().generateSeed(SALT_BYTES)
        val iv = SecureRandom().generateSeed(IV_BYTES)
        val key = deriveKey(password, salt)
        val keyBytes = key.encoded
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { gz -> input.copyTo(gz) }
        val compressedBytes = compressed.toByteArray()

        try {
            output.write(VERSION_GZIP.toInt())
            output.write(salt)
            output.write(iv)
            output.write(cipher.doFinal(compressedBytes))
            output.flush()
        } finally {
            Arrays.fill(keyBytes, 0)
            Arrays.fill(compressedBytes, 0)
            Arrays.fill(iv, 0)
            Arrays.fill(salt, 0)
        }
    }

    fun decrypt(input: InputStream, output: OutputStream, password: String) {
        val firstByte = input.read()
        if (firstByte == -1) throw IOException("Empty vault file")

        val versioned = firstByte.toByte() == VERSION_GZIP

        val salt: ByteArray
        val iv: ByteArray

        if (versioned) {
            salt = input.readExact(SALT_BYTES)
            iv = input.readExact(IV_BYTES)
        } else {
            // Legacy format: firstByte is the first byte of the salt
            val rest = input.readExact(SALT_BYTES - 1)
            salt = ByteArray(SALT_BYTES).also {
                it[0] = firstByte.toByte()
                rest.copyInto(it, destinationOffset = 1)
            }
            iv = input.readExact(IV_BYTES)
        }

        val key = deriveKey(password, salt)
        val keyBytes = key.encoded
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = input.readBytes()
        val decrypted = cipher.doFinal(ciphertext)

        try {
            if (versioned) {
                GZIPInputStream(ByteArrayInputStream(decrypted)).use { it.copyTo(output) }
            } else {
                output.write(decrypted)
            }
            output.flush()
        } finally {
            Arrays.fill(keyBytes, 0)
            Arrays.fill(decrypted, 0)
            Arrays.fill(iv, 0)
            Arrays.fill(salt, 0)
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
