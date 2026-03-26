package com.meowrescue.game.model

data class Enemy(
    val id: String,
    val name: String,
    val maxHp: Int,
    var currentHp: Int,
    val attack: Int,
    val weakness: BlockType?,
    val resistance: BlockType?,
    val spriteResId: Int,
    val attackPattern: List<EnemyAttackType>
) {
    val isAlive: Boolean get() = currentHp > 0
}

enum class EnemyAttackType {
    NORMAL,
    HEAVY,
    HEAL_SELF,
    BUFF_SELF
}
