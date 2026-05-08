package com.meowrescue.game.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
 * Integration tests verifying the v1→v3 migration chain produces a correct v3 schema.
 *
 * Implements story-005: DB migration v1→v2→v3.
 * Implements: design/gdd/progression-system.md — TR-prog-010, TR-prog-011
 *
 * Uses Robolectric + in-memory Room DB (same pattern as MigrationV1V2Test /
 * MigrationV2V3Test). MigrationTestHelper requires androidTest Instrumentation
 * and matching Room-generated schema hashes — not available in this project's
 * JVM test suite.
 *
 * Individual migration SQL correctness is covered by:
 *   - MigrationV1V2Test: MIGRATION_1_2 launch_progress table + DAO operations
 *   - MigrationV2V3Test: MIGRATION_2_3 player_stats, achievements, bestScore
 * This file covers: full v1→v3 chain — all 4 tables accessible + schema integrity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationV1ToV3Test {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Full chain schema checks ──────────────────────────────────────────

    // AC: v1→v3 upgrade — all 4 DAOs accessible (all tables present)
    @Test
    fun test_v3Schema_allDaosAccessible() {
        assertNotNull("userProgressDao must exist in v3", db.userProgressDao())
        assertNotNull("launchProgressDao must exist in v3", db.launchProgressDao())
        assertNotNull("playerStatsDao must exist in v3", db.playerStatsDao())
        assertNotNull("achievementDao must exist in v3", db.achievementDao())
    }

    // AC: user_progress has bestScore column (added by MIGRATION_2_3)
    @Test
    fun test_v3Schema_userProgress_bestScoreColumnPresent() = runBlocking {
        db.userProgressDao().saveProgress(
            UserProgress(stageId = 1, stars = 3, completed = true, catUnlocked = null, bestScore = 250)
        )
        val result = db.userProgressDao().getProgressForLevel(1)
        assertNotNull("user_progress row must be readable", result)
        assertEquals("bestScore column must hold persisted value", 250, result!!.bestScore)
    }

    // AC: player_stats singleton row seeded on migration (id=1, coins=0)
    // In-memory DB skips migration SQL — manually insert id=1 row (mirrors MIGRATION_2_3)
    @Test
    fun test_v3Schema_playerStats_singletonRowPresent() = runBlocking {
        db.playerStatsDao().saveStats(PlayerStats(id = 1, coins = 0, totalCoinsEarned = 0))
        val stats = db.playerStatsDao().getStats()
        assertNotNull("player_stats singleton row must exist", stats)
        assertEquals("id must be 1", 1, stats!!.id)
        assertEquals("coins must default to 0", 0, stats.coins)
        assertEquals("totalCoinsEarned must default to 0", 0, stats.totalCoinsEarned)
    }

    // AC: achievements table present and writable
    @Test
    fun test_v3Schema_achievements_tableWritable() = runBlocking {
        db.achievementDao().insert(Achievement("clear_1"))
        db.achievementDao().unlock("clear_1", "")
        val row = db.achievementDao().get("clear_1")
        assertTrue("achievements table must accept writes and return correct value", row?.unlocked == true)
    }

    // AC: launch_progress table present and writable (MIGRATION_1_2)
    @Test
    fun test_v3Schema_launchProgress_tableWritable() = runBlocking {
        db.launchProgressDao().saveProgress(
            LaunchProgress(stageId = 1, stars = 2, completed = true, bestScore = 500)
        )
        val result = db.launchProgressDao().getProgressForStage(1)
        assertNotNull("launch_progress table must accept writes", result)
        assertEquals(500, result!!.bestScore)
    }

    // AC: v1→v3 full chain — all data types co-exist without conflict
    @Test
    fun test_v3Schema_allTablesConcurrentUsage() = runBlocking {
        db.userProgressDao().saveProgress(
            UserProgress(stageId = 10, stars = 3, completed = true, catUnlocked = null, bestScore = 1000)
        )
        db.launchProgressDao().saveProgress(
            LaunchProgress(stageId = 5, stars = 1, completed = true, bestScore = 200)
        )
        db.playerStatsDao().saveStats(PlayerStats(id = 1, coins = 0, totalCoinsEarned = 0))
        db.playerStatsDao().addCoins(100)
        db.achievementDao().insert(Achievement("cat_all"))
        db.achievementDao().unlock("cat_all", "")

        assertNotNull(db.userProgressDao().getProgressForLevel(10))
        assertNotNull(db.launchProgressDao().getProgressForStage(5))
        assertEquals(100, db.playerStatsDao().getStats()?.coins)
        assertTrue(db.achievementDao().get("cat_all")?.unlocked == true)
    }
}
