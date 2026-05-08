package com.meowrescue.game.core.powerup

/**
 * Outcome of a full [PowerUpService.apply] cycle (spend → execute → maybe refund).
 *
 * Callers inspect this to decide what to show the player:
 * - [InsufficientCoins] — nothing happened; show "not enough coins" UI
 * - [Applied]           — effect worked; coins spent; update HUD
 * - [Refunded]          — coins returned; show reason-specific message
 */
sealed class PowerUpResult {
    /** Coins were never spent — balance was too low. */
    object InsufficientCoins : PowerUpResult()

    /** Effect applied successfully. Coins were deducted and NOT refunded. */
    object Applied : PowerUpResult()

    /**
     * Coins were spent but subsequently refunded because the effect could not
     * produce a meaningful change. The [reason] distinguishes Ice-no-target
     * ([EffectOutcome.NoEffect]) from Shuffle-solvability-failure
     * ([EffectOutcome.RetryExhausted]).
     */
    data class Refunded(val reason: EffectOutcome) : PowerUpResult()
}
