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
            CatDefinition(2, "치즈", 5, R.drawable.cat_2),
            CatDefinition(3, "모모", 10, R.drawable.cat_3),
            CatDefinition(4, "구름", 15, R.drawable.cat_4),
            CatDefinition(5, "호랑", 20, R.drawable.cat_5),
            CatDefinition(6, "까미", 25, R.drawable.cat_6),
            CatDefinition(7, "별이", 30, R.drawable.cat_7),
            CatDefinition(8, "달이", 35, R.drawable.cat_8),
            CatDefinition(9, "봄이", 40, R.drawable.cat_1),
            CatDefinition(10, "여름", 45, R.drawable.cat_2),
            CatDefinition(11, "가을", 50, R.drawable.cat_3),
            CatDefinition(12, "겨울", 55, R.drawable.cat_4),
            CatDefinition(13, "솜이", 60, R.drawable.cat_5),
            CatDefinition(14, "꽃이", 65, R.drawable.cat_6),
            CatDefinition(15, "하늘", 70, R.drawable.cat_7),
            CatDefinition(16, "바다", 75, R.drawable.cat_8),
            CatDefinition(17, "무지개", 80, R.drawable.cat_1),
            CatDefinition(18, "보석", 85, R.drawable.cat_2),
            CatDefinition(19, "왕자", 90, R.drawable.cat_3),
            CatDefinition(20, "공주", 95, R.drawable.cat_4),
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
}
