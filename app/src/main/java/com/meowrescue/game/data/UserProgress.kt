package com.meowrescue.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey val levelId: Int,
    val stars: Int,
    val completed: Boolean,
    val catUnlocked: String?,
    val bestScore: Int = 0
)
