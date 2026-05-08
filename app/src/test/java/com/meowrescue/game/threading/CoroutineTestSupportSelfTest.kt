package com.meowrescue.game.threading

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Self-tests for [CoroutineTestSupport].
 *
 * These four tests cover the four acceptance criteria (a)–(d) from
 * story-008 and serve as living examples of each scaffolding feature.
 */
class CoroutineTestSupportSelfTest {

    // -------------------------------------------------------------------------
    // AC (a) — runTest invokes a suspend function and asserts on its result
    // -------------------------------------------------------------------------

    @Test
    fun test_runTest_suspendFunction_returnsExpectedResult() = runTest {
        // Arrange
        suspend fun stubSuspend(): Int = 42

        // Act
        val result = stubSuspend()

        // Assert
        assertEquals(42, result)
    }

    // -------------------------------------------------------------------------
    // AC (b) — advanceTime moves virtual clock; delay(5000L) resolves in zero wall-clock
    // -------------------------------------------------------------------------

    @Test
    fun test_advanceTime_longDelay_resolvesWithoutWallClock() = runTest {
        // Arrange
        val scope = CoroutineTestSupport.standardScope()
        var result: Int? = null

        // Act — launch a coroutine that sleeps for 5 seconds of virtual time
        scope.launch {
            delay(5_000L)
            result = 42
        }

        // Virtual clock has not moved yet — coroutine is still waiting
        assertNull(result)

        // Bracket the advance in real wall-clock time to prove the 5-second
        // virtual delay resolves without actually sleeping (story AC-2 § Then).
        val wallStart = System.currentTimeMillis()
        CoroutineTestSupport.advanceTime(scope, 5_000L)
        val wallElapsed = System.currentTimeMillis() - wallStart

        // Assert — virtual time advanced; delay expired; result set
        assertEquals(42, result)
        assertTrue(
            "virtual delay took ${wallElapsed}ms, expected <100ms",
            wallElapsed < 100L
        )
    }

    // -------------------------------------------------------------------------
    // AC (c) — UnconfinedTestDispatcher runs body eagerly; result visible immediately after launch
    // -------------------------------------------------------------------------

    @Test
    fun test_unconfinedScope_launch_resultVisibleImmediately() {
        // Arrange
        val scope = CoroutineTestSupport.unconfinedScope()
        var result: Int? = null

        // Act — launch runs eagerly on the calling thread; no advance needed
        scope.launch {
            result = 42
        }

        // Assert — result is already set without any advanceUntilIdle
        assertEquals(42, result)
    }

    // -------------------------------------------------------------------------
    // AC (d) — StandardTestDispatcher defers until advanceUntilIdle
    // -------------------------------------------------------------------------

    @Test
    fun test_standardScope_launch_deferredUntilAdvanceUntilIdle() {
        // Arrange
        val scope = CoroutineTestSupport.standardScope()
        var result: Int? = null

        // Act — launch queues the coroutine; it does NOT run yet
        scope.launch {
            result = 42
        }

        // Assert pre-advance: coroutine has not executed
        assertNull(result)

        // Drain the scheduler
        scope.testScheduler.advanceUntilIdle()

        // Assert post-advance: coroutine has now executed
        assertEquals(42, result)
    }
}
