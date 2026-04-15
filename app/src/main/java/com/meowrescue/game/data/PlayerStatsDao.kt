package com.meowrescue.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayerStatsDao {

    @Query("SELECT * FROM player_stats WHERE id = 1")
    fun getStats(): PlayerStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveStats(stats: PlayerStats)

    @Query("UPDATE player_stats SET coins = coins + :amount, totalCoinsEarned = totalCoinsEarned + :amount WHERE id = 1")
    fun addCoins(amount: Int)

    @Query("UPDATE player_stats SET coins = coins - :amount WHERE id = 1 AND coins >= :amount")
    fun spendCoins(amount: Int): Int  // returns rows affected (1 = success, 0 = insufficient)

    @Query("UPDATE player_stats SET coins = coins + :amount WHERE id = 1")
    fun refundCoins(amount: Int)

    @Query("SELECT coins FROM player_stats WHERE id = 1")
    fun getCoins(): Int?
}
