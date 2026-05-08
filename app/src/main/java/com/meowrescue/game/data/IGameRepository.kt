package com.meowrescue.game.data

/**
 * Testability contract for Foundation persistence.
 *
 * Core modules depend on this interface rather than the concrete [GameRepository],
 * enabling in-process test doubles ([FakeGameRepository]) that run on plain JVM
 * without Android framework dependencies.
 *
 * Exclusions (not part of this interface):
 * - [GameRepository.getUnlockedCats] / [GameRepository.getNewlyUnlockedCat] — return
 *   [GameRepository.CatDefinition] which references Android drawable resource IDs.
 * - [GameRepository.seedAchievements] — Room setup concern, not a runtime contract.
 * - [GameRepository.getAllLaunchProgress] / [GameRepository.getUnlockedAchievements] —
 *   return types carry Room annotations; excluded to keep the interface JVM-portable.
 */
interface IGameRepository {

    // ── Coin Economy ──────────────────────────────────────────────────────

    /** Adds [amount] to both [getCoins] and [getTotalCoinsEarned]. Requires amount > 0. */
    suspend fun addCoins(amount: Int)

    /**
     * Decrements [getCoins] by [amount] if sufficient balance exists.
     * Does NOT affect [getTotalCoinsEarned].
     * @return true if coins were spent, false if balance was insufficient.
     */
    suspend fun spendCoins(amount: Int): Boolean

    /**
     * Refunds [amount] to [getCoins] without affecting [getTotalCoinsEarned].
     * Use for cancelled purchases / rollbacks. Requires amount > 0.
     */
    suspend fun refundCoins(amount: Int)

    /** Returns the current spendable coin balance. */
    suspend fun getCoins(): Int

    /** Returns the lifetime total of coins ever earned (never decremented by spending). */
    suspend fun getTotalCoinsEarned(): Int

    // ── Stage Progress ────────────────────────────────────────────────────

    /**
     * Records a stage clear. Promotes best stars and best score (never demotes).
     * Requires [stars] in 1..3.
     * @return true on first clear (was null or not yet completed), false on repeat.
     */
    suspend fun saveProgress(stageId: Int, stars: Int, score: Int = 0): Boolean

    /** Returns stored progress for [levelId], or null if never cleared. */
    suspend fun getProgress(levelId: Int): UserProgress?

    /** Returns the highest stageId that has been cleared, or 0 if none. */
    suspend fun getMaxCompletedLevel(): Int

    /** Returns the number of stages cleared with exactly 3 stars. */
    suspend fun getThreeStarCount(): Int

    /** Returns the best score for [levelId], or 0 if never cleared. */
    suspend fun getBestScore(levelId: Int): Int

    // ── Launch Mode Progress ──────────────────────────────────────────────

    /**
     * Records a Cat Launch stage clear. Promotes best stars and best score.
     * Requires [stars] in 1..3.
     */
    /**
     * Records a Cat Launch stage clear. Promotes best stars and best score.
     * Requires [stars] in 1..3.
     * @return true on first clear (stage was never cleared before), false on repeat.
     */
    suspend fun saveLaunchProgress(stageId: Int, stars: Int, coinMultiplier: Float = 1.0f, score: Int = 0): Boolean

    /** Returns stored Cat Launch progress for [stageId], or null if never cleared. */
    suspend fun getLaunchProgress(stageId: Int): LaunchProgress?

    /** Returns the count of Cat Launch stages that have been cleared at least once. */
    suspend fun getCompletedLaunchCount(): Int

    /** Returns the highest Cat Launch stageId that has been cleared, or 0 if none. */
    suspend fun getMaxCompletedLaunchStage(): Int

    // ── Achievements ──────────────────────────────────────────────────────

    /**
     * Marks achievement [id] as unlocked.
     * @return true on first unlock, false if already unlocked (idempotent).
     */
    suspend fun unlockAchievement(id: String): Boolean

    /** Returns true if achievement [id] has been unlocked. */
    suspend fun isAchievementUnlocked(id: String): Boolean

    /** Returns the count of unlocked achievements. */
    suspend fun getUnlockedAchievementCount(): Int

    // ── SharedPrefs ───────────────────────────────────────────────────────

    /** Returns the currently selected cat ID (default: 1). */
    fun getSelectedCatId(): Int

    /** Persists the selected cat ID. */
    fun setSelectedCatId(catId: Int)

    /** Returns the Endless mode play count. */
    fun getEndlessCount(): Int

    /** Persists the Endless mode play count. */
    fun setEndlessCount(count: Int)

    /** Returns the Endless mode best score. */
    fun getEndlessBest(): Int

    /** Persists the Endless mode best score. */
    fun setEndlessBest(best: Int)

    /**
     * Returns Endless coins earned today (resets at midnight local time).
     * Sync — reads SharedPreferences directly.
     */
    fun getDailyEndlessCoins(): Int

    /**
     * Adds Endless coins up to the daily cap ([GameRepository.ENDLESS_DAILY_COIN_CAP]).
     * Also calls [addCoins] with the actual amount added.
     * @return actual amount added (may be less than [amount] if near the cap).
     */
    suspend fun addEndlessCoins(amount: Int): Int

    /** Returns true if the tutorial has been completed. */
    fun isTutorialCompleted(): Boolean

    /** Marks the tutorial as completed (or explicitly uncompleted if [value] = false). */
    fun setTutorialCompleted(value: Boolean = true)

    /** Returns the number of times any power-up has been used. */
    fun getPowerUpUseCount(): Int

    /** Increments the power-up use count by 1. */
    fun incrementPowerUpUseCount()

    /** Returns true if game sound is enabled. */
    fun isSoundEnabled(): Boolean

    /** Persists the sound-enabled preference. */
    fun setSoundEnabled(enabled: Boolean)

    // ── Star Ratings Batch ────────────────────────────────────────────────

    /**
     * Returns an IntArray of size 211 where index [i] holds the best star count (0–3)
     * for stageId [i]. Index 0 is unused. 0 means never cleared.
     * Required by the stage-select screen to render all star badges in one pass (OQ-SS5).
     */
    suspend fun getAllStarRatings(): IntArray

    // ── Cat Launch Best Scores per Difficulty ─────────────────────────────

    /**
     * Returns the best total score ever achieved at [difficulty], or 0 if never played.
     * Sync — reads SharedPreferences directly.
     */
    fun getLaunchBestScore(difficulty: Difficulty): Int

    /**
     * Updates the best score for [difficulty] if [score] exceeds the stored value.
     * No-op if [score] ≤ current best. Sync — writes SharedPreferences directly.
     */
    fun updateLaunchBestScore(difficulty: Difficulty, score: Int)

    // ── World Theme ───────────────────────────────────────────────────────

    /**
     * Returns the player's cosmetic theme override ID, or null if none selected.
     * Sync — reads SharedPreferences directly (ADR-0009).
     */
    fun getSelectedThemeId(): String?

    /**
     * Persists the cosmetic theme override ID. Pass null to clear the override
     * (player returns to progression-based theming).
     */
    fun setSelectedThemeId(value: String?)

    // ── Ad Counter ────────────────────────────────────────────────────────

    /** Returns the number of stage clears since the last interstitial ad was shown. */
    fun getStagesSinceLastAd(): Int

    /** Persists the stages-since-last-ad [count]. */
    fun setStagesSinceLastAd(count: Int)
}
