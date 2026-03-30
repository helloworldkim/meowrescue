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
    }

    // ── Progress ────────────────────────────────────────────────────────

    suspend fun saveProgress(levelId: Int, stars: Int, catId: String?) = withContext(Dispatchers.IO) {
        val existing = db.userProgressDao().getProgressForLevel(levelId)
        val bestStars = maxOf(stars, existing?.stars ?: 0)
        val bestCat = catId ?: existing?.catUnlocked
        val progress = UserProgress(
            levelId = levelId,
            stars = bestStars,
            completed = bestStars > 0,
            catUnlocked = bestCat
        )
        db.userProgressDao().saveProgress(progress)
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

    fun getEndlessCount(): Int = prefs.getInt("endless_count", 1)

    fun setEndlessCount(count: Int) {
        prefs.edit().putInt("endless_count", count).apply()
    }

    fun getEndlessBest(): Int = prefs.getInt("endless_best", 0)

    fun setEndlessBest(best: Int) {
        prefs.edit().putInt("endless_best", best).apply()
    }
}
