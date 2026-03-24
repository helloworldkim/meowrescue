package com.meowrescue.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserProgressDao {

    @Query("SELECT * FROM user_progress WHERE levelId = :levelId")
    fun getProgressForLevel(levelId: Int): UserProgress?

    @Query("SELECT * FROM user_progress")
    fun getAllProgress(): List<UserProgress>

    @Query("SELECT catUnlocked FROM user_progress WHERE catUnlocked IS NOT NULL")
    fun getUnlockedCats(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveProgress(progress: UserProgress)

    @Query("SELECT MAX(levelId) FROM user_progress WHERE completed = 1")
    fun getMaxCompletedLevel(): Int?
}
