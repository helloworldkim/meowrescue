package com.meowrescue.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface LaunchProgressDao {

    @Query("SELECT * FROM launch_progress WHERE stageId = :stageId")
    fun getProgressForStage(stageId: Int): LaunchProgress?

    @Query("SELECT * FROM launch_progress")
    fun getAllProgress(): List<LaunchProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveProgress(progress: LaunchProgress)

    @Query("SELECT MAX(stageId) FROM launch_progress WHERE completed = 1")
    fun getMaxCompletedStage(): Int?

    @Query("SELECT COUNT(*) FROM launch_progress WHERE completed = 1")
    fun getCompletedCount(): Int
}
