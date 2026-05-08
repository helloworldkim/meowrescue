package com.meowrescue.game.core.threading

import com.meowrescue.game.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

/**
 * Project threading policy. These are the only sanctioned coroutine contexts.
 *
 * ## Sanctioned contexts
 *
 * - [Dispatchers.Main]    — Android UI thread: View ops, dialogs, HUD updates,
 *                           Activity lifecycle callbacks.
 *                           **Never**: DB calls, file I/O, blocking computation.
 *
 * - [Dispatchers.IO]      — Room + file I/O, encapsulated inside `GameRepository`.
 *                           Callers never specify this dispatcher directly; the
 *                           Repository wrapper applies it.
 *                           **Never**: CPU-bound work (BFS, physics calculation).
 *
 * - [Dispatchers.Default] — CPU-bound work: Puzzle BFS, Launch stage generation.
 *                           **Never**: DB calls; do not replace IO with Default.
 *
 * - Render Thread         — Cat Launch SurfaceView 60 Hz physics + drawing loop.
 *                           Manual `Thread` (not a coroutine). Communicates back
 *                           to Main via `Activity.runOnUiThread { }`.
 *                           **Never**: Room access, suspend functions.
 *
 * ## Forbidden patterns (any layer)
 *
 * - `runBlocking` on the Main thread — causes ANR.
 * - `Dispatchers.IO` for CPU-bound work — wastes IO pool threads.
 * - `Dispatchers.Main` for DB calls — causes ANR.
 * - `GlobalScope` — leaks coroutines across Activity lifecycle (see story 002).
 *
 * Use [assertDispatcher] in debug builds to verify callers respect these rules.
 */
object DispatcherPolicy {
    /** Android UI thread. Use for View ops, dialogs, and HUD updates. */
    val Main: CoroutineDispatcher = Dispatchers.Main

    /** Room + file I/O pool. Encapsulated inside GameRepository — callers do not use directly. */
    val Io: CoroutineDispatcher = Dispatchers.IO

    /** CPU-bound work pool. Use for puzzle BFS and Launch stage generation. */
    val Default: CoroutineDispatcher = Dispatchers.Default
}

/**
 * Debug-only assertion that the current coroutine is running on [expected].
 *
 * Throws [IllegalStateException] if the actual dispatcher does not match.
 * No-op in release builds (`BuildConfig.DEBUG == false`), so callers can leave
 * assertions in production code paths without any performance cost.
 *
 * AC-4 (release no-op) is verified by inspection of the `if (!BuildConfig.DEBUG) return`
 * guard below. Programmatic verification requires a debug/release source-set split
 * scheduled for Sprint 2.
 *
 * @param expected The dispatcher this coroutine is required to be running on.
 * @throws IllegalStateException if the actual dispatcher differs from [expected].
 */
suspend fun assertDispatcher(expected: CoroutineDispatcher) {
    if (!BuildConfig.DEBUG) return
    val actual = coroutineContext[ContinuationInterceptor]
    check(actual === expected) {
        "Expected dispatcher $expected, but running on $actual"
    }
}
