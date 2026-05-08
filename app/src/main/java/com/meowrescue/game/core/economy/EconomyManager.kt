package com.meowrescue.game.core.economy

import com.meowrescue.game.data.IGameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the player's coin economy, exposing observable balances via [StateFlow].
 *
 * All mutation operations delegate atomically to [IGameRepository] — EconomyManager
 * does not implement coin logic itself. StateFlows are refreshed by reading back
 * from the repository after each successful write.
 *
 * Call [init] once before any UI observes [coins] or [totalCoinsEarned].
 *
 * @param repository Persistence delegate (ADR-0003).
 */
class EconomyManager(private val repository: IGameRepository) {

    companion object {
        const val PUZZLE_COINS_1_STAR = 10
        const val PUZZLE_COINS_2_STAR = 20
        const val PUZZLE_COINS_3_STAR = 30
        const val FIRST_CLEAR_BONUS = 50
        const val LAUNCH_FIRST_CLEAR_CAP = 50
    }

    private val _coins = MutableStateFlow(0)
    /** Current spendable coin balance. Single sanctioned HUD observation path. */
    val coins: StateFlow<Int> = _coins.asStateFlow()

    private val _totalCoinsEarned = MutableStateFlow(0)
    /** Lifetime coins ever earned. Monotonically non-decreasing. */
    val totalCoinsEarned: StateFlow<Int> = _totalCoinsEarned.asStateFlow()

    /**
     * Seeds both StateFlows from the repository.
     * Must be called once before UI observes [coins] or [totalCoinsEarned].
     */
    suspend fun init() {
        _coins.value = repository.getCoins()
        _totalCoinsEarned.value = repository.getTotalCoinsEarned()
    }

    /**
     * Adds [amount] coins. Updates both [coins] and [totalCoinsEarned].
     * @throws IllegalArgumentException if amount <= 0.
     */
    suspend fun addCoins(amount: Int) {
        require(amount > 0) { "addCoins amount must be positive (got $amount)" }
        repository.addCoins(amount)
        _coins.value = repository.getCoins()
        _totalCoinsEarned.value = repository.getTotalCoinsEarned()
    }

    /**
     * Spends [amount] coins if balance is sufficient.
     * Returns false without modifying balance if funds are insufficient.
     * [totalCoinsEarned] is never affected.
     * @throws IllegalArgumentException if amount <= 0.
     */
    suspend fun spendCoins(amount: Int): Boolean {
        require(amount > 0) { "spendCoins amount must be positive (got $amount)" }
        val success = repository.spendCoins(amount)
        if (success) _coins.value = repository.getCoins()
        return success
    }

    /**
     * Awards coins for clearing a puzzle stage.
     *
     * Star mapping: 1★→10, 2★→20, 3★→30. First-clear adds 50 bonus coins.
     * Stars=0 returns 0 without calling addCoins.
     *
     * @param stars 0..3 (0 = no clear, treated as no award)
     * @param stageId Identifier for audit purposes (not used in calculation)
     * @param isFirstClear true if the stage was never cleared before
     * @return Total coins awarded (0 if stars == 0)
     * @throws IllegalArgumentException if stars not in 0..3
     */
    suspend fun awardPuzzleClearCoins(stars: Int, stageId: String, isFirstClear: Boolean): Int {
        require(stars in 0..3) { "stars must be in 0..3 (got $stars)" }
        if (stars == 0) return 0
        val base = when (stars) {
            1 -> PUZZLE_COINS_1_STAR
            2 -> PUZZLE_COINS_2_STAR
            3 -> PUZZLE_COINS_3_STAR
            else -> 0
        }
        val total = base + if (isFirstClear) FIRST_CLEAR_BONUS else 0
        addCoins(total)
        return total
    }

    /**
     * Awards coins for clearing a Cat Launch stage.
     *
     * Formula: `(starCoins(stars) * coinMultiplier).toInt()` + first-clear bonus.
     * First-clear bonus (50) applies when [isFirstClear] and [completedLaunchCount] < [LAUNCH_FIRST_CLEAR_CAP].
     * Stars=0 returns 0 without calling addCoins.
     *
     * @param stars 0..3
     * @param coinMultiplier Easy=1.0f, Normal=1.5f, Hard=2.0f
     * @param isFirstClear true if the stage was never cleared before this call
     * @param completedLaunchCount total unique Launch stages cleared (including this one)
     * @return Total coins awarded
     */
    suspend fun awardLaunchCoins(
        stars: Int,
        coinMultiplier: Float,
        isFirstClear: Boolean,
        completedLaunchCount: Int
    ): Int {
        require(stars in 0..3) { "stars must be in 0..3 (got $stars)" }
        if (stars == 0) return 0
        val base = when (stars) { 3 -> 30; 2 -> 20; else -> 10 }
        val scaled = (base * coinMultiplier).toInt()
        val bonus = if (isFirstClear && completedLaunchCount < LAUNCH_FIRST_CLEAR_CAP) FIRST_CLEAR_BONUS else 0
        val total = scaled + bonus
        if (total > 0) addCoins(total)
        return total
    }

    /**
     * Awards Endless mode coins subject to the daily cap (150 coins/day).
     *
     * Delegates to [IGameRepository.addEndlessCoins] for cap enforcement and date reset.
     * Refreshes [coins] and [totalCoinsEarned] StateFlows after any successful award.
     *
     * @param amount Coins to award (must be > 0)
     * @return Actual coins awarded (may be less than [amount] or 0 when cap is exhausted)
     * @throws IllegalArgumentException if amount <= 0.
     */
    suspend fun awardEndlessCoins(amount: Int): Int {
        require(amount > 0) { "awardEndlessCoins amount must be positive (got $amount)" }
        val actual = repository.addEndlessCoins(amount)
        if (actual > 0) {
            _coins.value = repository.getCoins()
            _totalCoinsEarned.value = repository.getTotalCoinsEarned()
        }
        return actual
    }

    /**
     * Refunds [amount] coins (e.g. cancelled purchase / rollback).
     * Only [coins] is updated — [totalCoinsEarned] is intentionally unchanged.
     * @throws IllegalArgumentException if amount <= 0.
     */
    suspend fun refundCoins(amount: Int) {
        require(amount > 0) { "refundCoins amount must be positive (got $amount)" }
        repository.refundCoins(amount)
        _coins.value = repository.getCoins()
        // _totalCoinsEarned intentionally NOT updated (refund invariant)
    }
}
