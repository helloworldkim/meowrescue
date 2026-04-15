package com.meowrescue.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val achievementId: String,
    val unlocked: Boolean = false,
    val unlockedAt: String = ""
)
