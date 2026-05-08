package com.meowrescue.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for [SelectedCosmetic] — tracks the currently equipped cosmetic per cat.
 *
 * All methods are synchronous (called from [GameRepository] inside `withContext(Dispatchers.IO)`).
 * This interface is `internal` per ADR-0006 — access exclusively through [GameRepository].
 */
@Dao
internal interface SelectedCosmeticDao {

    /**
     * Inserts or replaces the equipped cosmetic for a cat.
     * REPLACE strategy ensures equipping a new cosmetic overwrites the previous selection.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSelection(s: SelectedCosmetic)

    /**
     * Returns the current cosmetic selection for the given cat, or null if none is equipped.
     */
    @Query("SELECT * FROM selected_cosmetics WHERE catId = :catId LIMIT 1")
    fun getSelectionForCat(catId: Int): SelectedCosmetic?

    /** Removes the equipped cosmetic record for the given cat. */
    @Query("DELETE FROM selected_cosmetics WHERE catId = :catId")
    fun clearSelection(catId: Int)
}
