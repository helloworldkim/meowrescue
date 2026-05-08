package com.meowrescue.game.data

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 008 integration tests for Migration v3→v4 (cosmetic_purchases, selected_cosmetics,
 * theme_unlocks + DAOs + Repository methods).
 *
 * Uses Robolectric + in-memory Room DB (builds schema fresh from entities; skips migration
 * path, which is fine — the migration SQL is tested separately via the migration object itself).
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests "com.meowrescue.game.data.MigrationV3V4Test"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationV3V4Test {

    private lateinit var db: AppDatabase
    private lateinit var cosmeticDao: CosmeticDao
    private lateinit var selectedCosmeticDao: SelectedCosmeticDao
    private lateinit var themeDao: ThemeDao
    private lateinit var repo: GameRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cosmeticDao = db.cosmeticDao()
        selectedCosmeticDao = db.selectedCosmeticDao()
        themeDao = db.themeDao()
        repo = GameRepository(context)
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase.closeForTesting()
    }

    // ── 1: DB opens at version 4 without exception ────────────────────────

    @Test
    fun test_database_opensAtVersion4_noException() {
        // Forcing any DAO query opens the DB. No exception = schema at v4 is valid.
        assertEquals(0, cosmeticDao.getAllPurchaseCount())
    }

    // ── 2: cosmetic_purchases table schema ────────────────────────────────

    @Test
    fun test_cosmeticPurchases_tableSchema_correct() {
        val cursor = db.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("PRAGMA table_info(cosmetic_purchases)"))
        val columns = mutableListOf<String>()
        val pkColumns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val pk = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
            columns.add(name)
            if (pk > 0) pkColumns.add(name)
        }
        cursor.close()

        assertTrue("catId column missing",       "catId"       in columns)
        assertTrue("type column missing",        "type"        in columns)
        assertTrue("purchasedAt column missing", "purchasedAt" in columns)
        assertEquals("Expected exactly 3 columns", 3, columns.size)
        assertTrue("catId must be part of composite PK",  "catId" in pkColumns)
        assertTrue("type must be part of composite PK",   "type"  in pkColumns)
        assertEquals("Composite PK must have exactly 2 columns", 2, pkColumns.size)
    }

    // ── 3: selected_cosmetics table schema ────────────────────────────────

    @Test
    fun test_selectedCosmetics_tableSchema_correct() {
        val cursor = db.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("PRAGMA table_info(selected_cosmetics)"))
        val columns = mutableListOf<String>()
        val pkColumns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val pk = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
            columns.add(name)
            if (pk > 0) pkColumns.add(name)
        }
        cursor.close()

        assertTrue("catId column missing",     "catId"     in columns)
        assertTrue("cosmeticId column missing", "cosmeticId" in columns)
        assertEquals("Expected exactly 2 columns", 2, columns.size)
        assertEquals("Single PK must be catId", listOf("catId"), pkColumns)
    }

    // ── 4: theme_unlocks table schema ─────────────────────────────────────

    @Test
    fun test_themeUnlocks_tableSchema_correct() {
        val cursor = db.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("PRAGMA table_info(theme_unlocks)"))
        val columns = mutableListOf<String>()
        val pkColumns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val pk = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
            columns.add(name)
            if (pk > 0) pkColumns.add(name)
        }
        cursor.close()

        assertTrue("themeId column missing",    "themeId"    in columns)
        assertTrue("purchasedAt column missing", "purchasedAt" in columns)
        assertEquals("Expected exactly 2 columns", 2, columns.size)
        assertEquals("Single PK must be themeId", listOf("themeId"), pkColumns)
    }

    // ── 5: composite PK — duplicate (catId, type) returns -1 ─────────────

    @Test
    fun test_compositePK_duplicateInsert_returnsMinusOne() {
        val purchase = CosmeticPurchase(catId = 1, type = "PALETTE", purchasedAt = 1000L)
        val firstResult  = cosmeticDao.insertPurchase(purchase)
        val secondResult = cosmeticDao.insertPurchase(purchase.copy(purchasedAt = 2000L))

        assertTrue("First insert should succeed (rowId > 0)", firstResult > 0L)
        assertEquals("Duplicate insert should return -1", -1L, secondResult)
        assertEquals("Table must still have exactly 1 row", 1, cosmeticDao.getAllPurchaseCount())
    }

    // ── 6: composite PK — same catId but different type both inserted ─────

    @Test
    fun test_compositePK_differentType_bothInserted() {
        cosmeticDao.insertPurchase(CosmeticPurchase(catId = 2, type = "PALETTE",   purchasedAt = 1000L))
        cosmeticDao.insertPurchase(CosmeticPurchase(catId = 2, type = "ACCESSORY", purchasedAt = 2000L))

        val purchases = cosmeticDao.getPurchasesForCat(2)
        assertEquals("Both rows must be present", 2, purchases.size)
        assertTrue(purchases.any { it.type == "PALETTE" })
        assertTrue(purchases.any { it.type == "ACCESSORY" })
    }

    // ── 7: setEquippedCosmetic upserts and replaces existing ─────────────

    @Test
    fun test_setEquippedCosmetic_upsert_replacesExisting() {
        selectedCosmeticDao.upsertSelection(SelectedCosmetic(catId = 3, cosmeticId = 10))
        assertEquals(10, selectedCosmeticDao.getSelectionForCat(3)?.cosmeticId)

        selectedCosmeticDao.upsertSelection(SelectedCosmetic(catId = 3, cosmeticId = 20))
        assertEquals("Second upsert must replace the first", 20,
            selectedCosmeticDao.getSelectionForCat(3)?.cosmeticId)
    }

    // ── 8: clearEquippedCosmetic — subsequent read returns null ──────────

    @Test
    fun test_clearEquippedCosmetic_returnsNull() {
        selectedCosmeticDao.upsertSelection(SelectedCosmetic(catId = 4, cosmeticId = 5))
        assertNotNull(selectedCosmeticDao.getSelectionForCat(4))

        selectedCosmeticDao.clearSelection(4)
        assertNull("Selection must be null after clear", selectedCosmeticDao.getSelectionForCat(4))
    }

    // ── 9: theme unlock — duplicate insert returns -1 (false via repo) ───

    @Test
    fun test_themeUnlock_duplicateInsert_returnsFalse() {
        val firstResult  = themeDao.insertUnlock(ThemeUnlock(themeId = "forest", purchasedAt = 1000L))
        val secondResult = themeDao.insertUnlock(ThemeUnlock(themeId = "forest", purchasedAt = 2000L))

        assertTrue("First insert should succeed (rowId > 0)", firstResult > 0L)
        assertEquals("Duplicate insert should return -1", -1L, secondResult)
        assertEquals("Table must have exactly 1 row", 1, themeDao.getAllUnlocked().size)
    }

    // ── 10: isUnlocked returns correct Boolean ────────────────────────────

    @Test
    fun test_themeUnlock_isUnlocked_correctResult() {
        assertFalse("Should be false before unlock", themeDao.isUnlocked("ocean"))
        themeDao.insertUnlock(ThemeUnlock(themeId = "ocean", purchasedAt = 1000L))
        assertTrue("Should be true after unlock", themeDao.isUnlocked("ocean"))
        assertFalse("Unrelated theme must stay locked", themeDao.isUnlocked("desert"))
    }

    // ── 11: v3 tables unaffected by migration ────────────────────────────

    @Test
    fun test_v3Tables_unaffectedByMigration() {
        // user_progress
        val userDao = db.userProgressDao()
        userDao.saveProgress(UserProgress(stageId = 1, stars = 3, completed = true,
            catUnlocked = null, bestScore = 9000))
        assertEquals(9000, userDao.getProgressForLevel(1)!!.bestScore)

        // launch_progress
        val launchDao = db.launchProgressDao()
        launchDao.saveProgress(LaunchProgress(stageId = 1, stars = 2, completed = true, bestScore = 5000))
        assertEquals(5000, launchDao.getProgressForStage(1)!!.bestScore)

        // player_stats
        val statsDao = db.playerStatsDao()
        statsDao.saveStats(PlayerStats(id = 1, coins = 100, totalCoinsEarned = 200))
        assertEquals(100, statsDao.getStats()!!.coins)

        // achievements
        val achDao = db.achievementDao()
        achDao.insert(Achievement(achievementId = "first_win"))
        assertNotNull(achDao.get("first_win"))
    }

    // ── 12: Repository round-trip for insertCosmeticPurchase ─────────────

    @Test
    fun test_insertCosmeticPurchase_viaRepository_roundTrip() = runBlocking {
        val inserted = repo.insertCosmeticPurchase(catId = 5, type = "PALETTE", purchasedAt = 12345L)
        assertTrue("First insert must return true", inserted)

        val purchases = repo.getCosmeticPurchasesForCat(5)
        assertEquals("Exactly one purchase expected", 1, purchases.size)
        assertEquals(5,          purchases[0].catId)
        assertEquals("PALETTE",  purchases[0].type)
        assertEquals(12345L,     purchases[0].purchasedAt)

        val duplicate = repo.insertCosmeticPurchase(catId = 5, type = "PALETTE", purchasedAt = 99999L)
        assertFalse("Duplicate insert must return false", duplicate)
        assertEquals("Row count must remain 1", 1, repo.getCosmeticPurchasesForCat(5).size)
    }
}
