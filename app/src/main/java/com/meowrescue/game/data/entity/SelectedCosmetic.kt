package com.meowrescue.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks which cosmetic is currently equipped on a cat.
 *
 * One row per cat — upserted (REPLACE) on equip, deleted on unequip.
 * Never appears in a public [GameRepository] return type, so this type stays `internal`.
 *
 * Usage:
 * ```kotlin
 * val selection = SelectedCosmetic(catId = 3, cosmeticId = 7)
 * ```
 */
@Entity(tableName = "selected_cosmetics")
internal data class SelectedCosmetic(
    /** The cat whose active cosmetic selection this row tracks. */
    @PrimaryKey val catId: Int,
    /** ID of the cosmetic currently equipped on this cat. */
    val cosmeticId: Int
)
