package com.meowrescue.game.engine

import com.meowrescue.game.model.BattleState
import com.meowrescue.game.model.DamageResult
import com.meowrescue.game.model.Enemy
import com.meowrescue.game.model.MatchResult

class BattleEngine(val state: BattleState) {

    interface BattleEventListener {
        fun onPhaseChanged(phase: BattleTurnPhase) {}
        fun onMatchFound(matches: List<MatchResult>) {}
        fun onCascade(round: Int, matches: List<MatchResult>) {}
        fun onDamageDealt(results: List<DamageResult>) {}
        fun onPlayerHealed(amount: Int) {}
        fun onEnemyAttack(enemy: Enemy, effect: EnemyAI.AttackEffect) {}
        fun onEnemyDefeated(enemy: Enemy) {}
        fun onVictory() {}
        fun onDefeat() {}
    }

    var eventListener: BattleEventListener? = null

    private val random = java.util.Random()

    // The position tapped by the player, used in MATCHING phase
    private var tappedRow: Int = -1
    private var tappedCol: Int = -1

    // Initial match found at the tapped position (chainLevel 0)
    private var initialMatch: List<MatchResult> = emptyList()

    // Cascade rounds collected after initial match (chainLevel 1+)
    private var cascadeRounds: List<List<MatchResult>> = emptyList()

    // All match results accumulated for the turn (initial + cascade), used in PLAYER_ATTACK
    private var allTurnMatches: List<MatchResult> = emptyList()

    fun onPlayerSelectBlock(row: Int, col: Int): Boolean {
        if (state.phase != BattleTurnPhase.PLAYER_INPUT) return false

        val validTargets = GridEngine.getValidTapTargets(state.grid)
        if ((row to col) !in validTargets) return false

        tappedRow = row
        tappedCol = col

        transitionTo(BattleTurnPhase.MATCHING)
        return true
    }

    fun advancePhase() {
        when (state.phase) {
            BattleTurnPhase.MATCHING -> handleMatching()
            BattleTurnPhase.CASCADING -> handleCascading()
            BattleTurnPhase.PLAYER_ATTACK -> handlePlayerAttack()
            BattleTurnPhase.ENEMY_ATTACK -> handleEnemyAttack()
            BattleTurnPhase.VICTORY -> eventListener?.onVictory()
            BattleTurnPhase.DEFEAT -> eventListener?.onDefeat()
            BattleTurnPhase.PLAYER_INPUT -> { /* waiting for input, nothing to advance */ }
        }
    }

    private fun handleMatching() {
        // Find the match group containing the tapped block
        val allMatches = GridEngine.findMatches(state.grid)
        val matchAtTap = allMatches.firstOrNull { match ->
            (tappedRow to tappedCol) in match.positions
        }

        if (matchAtTap == null) {
            // No valid match at tap position; return to player input
            transitionTo(BattleTurnPhase.PLAYER_INPUT)
            return
        }

        // Tag as chainLevel 0 and remove from grid
        val taggedMatch = matchAtTap.copy(chainLevel = 0)
        initialMatch = listOf(taggedMatch)

        val gridAfterRemoval = GridEngine.removeMatches(state.grid, initialMatch)
        val gridAfterGravity = GridEngine.applyGravity(gridAfterRemoval)
        val gridAfterRefill = GridEngine.refillEmpty(gridAfterGravity, random)

        // Apply the updated grid back to state
        applyGrid(gridAfterRefill)

        eventListener?.onMatchFound(initialMatch)

        transitionTo(BattleTurnPhase.CASCADING)
    }

    private fun handleCascading() {
        // Replay cascade manually using our shared random so the grid state stays in sync.
        // Each round is tagged starting at chainLevel 1 (initial match was 0).
        val rounds = mutableListOf<List<MatchResult>>()
        var current = state.grid.copy()
        var chainLevel = 1

        while (true) {
            val matches = GridEngine.findMatches(current)
            if (matches.isEmpty()) break

            val taggedMatches = matches.map { it.copy(chainLevel = chainLevel) }
            rounds.add(taggedMatches)

            current = GridEngine.removeMatches(current, taggedMatches)
            current = GridEngine.applyGravity(current)
            current = GridEngine.refillEmpty(current, random)

            chainLevel++
        }

        cascadeRounds = rounds
        applyGrid(current)

        // Fire cascade events
        cascadeRounds.forEachIndexed { index, roundMatches ->
            eventListener?.onCascade(index + 1, roundMatches)
        }

        // Collect all matches for PLAYER_ATTACK phase
        allTurnMatches = initialMatch + cascadeRounds.flatten()

        transitionTo(BattleTurnPhase.PLAYER_ATTACK)
    }

    private fun handlePlayerAttack() {
        val damageResults = DamageCalculator.calculateDamage(
            matches = allTurnMatches,
            buffs = state.catBuffs,
            relics = state.relics,
            enemies = state.enemies
        )

        // Apply damage to enemies
        for (result in damageResults) {
            val enemy = state.enemies.find { it.id == result.enemyId } ?: continue
            enemy.currentHp = maxOf(0, enemy.currentHp - result.damage)
        }

        if (damageResults.isNotEmpty()) {
            eventListener?.onDamageDealt(damageResults)
        }

        // Apply healing to player
        val healAmount = DamageCalculator.calculateHeal(
            matches = allTurnMatches,
            buffs = state.catBuffs,
            relics = state.relics
        )
        if (healAmount > 0) {
            val healed = minOf(healAmount, state.playerMaxHp - state.playerCurrentHp)
            state.playerCurrentHp = minOf(state.playerCurrentHp + healAmount, state.playerMaxHp)
            if (healed > 0) {
                eventListener?.onPlayerHealed(healed)
            }
        }

        // Fire defeated events
        for (result in damageResults) {
            val enemy = state.enemies.find { it.id == result.enemyId } ?: continue
            if (!enemy.isAlive) {
                eventListener?.onEnemyDefeated(enemy)
            }
        }

        if (state.allEnemiesDead) {
            transitionTo(BattleTurnPhase.VICTORY)
        } else {
            transitionTo(BattleTurnPhase.ENEMY_ATTACK)
        }
    }

    private fun handleEnemyAttack() {
        for (enemy in state.enemies) {
            if (!enemy.isAlive) continue

            val effect = EnemyAI.resolveAttack(enemy, state.turnCount)
            eventListener?.onEnemyAttack(enemy, effect)

            when (effect) {
                is EnemyAI.AttackEffect.DamagePlayer -> {
                    val damage = DamageCalculator.calculateEnemyDamage(
                        enemy = enemy,
                        turnCount = state.turnCount,
                        buffs = state.catBuffs
                    )
                    state.playerCurrentHp = maxOf(0, state.playerCurrentHp - damage)
                }
                is EnemyAI.AttackEffect.HealSelf -> {
                    enemy.currentHp = minOf(enemy.currentHp + effect.healAmount, enemy.maxHp)
                }
                is EnemyAI.AttackEffect.BuffSelf -> {
                    // Enemy.attack is val; replace the entry with a copy that has the boosted value.
                    val boost = (enemy.attack * effect.attackBoostPercent / 100f).toInt()
                    val idx = state.enemies.indexOf(enemy)
                    if (idx >= 0) {
                        state.enemies[idx] = enemy.copy(attack = enemy.attack + boost)
                    }
                }
            }
        }

        state.turnCount++

        if (!state.isPlayerAlive) {
            transitionTo(BattleTurnPhase.DEFEAT)
        } else {
            transitionTo(BattleTurnPhase.PLAYER_INPUT)
        }
    }

    private fun transitionTo(phase: BattleTurnPhase) {
        state.phase = phase
        eventListener?.onPhaseChanged(phase)
    }

    private fun applyGrid(newGrid: com.meowrescue.game.model.GridState) {
        for (row in 0 until newGrid.height) {
            for (col in 0 until newGrid.width) {
                state.grid.set(row, col, newGrid.get(row, col))
            }
        }
    }
}
