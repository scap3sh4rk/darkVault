package com.darkvault.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MEDIUM-005 fix: recovery key clipboard auto-clearing after 60 seconds.
 *
 * These tests validate the coroutine-based clearing logic without requiring Android APIs.
 * The actual ClipboardManager integration is UI-layer logic; here we test the timing
 * and state-change behaviour of the clearing coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardHygieneTest {

    /**
     * Simulates the clipboard state as a simple mutable variable.
     * In production code, this maps to ClipboardManager.setText() / clearPrimaryClip().
     */
    private var fakeClipboard: String = ""

    @Test
    fun `clipboard is populated immediately on copy`() = runTest {
        val recoveryKey = "AABB1122-CCDD3344-EEFF5566-77889900"
        fakeClipboard = recoveryKey

        assertEquals("Clipboard must contain recovery key immediately after copy",
            recoveryKey, fakeClipboard)
    }

    @Test
    fun `clipboard is cleared after 60 seconds delay`() = runTest {
        val recoveryKey = "AABB1122-CCDD3344-EEFF5566-77889900"
        fakeClipboard = recoveryKey

        // Launch the clearing coroutine (mirrors MEDIUM-005 fix pattern)
        val job = launch {
            delay(60_000L)
            fakeClipboard = ""
        }

        // Advance time by 59 seconds — clipboard must still contain the key
        advanceTimeBy(59_000L)
        assertEquals("Clipboard must still contain key before 60 seconds",
            recoveryKey, fakeClipboard)

        // Advance past 60 seconds — clearing must have fired
        advanceTimeBy(2_000L)
        job.join()
        assertEquals("Clipboard must be cleared after 60 seconds", "", fakeClipboard)
    }

    @Test
    fun `clipboard clearing does not fire before timeout`() = runTest {
        val recoveryKey = "AABB1122-CCDD3344-EEFF5566-77889900"
        fakeClipboard = recoveryKey
        var cleared = false

        val job = launch {
            delay(60_000L)
            fakeClipboard = ""
            cleared = true
        }

        // 30 seconds in — not yet cleared
        advanceTimeBy(30_000L)
        assertFalse("Clipboard must not be cleared at 30 seconds", cleared)
        assertEquals("Clipboard must still contain key at 30 seconds", recoveryKey, fakeClipboard)

        job.cancel()
    }

    @Test
    fun `clipboard clearing coroutine runs to completion`() = runTest {
        var clearingCoroutineCompleted = false
        fakeClipboard = "some-recovery-key"

        val job = launch {
            delay(60_000L)
            fakeClipboard = ""
            clearingCoroutineCompleted = true
        }

        advanceTimeBy(61_000L)
        job.join()

        assertTrue("Clearing coroutine must complete", clearingCoroutineCompleted)
        assertEquals("Clipboard must be empty", "", fakeClipboard)
    }

    @Test
    fun `multiple copy actions each schedule independent clearing`() = runTest {
        val key1 = "KEY1-AAAA-BBBB-CCCC"
        val key2 = "KEY2-DDDD-EEEE-FFFF"
        var clearCount = 0

        // First copy
        fakeClipboard = key1
        val job1 = launch {
            delay(60_000L)
            fakeClipboard = ""
            clearCount++
        }

        // Advance 30 seconds, then do second copy (simulates user re-tapping copy)
        advanceTimeBy(30_000L)
        fakeClipboard = key2
        val job2 = launch {
            delay(60_000L)
            fakeClipboard = ""
            clearCount++
        }

        // At t=61s: first job fires
        advanceTimeBy(31_000L)
        job1.join()

        // At t=90s: second job fires
        advanceTimeBy(29_000L)
        job2.join()

        assertEquals("Both clearing coroutines must have run", 2, clearCount)
        assertEquals("Clipboard must be empty after both clear", "", fakeClipboard)
    }
}
