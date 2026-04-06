package com.meowrescue.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "launch_progress")
data class LaunchProgress(
    @PrimaryKey val stageId: Int,
    val stars: Int,
    val completed: Boolean,
    val bestScore: Int
)
