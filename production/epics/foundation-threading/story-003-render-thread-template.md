# Story 003: Render thread template (`LaunchRenderThread`)

> **Epic**: foundation-threading
> **Status**: Ready
> **Layer**: Foundation
> **Type**: Logic
> **Manifest Version**: 2026-04-18

## Context

**GDD**: `design/gdd/cat-launch-system.md` (60Hz physics + rendering requirement)
**Requirement**: `TR-launch-001` (render-thread slice of the JBox2D 60Hz step + render loop)

**ADR Governing Implementation**: ADR-0004 (Threading & Coroutine Model); consumed by ADR-0008 (JBox2D Integration) and ADR-0010 (Rendering — `LaunchView` Surface 2)
**ADR Decision Summary**: Cat Launch runs on a dedicated manual `Thread` at `THREAD_PRIORITY_DISPLAY`, not a coroutine. Lifecycle flags are `@Volatile var running` / `@Volatile var paused`. 16 ms sleep-based pacing. Never coroutine-based (latency jitter of `Dispatchers.Default` is unacceptable for 60Hz).

**Engine**: Native Android (Kotlin) · `java.lang.Thread` + `Process.setThreadPriority` | **Risk**: LOW
**Engine Notes**: `THREAD_PRIORITY_DISPLAY` = -4, stable since API 1. `@Volatile` semantics are JVM-stable.

**Control Manifest Rules (Foundation — threading)**:
- Required: Cat Launch render loop on dedicated thread with `THREAD_PRIORITY_DISPLAY`; `@Volatile var running` for stop signal; `@Volatile var paused` for pause signal.
- Required: 16 ms sleep-based frame pacing (60Hz target).
- Forbidden: Coroutine-based render loop (latency jitter unacceptable).

---

## Acceptance Criteria

*From ADR-0004 § Decision + § Implementation Guidelines + ADR-0008 dependency:*

- [ ] `LaunchRenderThread` class extends `Thread`. Constructor accepts a `tick: () -> Unit` callback (caller — physics-core — injects the physics step + draw logic).
- [ ] `@Volatile var running: Boolean = false` and `@Volatile var paused: Boolean = false` are the only lifecycle signals.
- [ ] `override fun run()`:
  1. `Process.setThreadPriority(THREAD_PRIORITY_DISPLAY)` at entry.
  2. Loop while `running`: if `!paused`, invoke `tick()`; sleep 16 ms (constant, not delta-compensating — physics accumulator lives in ADR-0008's `PhysicsLoop`, not here).
  3. On exit, thread terminates naturally.
- [ ] `fun startThread()` method: sets `running = true`, then calls `start()`.
- [ ] `fun stopThread()` method: sets `running = false`, then calls `join()` with a 100 ms timeout; if timeout expires, logs a warning but does not throw.
- [ ] `fun pause()` / `fun resume()`: toggle the `paused` flag. No thread-state mutation.
- [ ] Logic test `LaunchRenderThreadTest` demonstrates: (a) thread starts and calls `tick()` repeatedly, (b) `pause()` stops `tick()` calls within 32 ms, (c) `resume()` restarts them, (d) `stopThread()` joins within 100 ms, (e) thread priority is `THREAD_PRIORITY_DISPLAY` while running.
- [ ] KDoc on `LaunchRenderThread` names ADR-0004 § Decision and ADR-0008 § Pause Semantics as authoritative sources.

---

## Implementation Notes

*From ADR-0004 § Decision (verbatim) + ADR-0008 § Pause Semantics consumption:*

```kotlin
// core/threading/LaunchRenderThread.kt
/**
 * Dedicated render-thread template for the Cat Launch SurfaceView loop.
 *
 * Consumed by [LaunchView] (ADR-0010) which injects a tick lambda that calls
 * PhysicsLoop.tick() + draws to the Surface canvas.
 *
 * Lifecycle: [startThread] → run loop → [pause]/[resume] as needed → [stopThread].
 *
 * Pause semantics (ADR-0008 § Pause Semantics): set [paused] BEFORE flipping
 * [running] to false so the world is guaranteed paused when the caller is
 * about to destroy. Resume resets lastTickNs to 0 at the PhysicsLoop layer
 * (not here) to avoid catch-up delta.
 */
class LaunchRenderThread(
    private val tick: () -> Unit,
    threadName: String = "LaunchRenderThread",
) : Thread(threadName) {

    @Volatile var running: Boolean = false
        private set
    @Volatile var paused: Boolean = false
        private set

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
        while (running) {
            if (!paused) tick()
            try { sleep(FRAME_MS) } catch (_: InterruptedException) { /* exit */ }
        }
    }

    fun startThread() {
        running = true
        start()
    }

    fun stopThread() {
        running = false
        try {
            join(JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) { /* best-effort */ }
        if (isAlive) Log.w("LaunchRenderThread", "join timed out after ${JOIN_TIMEOUT_MS}ms")
    }

    fun pauseLoop()  { paused = true  }
    fun resumeLoop() { paused = false }

    companion object {
        const val FRAME_MS = 16L
        const val JOIN_TIMEOUT_MS = 100L
    }
}
```

- Expose `startThread` / `stopThread` (not the inherited `start`/`join`) so call sites can be found via static search.
- `pauseLoop`/`resumeLoop` names avoid collision with `Thread.resume()` (deprecated).
- Test uses `AtomicInteger` tick counter; lets the thread run ~50 ms, asserts `count > 1`; `pauseLoop()`; sleeps 32 ms; snapshots count; sleeps 32 ms more; asserts count unchanged.

---

## Out of Scope

*Handled by neighbouring stories — do not implement here:*

- Story 004: Render → Main handoff (`runOnUiThread` helper)
- Story 005: Activity ↔ render-thread pause/resume coordination (at the Activity lifecycle level)
- Core physics epic (ADR-0008): `PhysicsLoop` — the actual physics step + accumulator that runs inside `tick()`
- Presentation epic (ADR-0010): `LaunchView` SurfaceView integration that owns this thread

---

## QA Test Cases

**AC-1 — Thread starts and ticks**
  - Given: `LaunchRenderThread` constructed with a `tick` lambda that increments an `AtomicInteger`.
  - When: `startThread()` is called; test sleeps 50 ms.
  - Then: Counter > 1 (at least 2 ticks at 16 ms each).
  - Edge cases: Counter should be between 2 and 4 inclusive — confirms pacing is ~16 ms, not busy-loop.

**AC-2 — Pause stops tick calls within 32 ms**
  - Given: Running thread from AC-1.
  - When: `pauseLoop()` is called; snapshot counter; sleep 32 ms; snapshot again.
  - Then: Both snapshots equal.
  - Edge cases: No mid-pause race — tests the flag-before-sleep ordering.

**AC-3 — Resume restarts tick calls**
  - Given: Paused thread from AC-2.
  - When: `resumeLoop()` is called; sleep 50 ms.
  - Then: Counter increases again.

**AC-4 — Stop joins within 100 ms**
  - Given: Running thread.
  - When: `stopThread()` is called.
  - Then: `isAlive == false` AND wall-clock time < 120 ms.

**AC-5 — Thread priority is `THREAD_PRIORITY_DISPLAY` while running**
  - Given: Running thread.
  - When: Thread reads its own priority via `Process.getThreadPriority(Process.myTid())` inside the tick lambda.
  - Then: Value == `Process.THREAD_PRIORITY_DISPLAY` (-4).

---

## Test Evidence

**Story Type**: Logic
**Required evidence**: `tests/unit/threading/launch_render_thread_test.kt` — must exist and pass all 5 ACs.
**Additional**: `production/qa/evidence/render-thread-manual-test.md` — one-page manual test on a Cat Launch stub Activity documenting start → pause → resume → stop on a real device, with logcat snippet showing `THREAD_PRIORITY_DISPLAY` confirmation.

**Status**: [ ] Not yet created

---

## Dependencies

- Depends on: Story 001 (dispatcher policy — this story's KDoc references `DispatcherPolicy` for where render threads sit relative to coroutine contexts), Story 008 (test scaffolding).
- Unlocks: Story 004 (render → Main handoff extension), Story 005 (pause/resume coordination), all physics-core stories (ADR-0008), `LaunchView` stories (ADR-0010).
