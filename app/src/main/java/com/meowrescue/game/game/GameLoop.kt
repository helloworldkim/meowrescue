package com.meowrescue.game.game

import com.meowrescue.game.engine.BattleEngine
import com.meowrescue.game.engine.BattleTurnPhase
import com.meowrescue.game.ui.BattleView
import com.meowrescue.game.util.GridConstants

/**
 * Animation-driven game loop for the turn-based battle system.
 * Renders at 60fps during animations, lower FPS during idle (PLAYER_INPUT).
 */
class GameLoop(
    private val battleEngine: BattleEngine,
    private val battleView: BattleView
) : Runnable {

    companion object {
        const val ACTIVE_FPS = 60
        const val IDLE_FPS = 30
        const val ACTIVE_FRAME_TIME = 1_000_000_000L / ACTIVE_FPS
        const val IDLE_FRAME_TIME = 1_000_000_000L / IDLE_FPS
    }

    @Volatile
    var running = false
    private var gameThread: Thread? = null

    // Phase animation timing
    private var phaseStartTime = 0L
    private var autoAdvanceScheduled = false

    fun start() {
        if (running) return
        running = true
        gameThread = Thread(this).also { it.start() }
    }

    fun stop() {
        running = false
        try {
            gameThread?.join(2000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        gameThread = null
    }

    override fun run() {
        while (running) {
            val frameStart = System.nanoTime()
            val phase = battleEngine.state.phase

            // Auto-advance non-interactive phases after animation delay
            handleAutoAdvance(phase)

            // Render
            battleView.render()

            // Frame timing
            val isActive = phase != BattleTurnPhase.PLAYER_INPUT ||
                    battleView.isAnimatingMatch() ||
                    battleView.effectRenderer.hasActiveEffects()
            val targetTime = if (isActive) ACTIVE_FRAME_TIME else IDLE_FRAME_TIME
            val elapsed = System.nanoTime() - frameStart
            val sleepMs = (targetTime - elapsed) / 1_000_000L
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    fun onPhaseChanged(phase: BattleTurnPhase) {
        phaseStartTime = System.currentTimeMillis()
        autoAdvanceScheduled = true
    }

    private fun handleAutoAdvance(phase: BattleTurnPhase) {
        if (!autoAdvanceScheduled) return
        val elapsed = System.currentTimeMillis() - phaseStartTime

        val delay = when (phase) {
            BattleTurnPhase.MATCHING -> GridConstants.MATCH_ANIM_MS
            BattleTurnPhase.CASCADING -> GridConstants.CASCADE_ANIM_MS
            BattleTurnPhase.PLAYER_ATTACK -> GridConstants.ATTACK_ANIM_MS
            BattleTurnPhase.ENEMY_ATTACK -> GridConstants.ATTACK_ANIM_MS
            BattleTurnPhase.VICTORY, BattleTurnPhase.DEFEAT -> 500L
            BattleTurnPhase.PLAYER_INPUT -> {
                autoAdvanceScheduled = false
                return
            }
        }

        if (elapsed >= delay) {
            autoAdvanceScheduled = false
            battleEngine.advancePhase()
        }
    }
}
