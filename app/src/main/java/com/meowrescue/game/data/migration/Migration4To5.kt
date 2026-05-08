package com.meowrescue.game.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Recovery migration for devices that ran the original (broken) MIGRATION_3_4.
 *
 * The original MIGRATION_3_4 created `selected_cosmetics` and `theme_unlocks` with
 * inline PRIMARY KEY syntax (e.g. `catId INTEGER PRIMARY KEY`) which SQLite's
 * PRAGMA table_info returns as notnull=0. Room's entity schema marks both columns
 * notNull=true, causing an identity hash mismatch and IllegalStateException on DB open.
 *
 * This migration drops and recreates both affected tables with explicit NOT NULL and
 * table-level PRIMARY KEY constraints, matching Room's generated createSql exactly.
 * Data loss is acceptable — these tables were new in v4 and contain no real user data.
 *
 * `cosmetic_purchases` is NOT touched — its original MIGRATION_3_4 SQL was correct.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS selected_cosmetics")
        db.execSQL(
            "CREATE TABLE selected_cosmetics (" +
                "catId INTEGER NOT NULL, " +
                "cosmeticId INTEGER NOT NULL, " +
                "PRIMARY KEY(catId)" +
                ")"
        )

        db.execSQL("DROP TABLE IF EXISTS theme_unlocks")
        db.execSQL(
            "CREATE TABLE theme_unlocks (" +
                "themeId TEXT NOT NULL, " +
                "purchasedAt INTEGER NOT NULL, " +
                "PRIMARY KEY(themeId)" +
                ")"
        )
    }
}
