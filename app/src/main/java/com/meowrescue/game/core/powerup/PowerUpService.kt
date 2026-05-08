package com.meowrescue.game.core.powerup

import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.data.IGameRepository
import com.meowrescue.game.puzzle.engine.PuzzleGrid

/**
 * Orchestrates the spend-execute-refund protocol for all power-ups.
 *
 * Protocol (per [apply] call):
 * 1. Attempt to spend [PowerUpKind.cost] via [EconomyManager.spendCoins].
 *    On failure → return [PowerUpResult.InsufficientCoins] immediately (no side effects).
 * 2. Take a grid snapshot (for potential rollback).
 * 3. Delegate to the registered [PowerUpEffect] for this [PowerUpKind].
 * 4. Evaluate [EffectOutcome]:
 *    - [EffectOutcome.Applied]         → keep coins, no grid restore.
 *    - [EffectOutcome.NoEffect]        → refund coins, grid already unchanged.
 *    - [EffectOutcome.RetryExhausted]  → refund coins, restore grid from snapshot.
 * 5. Regardless of outcome (except [PowerUpResult.InsufficientCoins]):
 *    increment the power-up use counter THEN fire the achievement checker.
 *
 * Implements: design/gdd/puzzle-system.md — Power-Up / Spend-Then-Verify Protocol
 *
 * @param repository    Persistence layer — used only for [IGameRepository.incrementPowerUpUseCount].
 * @param economy       Coin gate — all spend/refund operations go through here.
 * @param achievements  Called after the use counter is incremented (AC-8 ordering guarantee).
 * @param effects       Strategy table mapping each [PowerUpKind] to its [PowerUpEffect].
 */
class PowerUpService(
    private val repository: IGameRepository,
    private val economy: EconomyManager,
    private val achievements: AchievementChecker,
    private val effects: Map<PowerUpKind, PowerUpEffect>
) {

    /**
     * Executes the full spend-execute-refund cycle for [kind] against [grid].
     *
     * This function is safe to call on [kotlinx.coroutines.Dispatchers.Main] — all
     * suspend work delegates to [EconomyManager] which manages its own dispatcher.
     *
     * @param kind The power-up to apply.
     * @param grid The live puzzle grid. May be mutated in-place on [PowerUpResult.Applied].
     *             Restored to pre-call state on [PowerUpResult.Refunded] with
     *             reason [EffectOutcome.RetryExhausted].
     * @return [PowerUpResult] describing what happened and what the caller should display.
     */
    suspend fun apply(kind: PowerUpKind, grid: PuzzleGrid): PowerUpResult {
        val cost = kind.cost

        // Step 1: gate on coins — early return, no side effects
        if (!economy.spendCoins(cost)) return PowerUpResult.InsufficientCoins

        // Step 2: snapshot before effect mutates grid
        val snapshot = grid.snapshot()

        // Step 3: execute effect
        val outcome = effects.getValue(kind).execute(grid)

        // Step 4: build result, apply refund / rollback as needed
        val result = when (outcome) {
            EffectOutcome.Applied -> PowerUpResult.Applied

            EffectOutcome.NoEffect -> {
                economy.refundCoins(cost)
                PowerUpResult.Refunded(outcome)
            }

            EffectOutcome.RetryExhausted -> {
                grid.restoreFrom(snapshot)
                economy.refundCoins(cost)
                PowerUpResult.Refunded(outcome)
            }
        }

        // Step 5: counter + achievement (runs on ALL non-InsufficientCoins paths)
        repository.incrementPowerUpUseCount()
        achievements.onPowerUpUsed()

        return result
    }
}
