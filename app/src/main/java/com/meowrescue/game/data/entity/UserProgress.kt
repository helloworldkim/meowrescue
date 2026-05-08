package com.meowrescue.game.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey @ColumnInfo(name = "levelId") val stageId: Int,
    val stars: Int,
    val completed: Boolean,
    val catUnlocked: String?,
    val bestScore: Int = 0
)
