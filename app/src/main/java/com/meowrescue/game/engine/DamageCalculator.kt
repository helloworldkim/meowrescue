package com.meowrescue.game.engine

import com.meowrescue.game.model.BlockType
import com.meowrescue.game.model.BuffType
import com.meowrescue.game.model.CatBuff
import com.meowrescue.game.model.DamageResult
import com.meowrescue.game.model.Enemy
import com.meowrescue.game.model.EnemyAttackType
import com.meowrescue.game.model.MatchResult
import com.meowrescue.game.model.Relic
import com.meowrescue.game.model.RelicEffect
import com.meowrescue.game.util.GridConstants
import kotlin.math.roundToInt

object DamageCalculator {

    fun calculateDamage(
        matches: List<MatchResult>,
        buffs: List<CatBuff>,
        relics: List<Relic>,
        enemies: List<Enemy>
    ): List<DamageResult> {
        val aliveEnemies = enemies.filter { it.isAlive }
        if (aliveEnemies.isEmpty()) return emptyList()

        val attackBoost = buffs
            .filter { it.buffType == BuffType.ATTACK_BOOST }
            .fold(1f) { acc, buff -> acc * buff.magnitude }

        val fireBoost = buffs
            .filter { it.buffType == BuffType.FIRE_BOOST }
            .fold(1f) { acc, buff -> acc * buff.magnitude }

        val waterBoost = buffs
            .filter { it.buffType == BuffType.WATER_BOOST }
            .fold(1f) { acc, buff -> acc * buff.magnitude }

        val matchBonusDamage = relics
            .filter { it.effect == RelicEffect.MATCH_BONUS_DAMAGE }
            .sumOf { it.magnitude.toDouble() }
            .toFloat()

        val chainMultiplier = relics
            .filter { it.effect == RelicEffect.CHAIN_MULTIPLIER }
            .fold(1f) { acc, relic -> acc * relic.magnitude }

        val damagePerEnemy = mutableMapOf<String, Float>()
        val weaknessHit = mutableMapOf<String, Boolean>()
        val resistedHit = mutableMapOf<String, Boolean>()

        aliveEnemies.forEach { enemy ->
            damagePerEnemy[enemy.id] = 0f
            weaknessHit[enemy.id] = false
            resistedHit[enemy.id] = false
        }

        for (match in matches) {
            if (match.type == BlockType.HEAL) continue

            val baseDamage = match.size * GridConstants.BASE_DAMAGE_PER_BLOCK.toFloat()

            val sizeMultiplier = if (match.size >= GridConstants.SIZE_BONUS_THRESHOLD) {
                1f + GridConstants.SIZE_BONUS_PER_EXTRA * (match.size - 3)
            } else {
                1f
            }

            val chainBonusPercent = match.chainLevel * GridConstants.CHAIN_BONUS_PERCENT * chainMultiplier
            val chainMultiplierValue = 1f + chainBonusPercent / 100f

            var matchDamage = baseDamage * sizeMultiplier * chainMultiplierValue

            matchDamage *= attackBoost

            matchDamage = when (match.type) {
                BlockType.FIRE -> matchDamage * fireBoost
                BlockType.WATER -> matchDamage * waterBoost
                else -> matchDamage
            }

            matchDamage += matchBonusDamage

            for (enemy in aliveEnemies) {
                var enemyDamage = matchDamage

                when {
                    match.type == enemy.weakness -> {
                        enemyDamage *= GridConstants.WEAKNESS_MULTIPLIER
                        weaknessHit[enemy.id] = true
                    }
                    match.type == enemy.resistance -> {
                        enemyDamage *= GridConstants.RESISTANCE_MULTIPLIER
                        resistedHit[enemy.id] = true
                    }
                }

                damagePerEnemy[enemy.id] = (damagePerEnemy[enemy.id] ?: 0f) + enemyDamage
            }
        }

        return aliveEnemies.map { enemy ->
            DamageResult(
                enemyId = enemy.id,
                damage = (damagePerEnemy[enemy.id] ?: 0f).roundToInt(),
                isWeakness = weaknessHit[enemy.id] ?: false,
                isResisted = resistedHit[enemy.id] ?: false,
                healAmount = 0
            )
        }
    }

    fun calculateHeal(
        matches: List<MatchResult>,
        buffs: List<CatBuff>,
        relics: List<Relic>
    ): Int {
        val healMatches = matches.filter { it.type == BlockType.HEAL }
        if (healMatches.isEmpty()) return 0

        val healBoost = buffs
            .filter { it.buffType == BuffType.HEAL_BOOST }
            .fold(1f) { acc, buff -> acc * buff.magnitude }

        val healOnMatchBonus = relics
            .filter { it.effect == RelicEffect.HEAL_ON_MATCH }
            .sumOf { it.magnitude.toDouble() }
            .toFloat()

        var totalHeal = 0f

        for (match in healMatches) {
            val baseHeal = match.size * GridConstants.BASE_DAMAGE_PER_BLOCK.toFloat()
            totalHeal += baseHeal * healBoost + healOnMatchBonus
        }

        return totalHeal.roundToInt()
    }

    fun calculateEnemyDamage(
        enemy: Enemy,
        turnCount: Int,
        buffs: List<CatBuff>
    ): Int {
        if (enemy.attackPattern.isEmpty()) return 0

        val attackType = enemy.attackPattern[turnCount % enemy.attackPattern.size]

        val rawDamage = when (attackType) {
            EnemyAttackType.NORMAL -> enemy.attack.toFloat()
            EnemyAttackType.HEAVY -> enemy.attack * 1.5f
            EnemyAttackType.HEAL_SELF -> 0f
            EnemyAttackType.BUFF_SELF -> 0f
        }

        if (rawDamage == 0f) return 0

        val damageReduce = buffs
            .filter { it.buffType == BuffType.DAMAGE_REDUCE }
            .fold(1f) { acc, buff -> acc * (1f - buff.magnitude) }

        return (rawDamage * damageReduce).roundToInt()
    }
}
