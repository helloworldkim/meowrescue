package com.meowrescue.game.core.threading

import android.os.Process
import android.util.Log

/**
 * Dedicated render-thread template for the Cat Launch SurfaceView loop.
 *
 * Authoritative sources: ADR-0004 § Decision (threading model),
 * ADR-0008 § Pause Semantics (pause before stop ordering).
 *
 * Consumed by LaunchView (ADR-0010) which injects a tick lambda that calls
 * PhysicsLoop.tick() and draws to the Surface canvas.
 *
 * Lifecycle: [startThread] → run loop → [pauseLoop]/[resumeLoop] as needed → [stopThread].
 *
 * Pause semantics (ADR-0008): set [paused] BEFORE flipping [running] to false so the
 * world is guaranteed paused when the caller is about to destroy. Resume resets
 * lastTickNs to 0 at the PhysicsLoop layer (not here) to avoid catch-up delta.
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
            try { sleep(FRAME_MS) } catch (_: InterruptedException) { /* exit cleanly */ }
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
        if (isAlive) Log.w(TAG, "join timed out after ${JOIN_TIMEOUT_MS}ms")
    }

    fun pauseLoop()  { paused = true  }
    fun resumeLoop() { paused = false }

    companion object {
        const val FRAME_MS = 16L
        const val JOIN_TIMEOUT_MS = 100L
        private const val TAG = "LaunchRenderThread"
    }
}
