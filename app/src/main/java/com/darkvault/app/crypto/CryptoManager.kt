package com.darkvault.app.crypto

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
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

    fun decrypt(input: InputStream, output: OutputStream, password: String) {
        val salt = input.readExact(SALT_BYTES)
        val iv = input.readExact(IV_BYTES)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        val ciphertext = input.readBytes()
        output.write(cipher.doFinal(ciphertext))
        output.flush()
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
