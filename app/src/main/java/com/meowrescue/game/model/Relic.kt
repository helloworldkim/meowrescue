package com.meowrescue.game.model

data class Relic(
    val id: String,
    val name: String,
    val description: String,
    val effect: RelicEffect,
    val magnitude: Float
)

enum class RelicEffect {
    MATCH_BONUS_DAMAGE,
    CHAIN_MULTIPLIER,
    HEAL_ON_MATCH,
    START_SHIELD,
    EXTRA_TURN_CHANCE
}
