package com.meowrescue.game.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 010 Robolectric tests for the post-v4→v5 schema.
 *
 * Builds the in-memory DB fresh from entity definitions (not from migration SQL).
 * Validates DAO round-trips and NOT NULL enforcement on primary key columns.
 *
 * Migration SQL correctness (the actual DROP/CREATE path) is covered separately
 * by MigrationRealSQLiteTest and MigrationV4V5InstrumentedTest.
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests "com.meowrescue.game.data.MigrationV4V5Test"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationV4V5Test {

    private lateinit var db: AppDatabase
    private lateinit var selectedCosmeticDao: SelectedCosmeticDao
    private lateinit var themeDao: ThemeDao
    private lateinit var cosmeticDao: CosmeticDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        selectedCosmeticDao = db.selectedCosmeticDao()
        themeDao = db.themeDao()
        cosmeticDao = db.cosmeticDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_postMigration_selectedCosmeticsTableExists() {
        selectedCosmeticDao.upsertSelection(SelectedCosmetic(catId = 1, cosmeticId = 7))
        val result = selectedCosmeticDao.getSelectionForCat(1)
        assertEquals(1, result?.catId)
        assertEquals(7, result?.cosmeticId)
    }

    @Test
    fun test_postMigration_selectedCosmeticsPkIsNotNull() {
        // SQLite INTEGER PRIMARY KEY is a rowid alias — inserting NULL auto-assigns a rowid
        // rather than throwing, so raw-SQL constraint enforcement is not testable here.
        // What matters for Room's identity hash is that PRAGMA table_info reports notnull=1.
        // The raw DROP/CREATE SQL path is verified by MigrationRealSQLiteTest and
        // MigrationV4V5InstrumentedTest (real device).
        val cursor = db.openHelper.readableDatabase
            .query("PRAGMA table_info(selected_cosmetics)")
        val notNullMap = mutableMapOf<String, Int>()
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow("name")
            val notNullIdx = it.getColumnIndexOrThrow("notnull")
            while (it.moveToNext()) {
                notNullMap[it.getString(nameIdx)] = it.getInt(notNullIdx)
            }
        }
        assertEquals("catId must be declared NOT NULL for Room hash match", 1, notNullMap["catId"])
        assertEquals("cosmeticId must be declared NOT NULL", 1, notNullMap["cosmeticId"])
    }

    @Test
    fun test_postMigration_themeUnlocksTableExists() {
        themeDao.insertUnlock(ThemeUnlock(themeId = "forest", purchasedAt = 5000L))
        val result = themeDao.getAllUnlocked()
        assertEquals(1, result.size)
        assertEquals("forest", result[0].themeId)
        assertEquals(5000L, result[0].purchasedAt)
    }

    @Test
    fun test_postMigration_themeUnlocksPkIsNotNull() {
        // TEXT PRIMARY KEY columns do enforce NOT NULL for null literal inserts in SQLite,
        // but TEXT PK is not a rowid alias so the constraint path differs from INTEGER PK.
        // We verify the schema declaration (notnull=1) rather than raw insertion behaviour
        // for consistency with the selectedCosmetics test and clarity of intent.
        val cursor = db.openHelper.readableDatabase
            .query("PRAGMA table_info(theme_unlocks)")
        val notNullMap = mutableMapOf<String, Int>()
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow("name")
            val notNullIdx = it.getColumnIndexOrThrow("notnull")
            while (it.moveToNext()) {
                notNullMap[it.getString(nameIdx)] = it.getInt(notNullIdx)
            }
        }
        assertEquals("themeId must be declared NOT NULL for Room hash match", 1, notNullMap["themeId"])
        assertEquals("purchasedAt must be declared NOT NULL", 1, notNullMap["purchasedAt"])
    }

    @Test
    fun test_postMigration_cosmeticPurchasesUnaffected() {
        cosmeticDao.insertPurchase(CosmeticPurchase(catId = 1, type = "PALETTE", purchasedAt = 1000L))
        val result = cosmeticDao.getPurchasesForCat(1)
        assertEquals(1, result.size)
        assertEquals(1, result[0].catId)
        assertEquals("PALETTE", result[0].type)
        assertEquals(1000L, result[0].purchasedAt)
    }
}
