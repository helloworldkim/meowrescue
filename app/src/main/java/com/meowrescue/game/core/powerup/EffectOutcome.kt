package com.meowrescue.game.core.powerup

/**
 * Result returned by a [PowerUpEffect] after executing on a [PuzzleGrid].
 *
 * [PowerUpService] uses this to decide whether to refund coins and whether
 * to restore the grid from its pre-execution snapshot.
 */
sealed class EffectOutcome {
    /** Effect executed and changed the grid meaningfully. Coins are kept. */
    object Applied : EffectOutcome()

    /**
     * Effect had nothing to act on (e.g. Ice on a grid with only cat/wall/key blocks).
     * Coins are refunded; grid is unchanged.
     */
    object NoEffect : EffectOutcome()

    /**
     * Shuffle exhausted all [ShuffleEffect.MAX_ATTEMPTS] attempts without producing
     * a solvable arrangement. Coins are refunded; grid is restored from pre-execute snapshot.
     */
    object RetryExhausted : EffectOutcome()
}
