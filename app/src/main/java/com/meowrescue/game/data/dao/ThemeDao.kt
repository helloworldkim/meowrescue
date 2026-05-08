package com.meowrescue.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for [ThemeUnlock] — records which grid themes the player has purchased.
 *
 * All methods are synchronous (called from [GameRepository] inside `withContext(Dispatchers.IO)`).
 * This interface is `internal` per ADR-0006 — access exclusively through [GameRepository].
 */
@Dao
internal interface ThemeDao {

    /**
     * Inserts a theme unlock record.
     * Ignores duplicate [ThemeUnlock.themeId] rows — a purchase is idempotent.
     * @return the row ID of the inserted row, or -1 if the theme was already unlocked.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertUnlock(t: ThemeUnlock): Long

    /** Returns all purchased theme unlocks. Returns an empty list if none have been purchased. */
    @Query("SELECT * FROM theme_unlocks")
    fun getAllUnlocked(): List<ThemeUnlock>

    /**
     * Returns true if the theme with the given [themeId] has been purchased.
     * Uses `COUNT(*) > 0` to produce a direct Boolean without a nullable intermediate.
     */
    @Query("SELECT COUNT(*) > 0 FROM theme_unlocks WHERE themeId = :themeId")
    fun isUnlocked(themeId: String): Boolean
}
