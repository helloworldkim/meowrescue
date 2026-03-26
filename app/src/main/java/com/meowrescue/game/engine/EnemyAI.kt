package com.meowrescue.game.engine

import com.meowrescue.game.model.Enemy
import com.meowrescue.game.model.EnemyAttackType

object EnemyAI {

    /**
     * Select the attack type for an enemy based on its attack pattern and the current turn.
     * Cycles through the pattern list.
     */
    fun selectAttack(enemy: Enemy, turnCount: Int): EnemyAttackType {
        if (enemy.attackPattern.isEmpty()) return EnemyAttackType.NORMAL
        return enemy.attackPattern[turnCount % enemy.attackPattern.size]
    }

    /**
     * Execute an enemy's attack action and return the effect.
     * Returns a sealed result describing what happened.
     */
    fun resolveAttack(enemy: Enemy, turnCount: Int): AttackEffect {
        val attackType = selectAttack(enemy, turnCount)
        return when (attackType) {
            EnemyAttackType.NORMAL -> AttackEffect.DamagePlayer(enemy.attack)
            EnemyAttackType.HEAVY -> AttackEffect.DamagePlayer((enemy.attack * 1.5f).toInt())
            EnemyAttackType.HEAL_SELF -> AttackEffect.HealSelf((enemy.maxHp * 0.2f).toInt())
            EnemyAttackType.BUFF_SELF -> AttackEffect.BuffSelf(attackBoostPercent = 25)
        }
    }

    sealed class AttackEffect {
        data class DamagePlayer(val damage: Int) : AttackEffect()
        data class HealSelf(val healAmount: Int) : AttackEffect()
        data class BuffSelf(val attackBoostPercent: Int) : AttackEffect()
    }
}
