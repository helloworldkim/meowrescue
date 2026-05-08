package com.meowrescue.game.core.progression

import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.data.IGameRepository
import com.meowrescue.game.data.UserProgress

/**
 * Core-layer facade for stage progression persistence and achievement evaluation.
 *
 * Wraps [IGameRepository] with stageId validation. Best-ever semantics
 * (max stars, max score) are enforced by the repository tier.
 *
 * [economyManager] is required for achievement coin rewards — all addCoins calls
 * go through EconomyManager, never directly to the repository (ADR-0003).
 *
 * Implements: design/gdd/progression-system.md
 */
class ProgressionManager(
    private val repository: IGameRepository,
    private val economyManager: EconomyManager
) {

    companion object {
        /**
         * Returns true if [stageId] is playable given [maxCompletedLevel].
         *
         * Stage 1 is always unlocked. All other stages require the previous stage
         * to have been cleared at least once (sequential unlock, ADR-0017 / TR-prog-004).
         *
         * This is a pure function — callers read [getMaxCompletedLevel] once and pass
         * the result here to avoid repeated suspend calls per stage-select cell.
         */
        fun isStageUnlocked(stageId: Int, maxCompletedLevel: Int): Boolean =
            stageId == 1 || stageId <= maxCompletedLevel + 1
    }

    /**
     * Records a stage clear. Stars and score are promoted to best-ever (never demoted).
     *
     * @param stageId Stage identifier, must be >= 1.
     * @param stars   Star rating (1–3).
     * @param bestScore Score for this attempt; repository applies max(new, existing).
     * @return true on first-ever clear of [stageId], false on replay.
     */
    suspend fun saveProgress(stageId: Int, stars: Int, bestScore: Int = 0): Boolean {
        require(stageId >= 1) { "stageId must be >= 1 (got $stageId)" }
        require(stars in 1..3) { "stars must be 1, 2, or 3 (got $stars)" }
        return repository.saveProgress(stageId, stars, bestScore)
    }

    /** Returns stored progress for [stageId], or null if never cleared. */
    suspend fun getProgress(stageId: Int): UserProgress? = repository.getProgress(stageId)

    /** Returns the highest stageId cleared, or 0 on a fresh install. */
    suspend fun getMaxCompletedLevel(): Int = repository.getMaxCompletedLevel()

    /** Returns the count of stages cleared with exactly 3 stars. */
    suspend fun getThreeStarCount(): Int = repository.getThreeStarCount()

    /**
     * Returns the cat unlocked by clearing [clearedStage] for the first time, or null.
     *
     * A cat is unlocked only when [clearedStage] > [prevMaxCompletedLevel] (first-ever clear
     * of a stage beyond the previous high-water mark) AND [clearedStage] matches a cat's
     * [CatDefinition.requiredStage]. Replay guard: passing the level read BEFORE
     * [saveProgress] ensures replays of an already-cleared milestone return null.
     */
    fun getNewlyUnlockedCat(clearedStage: Int, prevMaxCompletedLevel: Int): CatDefinition? {
        if (clearedStage <= prevMaxCompletedLevel) return null
        return CatDefinitions.ALL.firstOrNull { it.requiredStage == clearedStage }
    }

    /** Returns all cats whose [CatDefinition.requiredStage] is <= the player's max completed level. */
    suspend fun getUnlockedCats(): List<CatDefinition> {
        val maxLevel = repository.getMaxCompletedLevel()
        return CatDefinitions.ALL.filter { it.requiredStage <= maxLevel }
    }

    /** Returns the number of cats the player has unlocked. */
    suspend fun getUnlockedCatCount(): Int = getUnlockedCats().size

    // ── SharedPrefs delegation (ADR-0001 Data Routing Rules) ─────────────

    /** Returns lifetime Endless mode session count; default 0 on fresh install. */
    fun getEndlessCount(): Int = repository.getEndlessCount()

    /** Persists lifetime Endless mode session count. */
    fun setEndlessCount(count: Int) = repository.setEndlessCount(count)

    /** Returns the all-time best Endless score; default 0 on fresh install. */
    fun getEndlessBest(): Int = repository.getEndlessBest()

    /**
     * Updates the Endless best score if [newScore] is strictly greater than the current best.
     * A tie (equal score) is a no-op — no write is performed.
     */
    fun updateEndlessBest(newScore: Int) {
        if (newScore > getEndlessBest()) repository.setEndlessBest(newScore)
    }

    /** Returns whether the tutorial has been completed; default false on fresh install. */
    fun isTutorialCompleted(): Boolean = repository.isTutorialCompleted()

    /** Persists tutorial completion state. */
    fun setTutorialCompleted(completed: Boolean) = repository.setTutorialCompleted(completed)

    /** Returns the currently selected cat ID; default 1 on fresh install. */
    fun getSelectedCatId(): Int = repository.getSelectedCatId()

    /** Persists the selected cat ID across app restarts. */
    fun setSelectedCatId(id: Int) = repository.setSelectedCatId(id)

    /**
     * Evaluates all [AchievementDefs.ALL] against the supplied [context] and unlocks
     * any newly-met achievements, awarding their coin rewards via [EconomyManager].
     *
     * Snapshot semantics: [AchievementCheckContext.totalCoinsEarned] is overwritten
     * with the current [EconomyManager.totalCoinsEarned] StateFlow value at the moment
     * this method is called — the caller-supplied value is ignored. This guarantees
     * that economy achievements always reflect persisted state, not speculative totals.
     *
     * Idempotent: already-unlocked achievements are skipped via
     * [IGameRepository.isAchievementUnlocked]. A second call with the same context
     * returns an empty list.
     *
     * @param context Snapshot of game state at the time of a stage clear or session end.
     * @return Ordered list of [AchievementDef]s unlocked by this call (may be empty).
     *
     * Implements: design/gdd/progression-system.md — Achievement Check Engine
     */
    suspend fun checkAchievements(context: AchievementCheckContext): List<AchievementDef> {
        val newlyUnlocked = mutableListOf<AchievementDef>()
        val coinsSnapshot = economyManager.totalCoinsEarned.value
        val ctx = context.copy(totalCoinsEarned = coinsSnapshot)
        for (achievement in AchievementDefs.ALL) {
            if (repository.isAchievementUnlocked(achievement.id)) continue
            if (achievement.condition(ctx)) {
                val unlocked = repository.unlockAchievement(achievement.id)
                if (unlocked) {
                    economyManager.addCoins(achievement.coinReward)
                    newlyUnlocked.add(achievement)
                }
            }
        }
        return newlyUnlocked
    }
}
