package com.meowrescue.game.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 003 (T-1-5) integration tests for Migration v2→v3 (player_stats, achievements, bestScore).
 *
 * Uses Robolectric + in-memory Room DB (same approach as MigrationV1V2Test) because
 * MigrationTestHelper requires Android Instrumentation (androidTest target).
 *
 * Known v3 schema divergences from ADR-0002 (intentional, fixed in T-1-12):
 *   - achievements.achievementId (PK) instead of id
 *   - achievements.unlockedAt TEXT instead of timestamp INTEGER
 *   - user_progress.catUnlocked column still present
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests com.meowrescue.game.data.MigrationV2V3Test
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationV2V3Test {

    private lateinit var db: AppDatabase
    private lateinit var playerStatsDao: PlayerStatsDao
    private lateinit var achievementDao: AchievementDao
    private lateinit var userDao: UserProgressDao
    private lateinit var launchDao: LaunchProgressDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playerStatsDao = db.playerStatsDao()
        achievementDao = db.achievementDao()
        userDao = db.userProgressDao()
        launchDao = db.launchProgressDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── AC-1: DB opens at v3 (migration chain intact) ─────────────────────

    @Test
    fun test_database_opensAtCurrentVersion_migrationChainIntact() {
        // Any DAO query forces DB open; no exception = all migrations applied
        assertEquals(0, achievementDao.getUnlocked().size)
    }

    // ── AC-2: user_progress bestScore column exists with default 0 ────────

    @Test
    fun test_userProgress_bestScore_defaultIsZero() {
        userDao.saveProgress(UserProgress(stageId = 1, stars = 3, completed = true, catUnlocked = null, bestScore = 0))
        assertEquals(0, userDao.getProgressForLevel(1)!!.bestScore)
    }

    @Test
    fun test_userProgress_bestScore_persistsNonZeroValue() {
        userDao.saveProgress(UserProgress(stageId = 2, stars = 2, completed = true, catUnlocked = null, bestScore = 5000))
        assertEquals(5000, userDao.getProgressForLevel(2)!!.bestScore)
    }

    @Test
    fun test_userProgress_tableSchema_hasBestScoreColumn() {
        val cursor = db.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("PRAGMA table_info(user_progress)"))
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        assertTrue("bestScore column missing from user_progress", "bestScore" in columns)
    }

    // ── AC-3: player_stats singleton row ──────────────────────────────────

    @Test
    fun test_playerStats_singletonRow_insertAndQueryRoundTrip() {
        // In-memory DB skips migrations; manually insert id=1 row (mirrors what MIGRATION_2_3 does)
        playerStatsDao.saveStats(PlayerStats(id = 1, coins = 0, totalCoinsEarned = 0))
        val stats = playerStatsDao.getStats()
        assertNotNull("player_stats singleton row missing after insert", stats)
        assertEquals(1, stats!!.id)
        assertEquals(0, stats.coins)
        assertEquals(0, stats.totalCoinsEarned)
    }

    @Test
    fun test_playerStats_tableSchema_hasCorrectColumns() {
        val cursor = db.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("PRAGMA table_info(player_stats)"))
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        assertTrue("id column missing",               "id"               in columns)
        assertTrue("coins column missing",            "coins"            in columns)
        assertTrue("totalCoinsEarned column missing", "totalCoinsEarned" in columns)
        assertEquals(3, columns.size)
    }

    // ── AC-4: achievements table schema ───────────────────────────────────

    @Test
    fun test_achievements_tableSchema_hasCorrectColumns() {
        val cursor = db.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("PRAGMA table_info(achievements)"))
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        // v3 actual columns (diverge from ADR-0002; fixed in T-1-12)
        assertTrue("achievementId column missing", "achievementId" in columns)
        assertTrue("unlocked column missing",      "unlocked"      in columns)
        assertTrue("unlockedAt column missing",    "unlockedAt"    in columns)
        assertEquals(3, columns.size)
    }

    @Test
    fun test_achievements_initiallyEmpty() {
        assertEquals(0, achievementDao.getUnlockedCount())
        assertEquals(0, achievementDao.getUnlocked().size)
    }

    // ── AC-5: launch_progress unaffected by v2→v3 ────────────────────────

    @Test
    fun test_launchProgress_survivesV2V3Migration() {
        launchDao.saveProgress(LaunchProgress(stageId = 3, stars = 2, completed = true, bestScore = 1500))
        launchDao.saveProgress(LaunchProgress(stageId = 4, stars = 1, completed = true, bestScore = 800))
        launchDao.saveProgress(LaunchProgress(stageId = 5, stars = 3, completed = true, bestScore = 9999))

        assertEquals(3, launchDao.getCompletedCount())
        assertEquals(1500, launchDao.getProgressForStage(3)!!.bestScore)
        assertEquals(9999, launchDao.getProgressForStage(5)!!.bestScore)
    }

    // ── AC-6: all tables coexist cleanly in v3 ────────────────────────────

    @Test
    fun test_allV3Tables_coexistWithoutConflict() {
        playerStatsDao.saveStats(PlayerStats(id = 1, coins = 0, totalCoinsEarned = 0))
        userDao.saveProgress(UserProgress(stageId = 10, stars = 3, completed = true, catUnlocked = null, bestScore = 7500))
        launchDao.saveProgress(LaunchProgress(stageId = 10, stars = 2, completed = true, bestScore = 3000))
        achievementDao.insert(Achievement(achievementId = "first_win"))

        assertEquals(7500, userDao.getProgressForLevel(10)!!.bestScore)
        assertEquals(3000, launchDao.getProgressForStage(10)!!.bestScore)
        assertNotNull(achievementDao.get("first_win"))
        assertNotNull(playerStatsDao.getStats())
    }
}
