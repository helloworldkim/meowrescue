package com.meowrescue.game.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from database version 3 to version 4.
 *
 * Adds three new tables supporting the cosmetic and theme economy expansion
 * (GDD TR-expand-002, TR-expand-004 / ADR-0009):
 *
 * - `cosmetic_purchases` — composite PK (catId, type); one row per cat × slot
 * - `selected_cosmetics` — single PK (catId); tracks currently equipped cosmetic
 * - `theme_unlocks`      — single PK (themeId); records purchased grid themes
 *
 * All three additions are purely additive. No existing columns are dropped or altered.
 *
 * Usage:
 * ```kotlin
 * Room.databaseBuilder(context, AppDatabase::class.java, "meow_rescue_db")
 *     .addMigrations(MIGRATION_3_4)
 *     .build()
 * ```
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE cosmetic_purchases (" +
                "catId INTEGER NOT NULL, " +
                "type TEXT NOT NULL, " +
                "purchasedAt INTEGER NOT NULL, " +
                "PRIMARY KEY (catId, type)" +
                ")"
        )
        db.execSQL(
            "CREATE TABLE selected_cosmetics (" +
                "catId INTEGER NOT NULL, " +
                "cosmeticId INTEGER NOT NULL, " +
                "PRIMARY KEY(catId)" +
                ")"
        )
        db.execSQL(
            "CREATE TABLE theme_unlocks (" +
                "themeId TEXT NOT NULL, " +
                "purchasedAt INTEGER NOT NULL, " +
                "PRIMARY KEY(themeId)" +
                ")"
        )
    }
}
