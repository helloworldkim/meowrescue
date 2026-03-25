package com.meowrescue.game.data

import android.content.Context
import android.content.Context.MODE_PRIVATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences("meow_rescue", MODE_PRIVATE)

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

    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean("sound_enabled", true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }
}
