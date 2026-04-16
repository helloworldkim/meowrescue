package com.meowrescue.game.data

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.meowrescue.game.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences("meow_rescue", MODE_PRIVATE)

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
    }

    // ── Progress ────────────────────────────────────────────────────────

    suspend fun saveProgress(levelId: Int, stars: Int, catId: String?, score: Int = 0) = withContext(Dispatchers.IO) {
        require(stars in 1..3) { "stars must be 1, 2, or 3 (got $stars)" }
        val existing = db.userProgressDao().getProgressForLevel(levelId)
        val bestStars = maxOf(stars, existing?.stars ?: 0)
        val bestCat = catId ?: existing?.catUnlocked
        val bestScore = maxOf(score, existing?.bestScore ?: 0)
        val isFirstClear = existing == null || !existing.completed
        val progress = UserProgress(
            levelId = levelId,
            stars = bestStars,
            completed = bestStars > 0,
            catUnlocked = bestCat,
            bestScore = bestScore
        )
        db.userProgressDao().saveProgress(progress)

        // Award coins
        val starCoins = when (stars) { 3 -> 30; 2 -> 20; 1 -> 10; else -> 0 }
        val firstClearBonus = if (isFirstClear) 50 else 0
        val totalCoins = starCoins + firstClearBonus
        addCoins(totalCoins)
    }

    suspend fun getProgress(levelId: Int): UserProgress? = withContext(Dispatchers.IO) {
        db.userProgressDao().getProgressForLevel(levelId)
    }

    suspend fun getMaxCompletedLevel(): Int = withContext(Dispatchers.IO) {
        db.userProgressDao().getMaxCompletedLevel() ?: 0
    }

    suspend fun getUnlockedCats(): List<String> = withContext(Dispatchers.IO) {
        db.userProgressDao().getUnlockedCats()
    }

    // ── Cat Collection ─────────────────────────────────────────────────

    fun getSelectedCatId(): Int {
        return prefs.getInt("selected_cat", 1)
    }

    fun setSelectedCatId(catId: Int) {
        prefs.edit().putInt("selected_cat", catId).apply()
    }

    /** Returns the drawable resource for the selected cat */
    fun getSelectedCatDrawable(): Int {
        val selectedId = getSelectedCatId()
        return CAT_DEFINITIONS.firstOrNull { it.id == selectedId }?.drawableRes ?: R.drawable.cat_1
    }

    /** Returns newly unlocked cat for given stage, or null */
    fun getNewlyUnlockedCat(clearedStage: Int): CatDefinition? {
        return CAT_DEFINITIONS.firstOrNull { it.requiredStage == clearedStage }
    }

    // ── Settings ────────────────────────────────────────────────────────

    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean("sound_enabled", true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    // ── Endless Mode ─────────────────────────────────────────────────

    fun getEndlessCount(): Int = prefs.getInt("endless_count", 0)

    fun setEndlessCount(count: Int) {
        prefs.edit().putInt("endless_count", count).apply()
    }

    fun getEndlessBest(): Int = prefs.getInt("endless_best", 0)

    fun setEndlessBest(best: Int) {
        prefs.edit().putInt("endless_best", best).apply()
    }

    // ── Tutorial ─────────────────────────────────────────────────────────

    fun isTutorialCompleted(): Boolean = prefs.getBoolean("tutorial_completed", false)

    fun setTutorialCompleted() {
        prefs.edit().putBoolean("tutorial_completed", true).apply()
    }

    // ── Launch Mode Progress ─────────────────────────────────────────

    suspend fun saveLaunchProgress(stageId: Int, stars: Int, coinMultiplier: Float = 1.0f, score: Int = 0) = withContext(Dispatchers.IO) {
        require(stars in 1..3) { "stars must be 1, 2, or 3 (got $stars)" }
        val existing = db.launchProgressDao().getProgressForStage(stageId)
        val bestStars = maxOf(stars, existing?.stars ?: 0)
        val bestScore = maxOf(score, existing?.bestScore ?: 0)
        val isFirstClear = existing == null || !existing.completed
        db.launchProgressDao().saveProgress(
            LaunchProgress(stageId = stageId, stars = bestStars, completed = bestStars > 0, bestScore = bestScore)
        )

        // Award coins with difficulty multiplier + first-clear cap (50 unique stages)
        val baseStarCoins = when (stars) { 3 -> 30; 2 -> 20; 1 -> 10; else -> 0 }
        val scaledStarCoins = Math.round(baseStarCoins * coinMultiplier)
        val completedCount = db.launchProgressDao().getCompletedCount()
        val firstClearBonus = if (isFirstClear && completedCount <= LAUNCH_FIRST_CLEAR_CAP) 50 else 0
        addCoins(scaledStarCoins + firstClearBonus)
    }

    suspend fun getLaunchProgress(stageId: Int): LaunchProgress? = withContext(Dispatchers.IO) {
        db.launchProgressDao().getProgressForStage(stageId)
    }

    suspend fun getMaxCompletedLaunchStage(): Int = withContext(Dispatchers.IO) {
        db.launchProgressDao().getMaxCompletedStage() ?: 0
    }

    suspend fun getAllLaunchProgress(): List<LaunchProgress> = withContext(Dispatchers.IO) {
        db.launchProgressDao().getAllProgress()
    }

    suspend fun getCompletedLaunchCount(): Int = withContext(Dispatchers.IO) {
        db.launchProgressDao().getCompletedCount()
    }

    // ── Coin Economy ──────────────────────────────────────────────

    private suspend fun ensurePlayerStats() = withContext(Dispatchers.IO) {
        if (db.playerStatsDao().getStats() == null) {
            db.playerStatsDao().saveStats(PlayerStats())
        }
    }

    suspend fun getCoins(): Int = withContext(Dispatchers.IO) {
        ensurePlayerStats()
        db.playerStatsDao().getCoins() ?: 0
    }

    suspend fun addCoins(amount: Int) = withContext(Dispatchers.IO) {
        require(amount > 0) { "addCoins amount must be positive (got $amount)" }
        ensurePlayerStats()
        db.playerStatsDao().addCoins(amount)
    }

    suspend fun spendCoins(amount: Int): Boolean = withContext(Dispatchers.IO) {
        require(amount > 0) { "spendCoins amount must be positive (got $amount)" }
        ensurePlayerStats()
        db.playerStatsDao().spendCoins(amount) > 0
    }

    /** Refund coins without inflating totalCoinsEarned. */
    suspend fun refundCoins(amount: Int) = withContext(Dispatchers.IO) {
        require(amount > 0) { "refundCoins amount must be positive (got $amount)" }
        ensurePlayerStats()
        db.playerStatsDao().refundCoins(amount)
    }

    suspend fun getTotalCoinsEarned(): Int = withContext(Dispatchers.IO) {
        ensurePlayerStats()
        db.playerStatsDao().getStats()?.totalCoinsEarned ?: 0
    }

    // ── Achievements ──────────────────────────────────────────────

    suspend fun unlockAchievement(id: String): Boolean = withContext(Dispatchers.IO) {
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

    suspend fun isAchievementUnlocked(id: String): Boolean = withContext(Dispatchers.IO) {
        db.achievementDao().get(id)?.unlocked == true
    }

    suspend fun getUnlockedAchievements(): List<Achievement> = withContext(Dispatchers.IO) {
        db.achievementDao().getUnlocked()
    }

    suspend fun getUnlockedAchievementCount(): Int = withContext(Dispatchers.IO) {
        db.achievementDao().getUnlockedCount()
    }

    // ── Best Score ────────────────────────────────────────────────

    suspend fun getBestScore(levelId: Int): Int = withContext(Dispatchers.IO) {
        db.userProgressDao().getProgressForLevel(levelId)?.bestScore ?: 0
    }

    // ── Power-Up Tracking ─────────────────────────────────────────

    fun getPowerUpUseCount(): Int = prefs.getInt("powerup_use_count", 0)

    fun incrementPowerUpUseCount() {
        prefs.edit().putInt("powerup_use_count", getPowerUpUseCount() + 1).apply()
    }

    // ── Star Count ────────────────────────────────────────────────

    suspend fun getThreeStarCount(): Int = withContext(Dispatchers.IO) {
        db.userProgressDao().getAllProgress().count { it.stars >= 3 }
    }
}
