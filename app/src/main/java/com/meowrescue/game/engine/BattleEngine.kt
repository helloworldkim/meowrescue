package com.meowrescue.game.engine

import com.meowrescue.game.model.BattleState
import com.meowrescue.game.model.DamageResult
import com.meowrescue.game.model.Enemy
import com.meowrescue.game.model.MatchResult

class BattleEngine(val state: BattleState) {

    interface BattleEventListener {
        fun onPhaseChanged(phase: BattleTurnPhase) {}
        fun onBlockSelected(row: Int, col: Int) {}
        fun onSwapStarted(r1: Int, c1: Int, r2: Int, c2: Int) {}
        fun onSwapFailed(r1: Int, c1: Int, r2: Int, c2: Int) {}
        fun onMatchFound(matches: List<MatchResult>) {}
        fun onCascade(round: Int, matches: List<MatchResult>) {}
        fun onDamageDealt(results: List<DamageResult>) {}
        fun onPlayerHealed(amount: Int) {}
        fun onEnemyAttack(enemy: Enemy, effect: EnemyAI.AttackEffect) {}
        fun onEnemyDefeated(enemy: Enemy) {}
        fun onNoValidMoves(canShuffle: Boolean) {}
        fun onShuffle() {}
        fun onVictory() {}
        fun onDefeat() {}
    }

    var eventListener: BattleEventListener? = null

    private val random = java.util.Random()
    private val lock = Any()

    // Selection state for swap
    var selectedRow: Int = -1
        private set
    var selectedCol: Int = -1
        private set

    // Swap coordinates for swap-back animation
    private var swapR1 = -1
    private var swapC1 = -1
    private var swapR2 = -1
    private var swapC2 = -1

    // Match results accumulated for the turn
    private var initialMatch: List<MatchResult> = emptyList()
    private var cascadeRounds: List<List<MatchResult>> = emptyList()
    private var allTurnMatches: List<MatchResult> = emptyList()

    fun onPlayerSelectBlock(row: Int, col: Int): Boolean {
        synchronized(lock) {
            if (state.phase != BattleTurnPhase.PLAYER_INPUT) return false
            val block = state.grid.get(row, col) ?: return false

            if (selectedRow < 0) {
                selectedRow = row
                selectedCol = col
                eventListener?.onBlockSelected(row, col)
                return true
            }

            if (selectedRow == row && selectedCol == col) {
                clearSelection()
                return true
            }

            val dr = Math.abs(selectedRow - row)
            val dc = Math.abs(selectedCol - col)
            if (dr + dc != 1) {
                selectedRow = row
                selectedCol = col
                eventListener?.onBlockSelected(row, col)
                return true
            }

            swapR1 = selectedRow
            swapC1 = selectedCol
            swapR2 = row
            swapC2 = col
            clearSelection()

            GridEngine.swapBlocks(state.grid, swapR1, swapC1, swapR2, swapC2)
            eventListener?.onSwapStarted(swapR1, swapC1, swapR2, swapC2)

            val matches = GridEngine.findMatches(state.grid)
            if (matches.isEmpty()) {
                transitionTo(BattleTurnPhase.SWAP_BACK)
            } else {
                transitionTo(BattleTurnPhase.MATCHING)
            }
            return true
        }
    }

    fun clearSelection() {
        selectedRow = -1
        selectedCol = -1
    }

    fun requestShuffle(): Boolean {
        if (!state.canShuffle) return false
        state.shufflesUsed++
        GridEngine.shuffle(state.grid, random)
        eventListener?.onShuffle()
        transitionTo(BattleTurnPhase.PLAYER_INPUT)
        return true
    }

    fun declineShuffle() {
        transitionTo(BattleTurnPhase.DEFEAT)
    }

    fun advancePhase() {
        synchronized(lock) {
            when (state.phase) {
                BattleTurnPhase.SWAP_BACK -> handleSwapBack()
                BattleTurnPhase.MATCHING -> handleMatching()
                BattleTurnPhase.CASCADING -> handleCascading()
                BattleTurnPhase.PLAYER_ATTACK -> handlePlayerAttack()
                BattleTurnPhase.ENEMY_ATTACK -> handleEnemyAttack()
                BattleTurnPhase.NO_MOVES -> { /* waiting for user choice */ }
                BattleTurnPhase.VICTORY -> eventListener?.onVictory()
                BattleTurnPhase.DEFEAT -> eventListener?.onDefeat()
                BattleTurnPhase.PLAYER_INPUT -> { /* waiting for input */ }
            }
        }
    }

    private fun handleSwapBack() {
        GridEngine.swapBlocks(state.grid, swapR1, swapC1, swapR2, swapC2)
        eventListener?.onSwapFailed(swapR1, swapC1, swapR2, swapC2)
        transitionTo(BattleTurnPhase.PLAYER_INPUT)
    }

    private fun handleMatching() {
        val allMatches = GridEngine.findMatches(state.grid)
        if (allMatches.isEmpty()) {
            transitionTo(BattleTurnPhase.PLAYER_INPUT)
            return
        }

        val taggedMatches = allMatches.map { it.copy(chainLevel = 0) }
        initialMatch = taggedMatches

        val gridAfterRemoval = GridEngine.removeMatches(state.grid, initialMatch)
        val gridAfterGravity = GridEngine.applyGravity(gridAfterRemoval)
        val gridAfterRefill = GridEngine.refillEmpty(gridAfterGravity, random)
        applyGrid(gridAfterRefill)

        eventListener?.onMatchFound(initialMatch)
        transitionTo(BattleTurnPhase.CASCADING)
    }

    private fun handleCascading() {
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

        cascadeRounds.forEachIndexed { index, roundMatches ->
            eventListener?.onCascade(index + 1, roundMatches)
        }

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

        for (result in damageResults) {
            val enemy = state.enemies.find { it.id == result.enemyId } ?: continue
            enemy.currentHp = maxOf(0, enemy.currentHp - result.damage)
        }

        if (damageResults.isNotEmpty()) {
            eventListener?.onDamageDealt(damageResults)
        }

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
            checkForDeadlock()
        }
    }

    private fun checkForDeadlock() {
        if (GridEngine.hasValidSwaps(state.grid)) {
            transitionTo(BattleTurnPhase.PLAYER_INPUT)
        } else {
            transitionTo(BattleTurnPhase.NO_MOVES)
            eventListener?.onNoValidMoves(state.canShuffle)
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
