package com.meowrescue.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records a purchased grid theme unlock.
 *
 * Visibility: `public` (no modifier) — returned from the public
 * [GameRepository.getAllThemeUnlocks] method. In this single-module project,
 * a public function cannot return an internal type. This is an intentional exception
 * to the ADR-0006 `internal`-by-default rule for entity types.
 *
 * Usage:
 * ```kotlin
 * val unlock = ThemeUnlock(themeId = "forest", purchasedAt = System.currentTimeMillis())
 * ```
 */
@Entity(tableName = "theme_unlocks")
data class ThemeUnlock(
    /** Stable string identifier for the theme (e.g. `"forest"`, `"ocean"`). */
    @PrimaryKey val themeId: String,
    /** UTC epoch milliseconds when the theme was purchased. */
    val purchasedAt: Long
)
