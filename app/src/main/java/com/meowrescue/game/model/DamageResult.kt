package com.meowrescue.game.model

data class DamageResult(
    val enemyId: String,
    val damage: Int,
    val isWeakness: Boolean,
    val isResisted: Boolean,
    val healAmount: Int = 0
)
