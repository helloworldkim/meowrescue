package com.meowrescue.game.threading

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Test scaffolding for suspend-function unit tests.
 *
 * Canonical pattern:
 *
 * ```kotlin
 * @Test fun `example suspend test`() = runTest {
 *     val result = suspendFunctionUnderTest()
 *     assertEquals(expected, result)
 * }
 * ```
 *
 * Virtual time — free delay-skipping:
 *
 * ```kotlin
 * @Test fun `example with delay`() = runTest {
 *     val deferred = async { delay(5000L); 42 }
 *     advanceTimeBy(5000L)
 *     assertEquals(42, deferred.await())
 * }
 * ```
 *
 * Forbidden:
 *  - `runBlocking { }` — flaky, uses real wall clock
 *  - `runBlockingTest { }` — deprecated since coroutines 1.6
 *
 * Prefer `runTest` directly for the simple case. Use these helpers when a test
 * needs an explicit [TestScope] to demonstrate dispatcher behaviour (e.g., the
 * Standard vs Unconfined contrast in [CoroutineTestSupportSelfTest]).
 */
object CoroutineTestSupport {

    /**
     * Returns a [TestScope] backed by [StandardTestDispatcher].
     *
     * Coroutines launched in this scope are queued and do not execute until
     * [TestScope.testScheduler] is advanced (via [advanceTime] or
     * `advanceUntilIdle()`). Use when the test logic depends on coroutine
     * ordering or explicit virtual-time control.
     */
    fun standardScope(): TestScope = TestScope(StandardTestDispatcher())

    /**
     * Returns a [TestScope] backed by [UnconfinedTestDispatcher].
     *
     * Coroutines launched in this scope run eagerly on the calling thread.
     * Use when the test only asserts end-state and does not care about
     * intermediate ordering.
     */
    fun unconfinedScope(): TestScope = TestScope(UnconfinedTestDispatcher())

    /**
     * Advances the virtual clock of [scope] by [ms] milliseconds and runs all
     * coroutines whose delay has now elapsed.
     *
     * Equivalent to calling `testScheduler.advanceTimeBy(ms)` followed by
     * `testScheduler.runCurrent()`. Both calls are required — `advanceTimeBy`
     * alone only schedules runnables; `runCurrent` actually executes them.
     *
     * @param scope The [TestScope] whose scheduler to advance.
     * @param ms    Milliseconds of virtual time to advance.
     */
    fun advanceTime(scope: TestScope, ms: Long) {
        scope.testScheduler.advanceTimeBy(ms)
        scope.testScheduler.runCurrent()
    }
}
