package com.meowrescue.game.core.threading

import androidx.appcompat.app.AppCompatActivity

/**
 * Activity-side pause/resume coordination for [LaunchRenderThread].
 *
 * Ordering invariant (ADR-0004 § Rules + ADR-0008 § Pause Semantics):
 *   onPause   → renderThread.pauseLoop()   (stop ticking immediately)
 *   onResume  → renderThread.resumeLoop()  (only if pause menu is hidden)
 *   onDestroy → renderThread.stopThread()  (join within 100ms)
 *
 * The render thread's [LaunchRenderThread.running] flag is flipped by
 * [LaunchRenderThread.stopThread]. [LaunchRenderThread.pauseLoop] and
 * [LaunchRenderThread.resumeLoop] only toggle [LaunchRenderThread.paused] —
 * the thread keeps running but skips tick() while paused. This lets
 * resumeLoop be cheap (no thread-recreation).
 *
 * Downstream contract: on [LaunchRenderThread.resumeLoop], the physics-core
 * [PhysicsLoop] must reset `lastTickNs = 0` to avoid a catch-up delta after a
 * long background period. See ADR-0008 § Pause Semantics.
 */
abstract class BaseLaunchActivity : AppCompatActivity() {

    protected abstract val renderThread: LaunchRenderThread

    /** Override to return `true` while the in-game pause menu is visible. */
    protected open val isPauseMenuShown: Boolean = false

    override fun onPause() {
        renderThread.pauseLoop()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!isPauseMenuShown) renderThread.resumeLoop()
    }

    override fun onDestroy() {
        renderThread.stopThread()
        super.onDestroy()
    }
}
