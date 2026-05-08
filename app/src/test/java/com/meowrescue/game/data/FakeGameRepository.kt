package com.meowrescue.game.data

/**
 * In-memory test double for [IGameRepository].
 *
 * Implements every method of [IGameRepository] with plain Kotlin data structures.
 * No Android framework dependencies — runs on plain JVM without Robolectric.
 *
 * Coin semantics match the real [GameRepository] contract exactly:
 * - [addCoins]: increments both [getCoins] and [getTotalCoinsEarned]
 * - [spendCoins]: decrements [getCoins] only; [getTotalCoinsEarned] is unchanged
 * - [refundCoins]: increments [getCoins] only; [getTotalCoinsEarned] is unchanged
 *
 * [saveProgress] and [saveLaunchProgress] are pure persistence — they update
 * in-memory maps only, with no coin-award side effects.
 *
 * @param initialCoins Starting coin balance. Both [getCoins] and [getTotalCoinsEarned]
 *   are initialised to this value (mirrors how the real repo seeds a fresh install).
 * @param initialAchievements Set of achievement IDs already unlocked at construction.
 */
public class FakeGameRepository(
    initialCoins: Int = 0,
    initialAchievements: Set<String> = emptySet()
) : IGameRepository {

    // ── Internal state ────────────────────────────────────────────────────

    private var _coins: Int = initialCoins
    private val launchBestScores = mutableMapOf<Difficulty, Int>()
    private var _totalCoinsEarned: Int = initialCoins
    private val progressById = mutableMapOf<Int, UserProgress>()
    private val launchProgressById = mutableMapOf<Int, LaunchProgress>()
    private val unlockedAchievements: MutableMap<String, Boolean> =
        initialAchievements.associateWith { true }.toMutableMap()
    private val prefs = mutableMapOf<String, Any?>()

    // ── Coin Economy ──────────────────────────────────────────────────────

    override suspend fun addCoins(amount: Int) {
        require(amount > 0) { "addCoins amount must be positive (got $amount)" }
        _coins += amount
        _totalCoinsEarned += amount
    }

    override suspend fun spendCoins(amount: Int): Boolean {
        require(amount > 0) { "spendCoins amount must be positive (got $amount)" }
        return if (_coins >= amount) {
            _coins -= amount
            true
        } else {
            false
        }
    }

    override suspend fun refundCoins(amount: Int) {
        require(amount > 0) { "refundCoins amount must be positive (got $amount)" }
        _coins += amount
        // totalCoinsEarned NOT touched — matches ADR coin-invariant
    }

    override suspend fun getCoins(): Int = _coins

    override suspend fun getTotalCoinsEarned(): Int = _totalCoinsEarned

    // ── Stage Progress ────────────────────────────────────────────────────

    override suspend fun saveProgress(stageId: Int, stars: Int, score: Int): Boolean {
        require(stars in 1..3) { "stars must be 1, 2, or 3 (got $stars)" }
        val existing = progressById[stageId]
        val isFirstClear = existing == null || !existing.completed
        val bestStars = maxOf(stars, existing?.stars ?: 0)
        val bestScore = maxOf(score, existing?.bestScore ?: 0)
        progressById[stageId] = UserProgress(
            stageId = stageId,
            stars = bestStars,
            completed = true,
            catUnlocked = existing?.catUnlocked,
            bestScore = bestScore
        )
        return isFirstClear
    }

    override suspend fun getProgress(levelId: Int): UserProgress? = progressById[levelId]

    override suspend fun getMaxCompletedLevel(): Int =
        progressById.entries
            .filter { it.value.completed }
            .maxOfOrNull { it.key } ?: 0

    override suspend fun getThreeStarCount(): Int =
        progressById.values.count { it.stars == 3 }

    override suspend fun getBestScore(levelId: Int): Int =
        progressById[levelId]?.bestScore ?: 0

    // ── Launch Mode Progress ──────────────────────────────────────────────

    override suspend fun saveLaunchProgress(
        stageId: Int,
        stars: Int,
        coinMultiplier: Float,
        score: Int
    ) {
        require(stars in 1..3) { "stars must be 1, 2, or 3 (got $stars)" }
        val existing = launchProgressById[stageId]
        val bestStars = maxOf(stars, existing?.stars ?: 0)
        val bestScore = maxOf(score, existing?.bestScore ?: 0)
        launchProgressById[stageId] = LaunchProgress(
            stageId = stageId,
            stars = bestStars,
            completed = true,
            bestScore = bestScore
        )
    }

    override suspend fun getLaunchProgress(stageId: Int): LaunchProgress? =
        launchProgressById[stageId]

    override suspend fun getCompletedLaunchCount(): Int =
        launchProgressById.values.count { it.completed }

    override suspend fun getMaxCompletedLaunchStage(): Int =
        launchProgressById.entries
            .filter { it.value.completed }
            .maxOfOrNull { it.key } ?: 0

    // ── Achievements ──────────────────────────────────────────────────────

    override suspend fun unlockAchievement(id: String): Boolean =
        if (unlockedAchievements.containsKey(id)) {
            false
        } else {
            unlockedAchievements[id] = true
            true
        }

    override suspend fun isAchievementUnlocked(id: String): Boolean =
        unlockedAchievements[id] == true

    override suspend fun getUnlockedAchievementCount(): Int = unlockedAchievements.size

    // ── Star Ratings Batch ────────────────────────────────────────────────

    override suspend fun getAllStarRatings(): IntArray {
        val ratings = IntArray(211)
        progressById.forEach { (id, p) -> if (id in 1..210) ratings[id] = p.stars }
        return ratings
    }

    // ── Cat Launch Best Scores per Difficulty ─────────────────────────────

    override fun getLaunchBestScore(difficulty: Difficulty): Int =
        launchBestScores[difficulty] ?: 0

    override fun updateLaunchBestScore(difficulty: Difficulty, score: Int) {
        val current = launchBestScores[difficulty] ?: 0
        if (score > current) launchBestScores[difficulty] = score
    }

    // ── SharedPrefs ───────────────────────────────────────────────────────

    override fun getSelectedCatId(): Int =
        (prefs[KEY_SELECTED_CAT] as? Int) ?: 1

    override fun setSelectedCatId(catId: Int) {
        prefs[KEY_SELECTED_CAT] = catId
    }

    override fun getEndlessCount(): Int =
        (prefs[KEY_ENDLESS_COUNT] as? Int) ?: 0

    override fun setEndlessCount(count: Int) {
        prefs[KEY_ENDLESS_COUNT] = count
    }

    override fun getEndlessBest(): Int =
        (prefs[KEY_ENDLESS_BEST] as? Int) ?: 0

    override fun setEndlessBest(best: Int) {
        prefs[KEY_ENDLESS_BEST] = best
    }

    override fun getDailyEndlessCoins(): Int =
        (prefs[KEY_DAILY_ENDLESS_COINS] as? Int) ?: 0

    override suspend fun addEndlessCoins(amount: Int): Int {
        val current = getDailyEndlessCoins()
        val remaining = (GameRepository.ENDLESS_DAILY_COIN_CAP - current).coerceAtLeast(0)
        val actual = amount.coerceAtMost(remaining)
        if (actual > 0) {
            prefs[KEY_DAILY_ENDLESS_COINS] = current + actual
            addCoins(actual)
        }
        return actual
    }

    override fun isTutorialCompleted(): Boolean =
        (prefs[KEY_TUTORIAL_COMPLETED] as? Boolean) ?: false

    override fun setTutorialCompleted(value: Boolean) {
        prefs[KEY_TUTORIAL_COMPLETED] = value
    }

    override fun getPowerUpUseCount(): Int =
        (prefs[KEY_POWERUP_USE_COUNT] as? Int) ?: 0

    override fun incrementPowerUpUseCount() {
        prefs[KEY_POWERUP_USE_COUNT] = getPowerUpUseCount() + 1
    }

    override fun isSoundEnabled(): Boolean =
        (prefs[KEY_SOUND_ENABLED] as? Boolean) ?: true

    override fun setSoundEnabled(enabled: Boolean) {
        prefs[KEY_SOUND_ENABLED] = enabled
    }

    override fun getStagesSinceLastAd(): Int =
        (prefs[KEY_STAGES_SINCE_AD] as? Int) ?: 0

    override fun setStagesSinceLastAd(count: Int) {
        prefs[KEY_STAGES_SINCE_AD] = count
    }

    // ── Private key constants ─────────────────────────────────────────────

    private companion object {
        const val KEY_SELECTED_CAT = "selectedCatId"
        const val KEY_ENDLESS_COUNT = "endlessCount"
        const val KEY_ENDLESS_BEST = "endlessBest"
        const val KEY_DAILY_ENDLESS_COINS = "dailyEndlessCoins"
        const val KEY_TUTORIAL_COMPLETED = "tutorialCompleted"
        const val KEY_POWERUP_USE_COUNT = "powerUpUseCount"
        const val KEY_SOUND_ENABLED = "soundEnabled"
        const val KEY_STAGES_SINCE_AD = "stagesSinceLastAd"
    }
}
