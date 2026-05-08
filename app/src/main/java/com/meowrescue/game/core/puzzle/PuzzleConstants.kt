package com.meowrescue.game.core.puzzle

/**
 * Shared numeric constants for the puzzle generation and solving pipeline.
 * All gameplay-tunable values live here so designers can adjust without
 * touching logic code.
 *
 * Implements: design/gdd/puzzle-system.md — Tuning Knobs section
 */
object PuzzleConstants {
    const val SEED_MULTIPLIER = 7919L
    const val SEED_CONSTANT = 42L
    const val MAX_ACCEPTED_DEPTH = 45
    const val MAX_OUTER_ATTEMPTS = 80
    const val MAX_INNER_ATTEMPTS = 60
    const val GENERATION_DEADLINE_NORMAL_MS = 3000L
    const val GENERATION_DEADLINE_ADVANCED_MS = 5000L
    const val MAX_STATES_DEFAULT = 150_000
    const val MAX_STATES_CHECKPOINT = 250_000
    const val MAX_STATES_MULTI_CAT = 300_000
    const val ACCEPT_THRESHOLD_RATIO = 0.50
}
