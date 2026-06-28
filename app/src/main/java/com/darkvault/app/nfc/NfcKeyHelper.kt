package com.darkvault.app.nfc

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object NfcKeyHelper {

    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_BITS = 256

    /**
     * Derives a 256-bit KEK from [secret] (NFC tag data or card identifier) and a [bindingSalt]
     * stored on the device. 100k PBKDF2 iterations.
     *
     * The bindingSalt makes the KEK device-specific: an attacker who copies/skims the NFC
     * secret still needs the salt from the device's DataStore.
     */
    fun deriveKek(secret: ByteArray, bindingSalt: ByteArray): ByteArray =
        pbkdf2(secret, bindingSalt, 100_000)

    /**
     * Two-pass derivation for tap+PIN mode.
     * step1 = PBKDF2(secret, bindingSalt, 50k) — binds to the physical tag + device salt
     * kek   = PBKDF2(pin,    step1,       50k) — step1 is the PIN derivation salt
     * Neither factor alone reproduces the KEK.
     * (A separate pinSalt was considered but step1 already provides strong tag+device binding
     * as the PIN salt, making a third salt redundant and confusing.)
     */
    fun deriveKekWithPin(
        secret: ByteArray,
        bindingSalt: ByteArray,
        pin: ByteArray
    ): ByteArray {
        val step1 = pbkdf2(secret, bindingSalt, 50_000)
        return try {
            pbkdf2(pin, step1, 50_000)
        } finally {
            Arrays.fill(step1, 0)
        }
    }

    /** AES-256-GCM encrypt [plaintext] with [kek]. Returns (iv, ciphertext+GCM-tag). */
    fun encryptBlob(plaintext: ByteArray, kek: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv to cipher.doFinal(plaintext)
    }

    /** AES-256-GCM decrypt. Throws AEADBadTagException on wrong key. */
    fun decryptBlob(iv: ByteArray, ct: ByteArray, kek: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    /**
     * PBKDF2-SHA256 with [iterations], 256-bit output.
     * [password] bytes are hex-encoded to preserve entropy through PBEKeySpec's char[] boundary.
     */
    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hexChars = password.joinToString("") { "%02x".format(it) }.toCharArray()
        val spec = PBEKeySpec(hexChars, salt, iterations, KEY_BITS)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            Arrays.fill(hexChars, ' ')
        }
    }
}
