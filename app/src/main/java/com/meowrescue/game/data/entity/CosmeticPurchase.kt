package com.meowrescue.game.data

import androidx.room.Entity

/**
 * Represents a purchased cosmetic item for a specific cat.
 *
 * This entity uses a composite primary key ([catId], [type]) because a single cat
 * can own one PALETTE and one ACCESSORY slot independently.
 *
 * Visibility: `public` (no modifier) — returned from the public
 * [GameRepository.getCosmeticPurchasesForCat] method. In this single-module project,
 * a public function cannot return an internal type. This is an intentional exception
 * to the ADR-0006 `internal`-by-default rule for entity types.
 *
 * Usage:
 * ```kotlin
 * val purchase = CosmeticPurchase(catId = 3, type = "PALETTE", purchasedAt = System.currentTimeMillis())
 * ```
 */
@Entity(tableName = "cosmetic_purchases", primaryKeys = ["catId", "type"])
data class CosmeticPurchase(
    /** The cat this cosmetic belongs to. */
    val catId: Int,
    /** Cosmetic slot type — either `"PALETTE"` or `"ACCESSORY"`. */
    val type: String,
    /** UTC epoch milliseconds when the cosmetic was purchased. */
    val purchasedAt: Long
)
