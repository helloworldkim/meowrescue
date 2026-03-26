package com.meowrescue.game.generator

import com.meowrescue.game.engine.DamageCalculator
import com.meowrescue.game.engine.GridEngine
import com.meowrescue.game.model.Enemy
import com.meowrescue.game.model.EnemyAttackType
import com.meowrescue.game.model.GridState

object SolvabilityVerifier {

    fun isSolvable(
        grid: GridState,
        enemies: List<Enemy>,
        playerHp: Int,
        maxTurns: Int = 30
    ): Boolean {
        val random = java.util.Random(42)

        var simGrid = grid.copy()
        var simEnemies = enemies.map { it.copy() }.toMutableList()
        var simPlayerHp = playerHp

        for (turn in 0 until maxTurns) {
            // Step a: find valid tap targets
            var targets = GridEngine.getValidTapTargets(simGrid)

            // Step b: if no valid targets, refill and retry
            if (targets.isEmpty()) {
                simGrid = GridEngine.applyGravity(simGrid)
                simGrid = GridEngine.refillEmpty(simGrid, random)
                targets = GridEngine.getValidTapTargets(simGrid)
                if (targets.isEmpty()) return false
            }

            // Step c: evaluate each candidate tap target
            var bestTarget: Pair<Int, Int>? = null
            var bestScore = -1f

            for (target in targets) {
                val candidate = simulateTap(simGrid, simEnemies, target, random)
                if (candidate > bestScore) {
                    bestScore = candidate
                    bestTarget = target
                }
            }

            if (bestTarget == null) return false

            // Step d/e: apply the chosen move
            val (newGrid, totalDamageResults) = applyTap(simGrid, simEnemies, bestTarget, random)
            simGrid = newGrid

            // Deal damage to enemies
            for (result in totalDamageResults) {
                val idx = simEnemies.indexOfFirst { it.id == result.enemyId }
                if (idx >= 0) {
                    val enemy = simEnemies[idx]
                    simEnemies[idx] = enemy.copy(currentHp = enemy.currentHp - result.damage)
                }
            }

            // Step f: check if all enemies are dead
            if (simEnemies.all { it.currentHp <= 0 }) return true

            // Enemy counter-attacks
            for (i in simEnemies.indices) {
                val enemy = simEnemies[i]
                if (enemy.currentHp <= 0) continue

                if (enemy.attackPattern.isEmpty()) continue
                val attackType = enemy.attackPattern[turn % enemy.attackPattern.size]

                when (attackType) {
                    EnemyAttackType.HEAL_SELF -> {
                        val healAmount = (enemy.maxHp * 0.2f).toInt()
                        val newHp = minOf(enemy.currentHp + healAmount, enemy.maxHp)
                        simEnemies[i] = enemy.copy(currentHp = newHp)
                    }
                    else -> {
                        val dmg = DamageCalculator.calculateEnemyDamage(enemy, turn, emptyList())
                        simPlayerHp -= dmg
                    }
                }
            }

            // Step g: check if player is dead
            if (simPlayerHp <= 0) return false
        }

        // maxTurns exceeded
        return false
    }

    /**
     * Simulates tapping [target] on a copy of the grid and returns total damage score.
     */
    private fun simulateTap(
        grid: GridState,
        enemies: List<Enemy>,
        target: Pair<Int, Int>,
        random: java.util.Random
    ): Float {
        val simRandom = java.util.Random(42)

        // Find the match group containing the target position
        val matches = GridEngine.findMatches(grid)
        val tapMatch = matches.find { target in it.positions } ?: return 0f

        // Remove the tapped match, apply gravity, refill
        var tempGrid = GridEngine.removeMatches(grid, listOf(tapMatch))
        tempGrid = GridEngine.applyGravity(tempGrid)
        tempGrid = GridEngine.refillEmpty(tempGrid, simRandom)

        // Process full cascade to collect all chain matches
        val cascadeRounds = GridEngine.processFullCascade(tempGrid, simRandom)

        // Flatten all matches: first match (chainLevel 0) + cascade results
        val firstMatch = tapMatch.copy(chainLevel = 0)
        val allMatches = mutableListOf(firstMatch)
        for (round in cascadeRounds) {
            allMatches.addAll(round)
        }

        // Calculate damage using empty buffs/relics
        val damageResults = DamageCalculator.calculateDamage(
            matches = allMatches,
            buffs = emptyList(),
            relics = emptyList(),
            enemies = enemies
        )

        return damageResults.sumOf { it.damage.toDouble() }.toFloat()
    }

    /**
     * Applies a tap at [target], returns the resulting grid and damage results.
     */
    private fun applyTap(
        grid: GridState,
        enemies: List<Enemy>,
        target: Pair<Int, Int>,
        random: java.util.Random
    ): Pair<GridState, List<com.meowrescue.game.model.DamageResult>> {
        val matches = GridEngine.findMatches(grid)
        val tapMatch = matches.find { target in it.positions }
            ?: return grid.copy() to emptyList()

        var newGrid = GridEngine.removeMatches(grid, listOf(tapMatch))
        newGrid = GridEngine.applyGravity(newGrid)
        newGrid = GridEngine.refillEmpty(newGrid, random)

        val cascadeRounds = GridEngine.processFullCascade(newGrid, random)

        val firstMatch = tapMatch.copy(chainLevel = 0)
        val allMatches = mutableListOf(firstMatch)
        for (round in cascadeRounds) {
            allMatches.addAll(round)
        }

        val damageResults = DamageCalculator.calculateDamage(
            matches = allMatches,
            buffs = emptyList(),
            relics = emptyList(),
            enemies = enemies
        )

        return newGrid to damageResults
    }
}
