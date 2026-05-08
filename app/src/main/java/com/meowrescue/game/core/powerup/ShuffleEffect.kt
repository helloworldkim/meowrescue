package com.meowrescue.game.core.powerup

import com.meowrescue.game.core.util.RngSource
import com.meowrescue.game.puzzle.engine.PuzzleGrid

/**
 * Shuffle power-up effect.
 *
 * Randomly redistributes all non-cat, non-wall block positions and BFS-checks
 * solvability. Retries up to [MAX_ATTEMPTS] times. Returns [EffectOutcome.Applied]
 * on the first solvable arrangement found, or [EffectOutcome.RetryExhausted] if
 * every attempt fails.
 *
 * On [EffectOutcome.RetryExhausted] the grid is left in an indeterminate shuffled
 * state — [PowerUpService] is responsible for restoring the pre-execute snapshot.
 *
 * Implements: design/gdd/puzzle-system.md — Power-Up / Shuffle
 *
 * @param solver   Lambda wrapping [PuzzleSolver.solveFast]. Returns ≥ 1 if solvable,
 *                 0 if already solved, -1 if unsolvable / state-budget exceeded.
 * @param rng      Randomness source for the Fisher-Yates position shuffle.
 */
class ShuffleEffect(
    private val solver: (PuzzleGrid) -> Int,
    private val rng: RngSource
) : PowerUpEffect {

    companion object {
        const val MAX_ATTEMPTS = 5
    }

    override fun execute(grid: PuzzleGrid): EffectOutcome {
        repeat(MAX_ATTEMPTS) {
            grid.shuffleNonCatBlocks(rng)
            if (solver(grid) >= 1) return EffectOutcome.Applied
        }
        return EffectOutcome.RetryExhausted
    }
}
