package com.meowrescue.game.model

import com.meowrescue.game.engine.BattleTurnPhase

data class BattleState(
    val grid: GridState,
    val enemies: MutableList<Enemy>,
    val playerMaxHp: Int,
    var playerCurrentHp: Int,
    var turnCount: Int,
    val catBuffs: MutableList<CatBuff>,
    val relics: MutableList<Relic>,
    var phase: BattleTurnPhase,
    val chapter: Int,
    val stage: Int,
    var shufflesUsed: Int = 0
) {
    val isPlayerAlive: Boolean get() = playerCurrentHp > 0
    val allEnemiesDead: Boolean get() = enemies.none { it.isAlive }
    val canShuffle: Boolean get() = shufflesUsed < 1
}
