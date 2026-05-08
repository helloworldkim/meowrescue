package com.meowrescue.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_stats")
data class PlayerStats(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 0,
    val totalCoinsEarned: Int = 0
)
