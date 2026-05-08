package com.meowrescue.game.core.powerup

import com.meowrescue.game.puzzle.engine.PuzzleGrid

/**
 * Magnet power-up effect.
 *
 * Magnet always succeeds — the visual magnetism animation is handled by the UI
 * layer. This effect's only responsibility in the logic layer is to signal
 * [EffectOutcome.Applied] so [PowerUpService] keeps the coins spent.
 *
 * Implements: design/gdd/puzzle-system.md — Power-Up / Magnet
 */
class MagnetEffect : PowerUpEffect {
    override fun execute(grid: PuzzleGrid): EffectOutcome = EffectOutcome.Applied
}
