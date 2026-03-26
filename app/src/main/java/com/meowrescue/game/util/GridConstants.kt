package com.meowrescue.game.util

object GridConstants {
    const val DEFAULT_GRID_WIDTH = 5
    const val DEFAULT_GRID_HEIGHT = 5
    const val MIN_MATCH_SIZE = 3
    const val BLOCK_SIZE_DP = 64
    const val BLOCK_GAP_DP = 4
    const val GRID_PADDING_DP = 16

    // Animation timings (milliseconds)
    const val MATCH_ANIM_MS = 300L
    const val CASCADE_ANIM_MS = 200L
    const val REFILL_ANIM_MS = 250L
    const val ATTACK_ANIM_MS = 400L
    const val DAMAGE_NUMBER_MS = 800L

    // Damage constants
    const val BASE_DAMAGE_PER_BLOCK = 5
    const val CHAIN_BONUS_PERCENT = 25
    const val WEAKNESS_MULTIPLIER = 1.5f
    const val RESISTANCE_MULTIPLIER = 0.5f
    const val SIZE_BONUS_THRESHOLD = 4
    const val SIZE_BONUS_PER_EXTRA = 0.15f

    // Design resolution (inherited from old GameView)
    const val DESIGN_WIDTH = 1080f
    const val DESIGN_HEIGHT = 1920f
}
