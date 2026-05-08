package com.meowrescue.game.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Real-SQLite migration SQL validation tests.
 *
 * Unlike MigrationV3V4Test (Robolectric + inMemoryDatabaseBuilder built fresh from entities),
 * these tests execute the actual CREATE TABLE SQL inside each Migration object via a real
 * Robolectric SQLite connection, then verify the resulting schema with PRAGMA table_info.
 *
 * This is the test layer that would have caught the v3→v4 inline PRIMARY KEY / NOT NULL
 * mismatch before it shipped — PRAGMA table_info returns notnull=0 for inline PKs, which
 * is exactly what caused the Room identity hash mismatch and startup crash.
 *
 * See coding-standards.md §"Room Migration Test Standard" for the rationale.
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests "com.meowrescue.game.data.MigrationRealSQLiteTest"
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class MigrationRealSQLiteTest {

    private fun openInMemoryDb(): SupportSQLiteDatabase {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(null) // in-memory
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {}
                })
                .build()
        )
        return helper.writableDatabase
    }

    /** Returns a map of columnName → notnull (1 = NOT NULL, 0 = nullable) for a given table. */
    private fun pragmaNotNull(db: SupportSQLiteDatabase, table: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val cursor = db.query("PRAGMA table_info($table)")
        cursor.use {
            val nameIdx = it.getColumnIndex("name")
            val notNullIdx = it.getColumnIndex("notnull")
            while (it.moveToNext()) {
                result[it.getString(nameIdx)] = it.getInt(notNullIdx)
            }
        }
        return result
    }

    /** Returns the set of column names that form the PRIMARY KEY for a given table. */
    private fun pragmaPk(db: SupportSQLiteDatabase, table: String): Set<String> {
        val result = mutableSetOf<String>()
        val cursor = db.query("PRAGMA table_info($table)")
        cursor.use {
            val nameIdx = it.getColumnIndex("name")
            val pkIdx = it.getColumnIndex("pk")
            while (it.moveToNext()) {
                if (it.getInt(pkIdx) > 0) result.add(it.getString(nameIdx))
            }
        }
        return result
    }

    // ── MIGRATION_3_4 ────────────────────────────────────────────────────────

    @Test
    fun migrate3to4_cosmeticPurchases_columnsAreNotNull() {
        val db = openInMemoryDb()
        MIGRATION_3_4.migrate(db)
        val cols = pragmaNotNull(db, "cosmetic_purchases")
        assertEquals("catId must be NOT NULL", 1, cols["catId"])
        assertEquals("type must be NOT NULL", 1, cols["type"])
        assertEquals("purchasedAt must be NOT NULL", 1, cols["purchasedAt"])
    }

    @Test
    fun migrate3to4_cosmeticPurchases_compositePkIsCorrect() {
        val db = openInMemoryDb()
        MIGRATION_3_4.migrate(db)
        val pk = pragmaPk(db, "cosmetic_purchases")
        assertTrue("PK must contain catId", pk.contains("catId"))
        assertTrue("PK must contain type", pk.contains("type"))
        assertEquals("PK must have exactly 2 columns", 2, pk.size)
    }

    @Test
    fun migrate3to4_selectedCosmetics_pkColumnIsNotNull() {
        // Regression guard for the v3→v4 bug: inline `INTEGER PRIMARY KEY` returned notnull=0.
        // Room's entity marks catId as notNull=true → identity hash mismatch → startup crash.
        val db = openInMemoryDb()
        MIGRATION_3_4.migrate(db)
        val cols = pragmaNotNull(db, "selected_cosmetics")
        assertEquals("catId (PK) must be NOT NULL — inline PK without NOT NULL causes Room hash mismatch", 1, cols["catId"])
        assertEquals("cosmeticId must be NOT NULL", 1, cols["cosmeticId"])
    }

    @Test
    fun migrate3to4_themeUnlocks_pkColumnIsNotNull() {
        // Regression guard: same inline PK bug as selected_cosmetics.
        val db = openInMemoryDb()
        MIGRATION_3_4.migrate(db)
        val cols = pragmaNotNull(db, "theme_unlocks")
        assertEquals("themeId (PK) must be NOT NULL — inline PK without NOT NULL causes Room hash mismatch", 1, cols["themeId"])
        assertEquals("purchasedAt must be NOT NULL", 1, cols["purchasedAt"])
    }

    // ── MIGRATION_4_5 ────────────────────────────────────────────────────────

    @Test
    fun migrate4to5_selectedCosmetics_droppedAndRecreatedCorrectly() {
        val db = openInMemoryDb()
        // Simulate a device with the broken v4 schema (inline PK, notnull=0).
        db.execSQL("CREATE TABLE selected_cosmetics (catId INTEGER PRIMARY KEY, cosmeticId INTEGER NOT NULL)")
        val brokenCols = pragmaNotNull(db, "selected_cosmetics")
        assertEquals("Precondition: broken v4 has notnull=0 on catId", 0, brokenCols["catId"])

        MIGRATION_4_5.migrate(db)

        val fixedCols = pragmaNotNull(db, "selected_cosmetics")
        assertEquals("After recovery: catId must be NOT NULL", 1, fixedCols["catId"])
        assertEquals("After recovery: cosmeticId must be NOT NULL", 1, fixedCols["cosmeticId"])
    }

    @Test
    fun migrate4to5_themeUnlocks_droppedAndRecreatedCorrectly() {
        val db = openInMemoryDb()
        db.execSQL("CREATE TABLE theme_unlocks (themeId TEXT PRIMARY KEY, purchasedAt INTEGER NOT NULL)")
        val brokenCols = pragmaNotNull(db, "theme_unlocks")
        assertEquals("Precondition: broken v4 has notnull=0 on themeId", 0, brokenCols["themeId"])

        MIGRATION_4_5.migrate(db)

        val fixedCols = pragmaNotNull(db, "theme_unlocks")
        assertEquals("After recovery: themeId must be NOT NULL", 1, fixedCols["themeId"])
        assertEquals("After recovery: purchasedAt must be NOT NULL", 1, fixedCols["purchasedAt"])
    }

    @Test
    fun migrate4to5_cosmeticPurchases_untouched() {
        val db = openInMemoryDb()
        MIGRATION_3_4.migrate(db) // set up correct v4 tables
        val beforeCols = pragmaNotNull(db, "cosmetic_purchases")

        MIGRATION_4_5.migrate(db)

        val afterCols = pragmaNotNull(db, "cosmetic_purchases")
        assertEquals("cosmetic_purchases must be unchanged by MIGRATION_4_5", beforeCols, afterCols)
    }
}
