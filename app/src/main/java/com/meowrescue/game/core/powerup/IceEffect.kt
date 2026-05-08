package com.meowrescue.game.core.powerup

import com.meowrescue.game.core.util.RngSource
import com.meowrescue.game.puzzle.engine.PuzzleGrid

/**
 * Ice power-up effect.
 *
 * Removes one randomly-selected obstacle block (non-cat, non-wall, non-key).
 * Returns [EffectOutcome.NoEffect] — and triggers a coin refund — when no
 * removable blocks exist (grid contains only cat / wall / key blocks).
 *
 * Implements: design/gdd/puzzle-system.md — Power-Up / Ice
 *
 * @param rng Randomness source. Inject [{ bound -> Random.nextInt(bound) }] in
 *   production; inject a controlled sequence in tests.
 */
class IceEffect(private val rng: RngSource) : PowerUpEffect {

    override fun execute(grid: PuzzleGrid): EffectOutcome {
        val removable = grid.blocks.filter { !it.isCat && !it.isWall && !it.isKey }
        if (removable.isEmpty()) return EffectOutcome.NoEffect
        val target = removable[rng.nextInt(removable.size)]
        grid.removeBlock(target.id)
        return EffectOutcome.Applied
    }
}
