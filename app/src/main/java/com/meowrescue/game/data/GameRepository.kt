package com.meowrescue.game.data

import android.content.Context
import com.meowrescue.game.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(context: Context) : IGameRepository {

    private val db = AppDatabase.getInstance(context)
    private val sharedPrefs = SharedPrefsWrapper(context)

    // ── Cat Collection Definitions ──────────────────────────────────────

    data class CatDefinition(
        val id: Int,
        val name: String,
        val requiredStage: Int,
        val drawableRes: Int
    )

    companion object {
        val CAT_DEFINITIONS = listOf(
            CatDefinition(1, "나비", 1, R.drawable.cat_1),
            CatDefinition(2, "봄이", 15, R.drawable.cat_9),
            CatDefinition(3, "여름", 30, R.drawable.cat_10),
            CatDefinition(4, "가을", 45, R.drawable.cat_11),
            CatDefinition(5, "겨울", 60, R.drawable.cat_12),
            CatDefinition(6, "솜이", 75, R.drawable.cat_13),
            CatDefinition(7, "꽃이", 90, R.drawable.cat_14),
            CatDefinition(8, "하늘", 110, R.drawable.cat_15),
            CatDefinition(9, "바다", 130, R.drawable.cat_16),
            CatDefinition(10, "무지개", 150, R.drawable.cat_17),
            CatDefinition(11, "보석", 170, R.drawable.cat_18),
            CatDefinition(12, "왕자", 185, R.drawable.cat_19),
            CatDefinition(13, "공주", 200, R.drawable.cat_20),
        )

        /** Maximum number of unique Launch stages that earn the first-clear bonus. */
        const val LAUNCH_FIRST_CLEAR_CAP = 50

        /** Maximum Endless coins earnable per day. */
        const val ENDLESS_DAILY_COIN_CAP = 150
    }

    // ── Progress ────────────────────────────────────────────────────────

    override suspend fun saveProgress(stageId: Int, stars: Int, score: Int): Boolean = withContext(Dispatchers.IO) {
        require(stars in 1..3) { "stars must be 1, 2, or 3 (got $stars)" }
        val existing = db.userProgressDao().getProgressForLevel(stageId)
        val bestStars = maxOf(stars, existing?.stars ?: 0)
        val bestScore = maxOf(score, existing?.bestScore ?: 0)
        val isFirstClear = existing == null || !existing.completed
        db.userProgressDao().saveProgress(
            UserProgress(stageId = stageId, stars = bestStars, completed = bestStars > 0,
                catUnlocked = existing?.catUnlocked, bestScore = bestScore)
        )
        val starCoins = when (stars) { 3 -> 30; 2 -> 20; 1 -> 10; else -> 0 }
        addCoins(starCoins + if (isFirstClear) 50 else 0)
        isFirstClear
    }

    override suspend fun getProgress(levelId: Int): UserProgress? = withContext(Dispatchers.IO) {
        db.userProgressDao().getProgressForLevel(levelId)
    }

    override suspend fun getMaxCompletedLevel(): Int = withContext(Dispatchers.IO) {
        db.userProgressDao().getMaxCompletedLevel() ?: 0
    }

    suspend fun getUnlockedCats(): List<CatDefinition> = withContext(Dispatchers.IO) {
        val maxStage = db.userProgressDao().getMaxCompletedLevel() ?: 0
        CAT_DEFINITIONS.filter { it.requiredStage <= maxStage }
    }

    // ── Cat Collection ─────────────────────────────────────────────────

    override fun getSelectedCatId(): Int {
        return sharedPrefs.getSelectedCatId()
    }

    override fun setSelectedCatId(catId: Int) {
        sharedPrefs.setSelectedCatId(catId)
    }

    /** Returns newly unlocked cat for given stage, or null */
    fun getNewlyUnlockedCat(clearedStage: Int): CatDefinition? {
        return CAT_DEFINITIONS.firstOrNull { it.requiredStage == clearedStage }
    }

    // ── Settings ────────────────────────────────────────────────────────

    override fun isSoundEnabled(): Boolean {
        return sharedPrefs.isSoundEnabled()
    }

    override fun setSoundEnabled(enabled: Boolean) {
        sharedPrefs.setSoundEnabled(enabled)
    }

    override fun getStagesSinceLastAd(): Int = sharedPrefs.getStagesSinceLastAd()

    override fun setStagesSinceLastAd(count: Int) {
        sharedPrefs.setStagesSinceLastAd(count)
    }

    override fun getSelectedThemeId(): String? = sharedPrefs.getSelectedThemeId()

    override fun setSelectedThemeId(value: String?) {
        sharedPrefs.setSelectedThemeId(value)
    }

    // ── Endless Mode ─────────────────────────────────────────────────

    override fun getEndlessCount(): Int = sharedPrefs.getEndlessCount()

    override fun setEndlessCount(count: Int) {
        sharedPrefs.setEndlessCount(count)
    }

    override fun getEndlessBest(): Int = sharedPrefs.getEndlessBest()

    override fun setEndlessBest(best: Int) {
        sharedPrefs.setEndlessBest(best)
    }

    // ── Endless Daily Coin Cap ───────────────────────────────────────────

    /** Returns how many Endless coins have been earned today. Resets at midnight (local). */
    override fun getDailyEndlessCoins(): Int {
        val savedDate = sharedPrefs.getEndlessCoinDate()
        val today = java.time.LocalDate.now().toString()
        return if (savedDate == today) sharedPrefs.getEndlessCoinToday() else 0
    }

    /** Adds Endless coins up to the daily cap. Returns the actual amount added (may be less than requested). */
    override suspend fun addEndlessCoins(amount: Int): Int {
        val today = java.time.LocalDate.now().toString()
        val savedDate = sharedPrefs.getEndlessCoinDate()
        val current = if (savedDate == today) sharedPrefs.getEndlessCoinToday() else 0
        val remaining = (ENDLESS_DAILY_COIN_CAP - current).coerceAtLeast(0)
        val actual = amount.coerceAtMost(remaining)
        if (actual > 0) {
            sharedPrefs.setEndlessCoinDateAndToday(today, current + actual)
            addCoins(actual)
        }
        return actual
    }

    // ── Tutorial ─────────────────────────────────────────────────────────

    override fun isTutorialCompleted(): Boolean = sharedPrefs.isTutorialCompleted()

    override fun setTutorialCompleted(value: Boolean) {
        sharedPrefs.setTutorialCompleted(value)
    }

    // ── Launch Mode Progress ─────────────────────────────────────────

    override suspend fun saveLaunchProgress(stageId: Int, stars: Int, coinMultiplier: Float, score: Int): Boolean = withContext(Dispatchers.IO) {
        require(stars in 1..3) { "stars must be 1, 2, or 3 (got $stars)" }
        val existing = db.launchProgressDao().getProgressForStage(stageId)
        val bestStars = maxOf(stars, existing?.stars ?: 0)
        val bestScore = maxOf(score, existing?.bestScore ?: 0)
        val isFirstClear = existing == null || !existing.completed
        db.launchProgressDao().saveProgress(
            LaunchProgress(stageId = stageId, stars = bestStars, completed = bestStars > 0, bestScore = bestScore)
        )
        isFirstClear
    }

    override suspend fun getLaunchProgress(stageId: Int): LaunchProgress? = withContext(Dispatchers.IO) {
        db.launchProgressDao().getProgressForStage(stageId)
    }

    override suspend fun getMaxCompletedLaunchStage(): Int = withContext(Dispatchers.IO) {
        db.launchProgressDao().getMaxCompletedStage() ?: 0
    }

    suspend fun getAllLaunchProgress(): List<LaunchProgress> = withContext(Dispatchers.IO) {
        db.launchProgressDao().getAllProgress()
    }

    override suspend fun getCompletedLaunchCount(): Int = withContext(Dispatchers.IO) {
        db.launchProgressDao().getCompletedCount()
    }

    // ── Coin Economy ──────────────────────────────────────────────

    private suspend fun ensurePlayerStats() = withContext(Dispatchers.IO) {
        if (db.playerStatsDao().getStats() == null) {
            db.playerStatsDao().saveStats(PlayerStats())
        }
    }

    override suspend fun getCoins(): Int = withContext(Dispatchers.IO) {
        ensurePlayerStats()
        db.playerStatsDao().getCoins() ?: 0
    }

    override suspend fun addCoins(amount: Int) = withContext(Dispatchers.IO) {
        require(amount > 0) { "addCoins amount must be positive (got $amount)" }
        ensurePlayerStats()
        db.playerStatsDao().addCoins(amount)
    }

    override suspend fun spendCoins(amount: Int): Boolean = withContext(Dispatchers.IO) {
        require(amount > 0) { "spendCoins amount must be positive (got $amount)" }
        ensurePlayerStats()
        db.playerStatsDao().spendCoins(amount) > 0
    }

    /** Refund coins without inflating totalCoinsEarned. */
    override suspend fun refundCoins(amount: Int) = withContext(Dispatchers.IO) {
        require(amount > 0) { "refundCoins amount must be positive (got $amount)" }
        ensurePlayerStats()
        db.playerStatsDao().refundCoins(amount)
    }

    override suspend fun getTotalCoinsEarned(): Int = withContext(Dispatchers.IO) {
        ensurePlayerStats()
        db.playerStatsDao().getStats()?.totalCoinsEarned ?: 0
    }

    // ── Achievements ──────────────────────────────────────────────

    suspend fun seedAchievements() = withContext(Dispatchers.IO) {
        AchievementDefs.ALL.forEach { def ->
            db.achievementDao().insert(Achievement(achievementId = def.id))
        }
    }

    override suspend fun unlockAchievement(id: String): Boolean = withContext(Dispatchers.IO) {
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        db.achievementDao().insert(Achievement(achievementId = id))
        val unlocked = db.achievementDao().unlock(id, time) > 0
        if (unlocked) {
            val def = AchievementDefs.get(id)
            if (def != null) addCoins(def.coinReward)
        }
        unlocked
    }

    override suspend fun isAchievementUnlocked(id: String): Boolean = withContext(Dispatchers.IO) {
        db.achievementDao().get(id)?.unlocked == true
    }

    suspend fun getUnlockedAchievements(): List<Achievement> = withContext(Dispatchers.IO) {
        db.achievementDao().getUnlocked()
    }

    override suspend fun getUnlockedAchievementCount(): Int = withContext(Dispatchers.IO) {
        db.achievementDao().getUnlockedCount()
    }

    // ── Best Score ────────────────────────────────────────────────

    // ── Star Ratings Batch ────────────────────────────────────────────────

    override suspend fun getAllStarRatings(): IntArray = withContext(Dispatchers.IO) {
        val ratings = IntArray(211)
        db.userProgressDao().getAllProgress().forEach { p ->
            if (p.stageId in 1..210) ratings[p.stageId] = p.stars
        }
        ratings
    }

    // ── Cat Launch Best Scores per Difficulty ─────────────────────────────

    override fun getLaunchBestScore(difficulty: Difficulty): Int =
        sharedPrefs.getLaunchBestScore(difficulty)

    override fun updateLaunchBestScore(difficulty: Difficulty, score: Int) {
        if (score > sharedPrefs.getLaunchBestScore(difficulty))
            sharedPrefs.setLaunchBestScore(difficulty, score)
    }

    override suspend fun getBestScore(levelId: Int): Int = withContext(Dispatchers.IO) {
        db.userProgressDao().getProgressForLevel(levelId)?.bestScore ?: 0
    }

    // ── Power-Up Tracking ─────────────────────────────────────────

    override fun getPowerUpUseCount(): Int = sharedPrefs.getPowerUpUseCount()

    override fun incrementPowerUpUseCount() {
        sharedPrefs.incrementPowerUpUseCount()
    }

    // ── Star Count ────────────────────────────────────────────────

    override suspend fun getThreeStarCount(): Int = withContext(Dispatchers.IO) {
        db.userProgressDao().getThreeStarCount()
    }

    // ── Cosmetic Purchases (TR-expand-002) ────────────────────────

    /**
     * Records a cosmetic purchase for [catId] and [type] (`"PALETTE"` or `"ACCESSORY"`).
     * Idempotent — if the purchase already exists the insert is silently ignored.
     * @param purchasedAt UTC epoch millis; defaults to [System.currentTimeMillis].
     * @return true if the row was newly inserted, false if it already existed.
     */
    suspend fun insertCosmeticPurchase(
        catId: Int,
        type: String,
        purchasedAt: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        db.cosmeticDao().insertPurchase(CosmeticPurchase(catId, type, purchasedAt)) != -1L
    }

    /**
     * Returns all cosmetic purchases for [catId].
     * Returns an empty list if the cat has no purchases.
     */
    suspend fun getCosmeticPurchasesForCat(catId: Int): List<CosmeticPurchase> =
        withContext(Dispatchers.IO) {
            db.cosmeticDao().getPurchasesForCat(catId)
        }

    // ── Equipped Cosmetics (TR-expand-002) ───────────────────────

    /**
     * Equips cosmetic [cosmeticId] on [catId], replacing any previous selection.
     */
    suspend fun setEquippedCosmetic(catId: Int, cosmeticId: Int) = withContext(Dispatchers.IO) {
        db.selectedCosmeticDao().upsertSelection(SelectedCosmetic(catId, cosmeticId))
    }

    /**
     * Returns the ID of the cosmetic currently equipped on [catId], or null if none.
     */
    suspend fun getEquippedCosmetic(catId: Int): Int? = withContext(Dispatchers.IO) {
        db.selectedCosmeticDao().getSelectionForCat(catId)?.cosmeticId
    }

    /**
     * Removes the equipped cosmetic record for [catId]. After this call
     * [getEquippedCosmetic] returns null for that cat.
     */
    suspend fun clearEquippedCosmetic(catId: Int) = withContext(Dispatchers.IO) {
        db.selectedCosmeticDao().clearSelection(catId)
    }

    // ── Theme Unlocks (TR-expand-004) ─────────────────────────────

    /**
     * Records a theme unlock for [themeId].
     * Idempotent — if the theme is already unlocked the insert is silently ignored.
     * @param purchasedAt UTC epoch millis; defaults to [System.currentTimeMillis].
     * @return true if the row was newly inserted, false if already unlocked.
     */
    suspend fun insertThemeUnlock(
        themeId: String,
        purchasedAt: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        db.themeDao().insertUnlock(ThemeUnlock(themeId, purchasedAt)) != -1L
    }

    /**
     * Returns true if the theme identified by [themeId] has been purchased.
     */
    suspend fun isThemeUnlocked(themeId: String): Boolean = withContext(Dispatchers.IO) {
        db.themeDao().isUnlocked(themeId)
    }

    /**
     * Returns all purchased theme unlocks.
     * Returns an empty list if no themes have been purchased.
     */
    suspend fun getAllThemeUnlocks(): List<ThemeUnlock> = withContext(Dispatchers.IO) {
        db.themeDao().getAllUnlocked()
    }
}
