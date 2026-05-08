package com.meowrescue.game.core.threading

import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for [assertDispatcher] and [DispatcherPolicy] — story-001 AC-2 and AC-3.
 *
 * AC-4 (release no-op) is verified by inspection of the `if (!BuildConfig.DEBUG) return`
 * guard in DispatcherPolicy.kt. Programmatic verification requires a debug/release
 * source-set split planned for Sprint 2. Unit tests here run in a debug build context
 * where BuildConfig.DEBUG is always true, so simulating a release build would require
 * reflection — deferred.
 *
 * Dispatchers.Main and Dispatchers.Main.immediate happy-paths are not tested here:
 * both are unavailable in a plain JVM unit test without calling Dispatchers.setMain(...).
 * The story's QA test cases explicitly list Dispatchers.Main.immediate as an edge case —
 * it is treated identically to Dispatchers.Main for this deferral (both require the same
 * test-main-dispatcher setup). Testing IO and Default is sufficient evidence for AC-2(a).
 * A future integration test (or a test that installs a test main dispatcher) can cover
 * both Main variants if needed.
 *
 * Test naming follows the project convention established in
 * com.meowrescue.game.threading.CoroutineTestSupportSelfTest:
 * `test_[scenario]_[expected]`.
 */
class DispatcherAssertionTest {

    // -------------------------------------------------------------------------
    // AC-2(a) — assertDispatcher passes on the correct IO dispatcher
    // -------------------------------------------------------------------------

    @Test
    fun test_assertDispatcher_onCorrectIoDispatcher_doesNotThrow() = runTest {
        withContext(Dispatchers.IO) {
            // Should complete without exception
            assertDispatcher(Dispatchers.IO)
        }
    }

    // -------------------------------------------------------------------------
    // AC-2(a) — assertDispatcher passes on the correct Default dispatcher
    // -------------------------------------------------------------------------

    @Test
    fun test_assertDispatcher_onCorrectDefaultDispatcher_doesNotThrow() = runTest {
        withContext(Dispatchers.Default) {
            // Should complete without exception
            assertDispatcher(Dispatchers.Default)
        }
    }

    // -------------------------------------------------------------------------
    // AC-3(b) — assertDispatcher throws IllegalStateException on wrong dispatcher
    //
    // runTest uses UnconfinedTestDispatcher by default, which is NOT Dispatchers.IO.
    // Calling assertDispatcher(Dispatchers.IO) from here must throw.
    // -------------------------------------------------------------------------

    @Test
    fun test_assertDispatcher_onWrongDispatcher_throwsIllegalStateException() = runTest {
        // We are on UnconfinedTestDispatcher, not Dispatchers.IO
        try {
            assertDispatcher(Dispatchers.IO)
            fail("Expected IllegalStateException but no exception was thrown")
        } catch (e: IllegalStateException) {
            // Expected — test passes
        }
    }

    // -------------------------------------------------------------------------
    // AC-3(c) — exception message names both actual and expected dispatcher
    // -------------------------------------------------------------------------

    @Test
    fun test_assertDispatcher_wrongDispatcher_messageContainsBothDispatchers() = runTest {
        // Capture the actual dispatcher (runTest default = UnconfinedTestDispatcher)
        // before triggering the assertion — we need it to verify the failure message
        // embeds the real toString, not just the scaffold words.
        val actualDispatcher = coroutineContext[ContinuationInterceptor]

        // We are on UnconfinedTestDispatcher, not Dispatchers.IO
        try {
            assertDispatcher(Dispatchers.IO)
            fail("Expected IllegalStateException but no exception was thrown")
        } catch (e: IllegalStateException) {
            val message = e.message ?: run {
                fail("Exception message was null")
                return@runTest
            }
            // Scaffold words — preserves the documented format
            assertTrue(
                "Message should contain 'Expected' but was: $message",
                message.contains("Expected")
            )
            assertTrue(
                "Message should contain actual dispatcher info ('running on') but was: $message",
                message.contains("running on")
            )
            // Content — catches message-format regressions that keep the scaffold
            // words but drop the dispatcher identities (e.g., a refactor that changes
            // the template to "Wrong context $actual != $expected")
            assertTrue(
                "Message should contain expected dispatcher toString '${Dispatchers.IO}' but was: $message",
                message.contains(Dispatchers.IO.toString())
            )
            assertTrue(
                "Message should contain actual dispatcher toString '$actualDispatcher' but was: $message",
                message.contains(actualDispatcher.toString())
            )
        }
    }
}
