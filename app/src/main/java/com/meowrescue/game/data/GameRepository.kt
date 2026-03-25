package com.meowrescue.game.data

import android.content.Context
import android.content.Context.MODE_PRIVATE

class GameRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences("meow_rescue", MODE_PRIVATE)

    fun saveProgress(levelId: Int, stars: Int, catId: String?) {
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

    fun getProgress(levelId: Int): UserProgress? {
        return db.userProgressDao().getProgressForLevel(levelId)
    }

    fun getMaxCompletedLevel(): Int {
        return db.userProgressDao().getMaxCompletedLevel() ?: 0
    }

    fun getUnlockedCats(): List<String> {
        return db.userProgressDao().getUnlockedCats()
    }

    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean("sound_enabled", true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }
}
