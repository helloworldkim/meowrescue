package com.meowrescue.game.core.powerup

import com.meowrescue.game.puzzle.engine.PuzzleGrid

/**
 * Strategy interface for a single power-up's grid mutation.
 *
 * Implementations may mutate [grid] in-place. [PowerUpService] takes a
 * snapshot before calling [execute] and will restore it on [EffectOutcome.RetryExhausted].
 *
 * Implementations must be pure with respect to external state — no coin ops,
 * no repository calls. Those responsibilities belong to [PowerUpService].
 */
fun interface PowerUpEffect {
    fun execute(grid: PuzzleGrid): EffectOutcome
}
