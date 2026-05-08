package com.meowrescue.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE unlocked = 1")
    fun getUnlocked(): List<Achievement>

    @Query("SELECT * FROM achievements WHERE achievementId = :id")
    fun get(id: String): Achievement?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(achievement: Achievement)

    @Query("UPDATE achievements SET unlocked = 1, unlockedAt = :time WHERE achievementId = :id AND unlocked = 0")
    fun unlock(id: String, time: String): Int  // returns rows affected

    @Query("SELECT COUNT(*) FROM achievements WHERE unlocked = 1")
    fun getUnlockedCount(): Int
}
