package com.meowrescue.game.model

data class CatBuff(
    val catId: String,
    val buffType: BuffType,
    val magnitude: Float
)

enum class BuffType {
    ATTACK_BOOST,
    HEAL_BOOST,
    FIRE_BOOST,
    WATER_BOOST,
    MAX_HP_UP,
    DAMAGE_REDUCE
}
