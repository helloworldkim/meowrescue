package com.meowrescue.game.core.powerup

/**
 * Enumerates every power-up available to the player, with its associated coin cost.
 *
 * Implements: design/gdd/economy-system.md — Power-Up Costs
 */
enum class PowerUpKind(val cost: Int) {
    MAGNET(30),
    ICE(40),
    SHUFFLE(50)
}
