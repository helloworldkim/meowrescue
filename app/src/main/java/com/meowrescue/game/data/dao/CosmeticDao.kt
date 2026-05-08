package com.meowrescue.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for [CosmeticPurchase] — tracks which cosmetic items a cat has purchased.
 *
 * All methods are synchronous (called from [GameRepository] inside `withContext(Dispatchers.IO)`).
 * This interface is `internal` per ADR-0006 — access exclusively through [GameRepository].
 */
@Dao
internal interface CosmeticDao {

    /**
     * Inserts a cosmetic purchase record.
     * Ignores duplicate (catId, type) pairs — a purchase is idempotent.
     * @return the row ID of the inserted row, or -1 if the row already existed.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPurchase(p: CosmeticPurchase): Long

    /**
     * Returns all cosmetic purchases for the given cat.
     * Returns an empty list if the cat has no purchases.
     */
    @Query("SELECT * FROM cosmetic_purchases WHERE catId = :catId")
    fun getPurchasesForCat(catId: Int): List<CosmeticPurchase>

    /** Returns the total number of cosmetic purchase rows across all cats. */
    @Query("SELECT COUNT(*) FROM cosmetic_purchases")
    fun getAllPurchaseCount(): Int
}
