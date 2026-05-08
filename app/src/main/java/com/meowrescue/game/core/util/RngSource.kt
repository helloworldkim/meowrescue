package com.meowrescue.game.core.util

/**
 * Minimal randomness abstraction used by power-up effects.
 *
 * Keeps [IceEffect] and [ShuffleEffect] deterministically testable:
 * production code passes `{ bound -> Random.nextInt(bound) }`;
 * tests pass a controlled sequence.
 */
fun interface RngSource {
    fun nextInt(bound: Int): Int
}
