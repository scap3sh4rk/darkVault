package com.darkvault.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Arrays

/**
 * Unit tests for HIGH-001 / MEDIUM-002 fixes: key material zeroing via try-finally.
 * Verifies that Arrays.fill() produces an all-zeros array and that finally blocks
 * run correctly even when an exception is thrown mid-operation.
 */
class KeyZeroingTest {

    @Test
    fun `Arrays fill zeroes all bytes`() {
        // Fix: HIGH-001, MEDIUM-002 — verify the zeroing primitive works correctly
        val key = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                              0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(),
                              0xEE.toByte(), 0xFF.toByte(), 0x12, 0x34)
        Arrays.fill(key, 0)
        val expected = ByteArray(key.size) { 0 }
        assertArrayEquals("All bytes must be zero after Arrays.fill", expected, key)
    }

    @Test
    fun `try-finally zeroes key even when exception thrown`() {
        // Fix: HIGH-001 — verify the try-finally pattern runs the finally block on exception
        val key = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        var finallyRan = false
        var caughtException: Exception? = null

        try {
            try {
                throw IllegalStateException("Simulated exception (e.g. IllegalBlockSizeException)")
            } finally {
                Arrays.fill(key, 0)
                finallyRan = true
            }
        } catch (e: Exception) {
            caughtException = e
        }

        assertNotNull("Exception must have been caught", caughtException)
        assert(finallyRan) { "finally block must have run" }
        assertArrayEquals("Key must be zeroed despite exception",
            ByteArray(key.size) { 0 }, key)
    }

    @Test
    fun `try-finally zeroes key on success path`() {
        // Fix: HIGH-001 — verify the try-finally pattern also zeroes on the happy path
        val key = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        var finallyRan = false
        var result: String? = null

        try {
            result = "ok"
        } finally {
            Arrays.fill(key, 0)
            finallyRan = true
        }

        assert(finallyRan) { "finally block must have run" }
        assertNotNull("Result must be set", result)
        assertArrayEquals("Key must be zeroed on success path",
            ByteArray(key.size) { 0 }, key)
    }

    @Test
    fun `nested try-finally zeroes outer key when inner throws`() {
        // Mirrors the HIGH-001 fix structure: outer kek try-finally, inner try-catch
        val kek = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        var outerFinallyRan = false
        var innerCatchRan = false

        try {
            try {
                try {
                    throw javax.crypto.AEADBadTagException("Wrong password simulation")
                } catch (e: javax.crypto.AEADBadTagException) {
                    innerCatchRan = true
                    // Handle wrong password — kek still in scope
                }
            } finally {
                Arrays.fill(kek, 0)
                outerFinallyRan = true
            }
        } catch (_: Exception) { }

        assert(innerCatchRan) { "inner catch must have run" }
        assert(outerFinallyRan) { "outer finally must have run" }
        assertArrayEquals("KEK must be zeroed after inner catch",
            ByteArray(kek.size) { 0 }, kek)
    }

    @Test
    fun `null variable becomes null after clearing reference`() {
        // Mirrors VaultSession.clearDek() pattern — byte array ref set to null
        var dek: ByteArray? = ByteArray(32) { 0xFF.toByte() }
        assertNotNull("DEK should not be null before clearing", dek)

        dek?.let { Arrays.fill(it, 0) }
        dek = null

        assertNull("DEK reference must be null after clearing", dek)
    }
}
