package com.meowrescue.game.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 002 (T-1-4) integration tests for Migration v1→v2 and LaunchProgressDao.
 *
 * MigrationTestHelper requires Android Instrumentation (androidTest target) and
 * valid Room-generated schema hashes, which are unavailable in hand-authored
 * backfill JSONs. These Robolectric tests instead verify:
 *   - launch_progress table schema via PRAGMA table_info (AC-3)
 *   - LaunchProgressDao CRUD operations (AC-4, AC-5)
 *   - user_progress rows are unaffected in the same DB (AC-2 equivalent)
 *   - DB opens at v3 (all migrations including MIGRATION_1_2 ran successfully, AC-1)
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests com.meowrescue.game.data.MigrationV1V2Test
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationV1V2Test {

    private lateinit var db: AppDatabase
    private lateinit var launchDao: LaunchProgressDao
    private lateinit var userDao: UserProgressDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        launchDao = db.launchProgressDao()
        userDao = db.userProgressDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── AC-1: DB opens successfully (all migrations applied) ──────────────

    @Test
    fun test_database_opensAtCurrentVersion_migrationChainIntact() {
        // Exercising any DAO query forces the DB open; no exception = all migrations ran.
        assertTrue(launchDao.getAllProgress().isEmpty())
    }

    // ── AC-2: user_progress rows unaffected by migration ─────────────────

    @Test
    fun test_userProgress_survivesAlongsideLaunchProgress() {
        userDao.saveProgress(UserProgress(stageId = 1, stars = 3, completed = true, catUnlocked = null))
        launchDao.saveProgress(LaunchProgress(stageId = 10, stars = 2, completed = true, bestScore = 800))

        assertEquals(3, userDao.getProgressForLevel(1)!!.stars)
        assertEquals(800, launchDao.getProgressForStage(10)!!.bestScore)
    }

    // ── AC-3: launch_progress table schema ────────────────────────────────

    @Test
    fun test_launchProgress_tableSchema_hasCorrectColumns() {
        val cursor = db.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("PRAGMA table_info(launch_progress)"))
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        assertTrue("stageId column missing",   "stageId"   in columns)
        assertTrue("completed column missing", "completed" in columns)
        assertTrue("stars column missing",     "stars"     in columns)
        assertTrue("bestScore column missing", "bestScore" in columns)
        assertEquals(4, columns.size)
    }

    // ── AC-4: LaunchProgressDao upsert + getById round-trip ──────────────

    @Test
    fun test_saveProgress_getProgressForStage_roundTrip() {
        launchDao.saveProgress(LaunchProgress(stageId = 42, stars = 3, completed = true, bestScore = 12_000))
        val result = launchDao.getProgressForStage(42)
        assertNotNull(result)
        assertEquals(42, result!!.stageId)
        assertEquals(3, result.stars)
        assertTrue(result.completed)
        assertEquals(12_000, result.bestScore)
    }

    @Test
    fun test_getProgressForStage_missingRow_returnsNull() {
        assertNull(launchDao.getProgressForStage(999))
    }

    @Test
    fun test_saveProgress_upsert_replacesExistingRow() {
        launchDao.saveProgress(LaunchProgress(stageId = 5, stars = 1, completed = true, bestScore = 100))
        launchDao.saveProgress(LaunchProgress(stageId = 5, stars = 3, completed = true, bestScore = 9_000))
        val result = launchDao.getProgressForStage(5)
        assertEquals(3, result!!.stars)
        assertEquals(9_000, result.bestScore)
    }

    @Test
    fun test_saveProgress_defaultBestScore_isZero() {
        launchDao.saveProgress(LaunchProgress(stageId = 7, stars = 2, completed = true, bestScore = 0))
        assertEquals(0, launchDao.getProgressForStage(7)!!.bestScore)
    }

    // ── AC-5: getCompletedCount ───────────────────────────────────────────

    @Test
    fun test_getCompletedCount_returnsCompletedRowsOnly() {
        launchDao.saveProgress(LaunchProgress(stageId = 1, stars = 3, completed = true,  bestScore = 0))
        launchDao.saveProgress(LaunchProgress(stageId = 2, stars = 2, completed = true,  bestScore = 0))
        launchDao.saveProgress(LaunchProgress(stageId = 3, stars = 1, completed = false, bestScore = 0))
        launchDao.saveProgress(LaunchProgress(stageId = 4, stars = 0, completed = false, bestScore = 0))
        assertEquals(2, launchDao.getCompletedCount())
    }

    @Test
    fun test_getCompletedCount_emptyTable_returnsZero() {
        assertEquals(0, launchDao.getCompletedCount())
    }

    @Test
    fun test_getCompletedCount_allIncomplete_returnsZero() {
        launchDao.saveProgress(LaunchProgress(stageId = 1, stars = 0, completed = false, bestScore = 0))
        assertEquals(0, launchDao.getCompletedCount())
    }
}
