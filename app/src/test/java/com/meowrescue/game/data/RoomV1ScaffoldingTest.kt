package com.meowrescue.game.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Story 001 (T-1-3) integration tests for Room scaffolding and UserProgress DAO.
 *
 * Validates in-memory DB construction, DAO upsert/getById round-trip,
 * missing-row null semantics, and aggregate queries. Uses Robolectric for
 * a real Android context without a device.
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests com.meowrescue.game.data.RoomV1ScaffoldingTest
 *
 * AC-5 (internal visibility) is a compile-time guarantee enforced by Kotlin's
 * module boundary; no runtime assertion is required or possible here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomV1ScaffoldingTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserProgressDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userProgressDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── AC-1: DB construction ─────────────────────────────────────────────

    @Test
    fun test_inMemoryDatabase_buildsSuccessfully_daoAccessible() {
        // Room opens lazily; execute a query to confirm DB is operational
        assertNotNull(db)
        assertTrue(dao.getAllProgress().isEmpty())
    }

    // ── AC-2: saveProgress + getProgressForLevel round-trip ───────────────

    @Test
    fun test_saveProgress_getProgressForLevel_roundTrip() {
        dao.saveProgress(UserProgress(stageId = 42, stars = 3, completed = true, catUnlocked = null, bestScore = 500))
        val result = dao.getProgressForLevel(42)
        assertNotNull(result)
        assertEquals(42, result!!.stageId)
        assertEquals(3, result.stars)
        assertTrue(result.completed)
        assertEquals(500, result.bestScore)
    }

    @Test
    fun test_getProgressForLevel_missingRow_returnsNull() {
        assertNull(dao.getProgressForLevel(999))
    }

    @Test
    fun test_saveProgress_upsert_replacesExistingRow() {
        dao.saveProgress(UserProgress(stageId = 1, stars = 1, completed = true, catUnlocked = null))
        dao.saveProgress(UserProgress(stageId = 1, stars = 3, completed = true, catUnlocked = null))
        val result = dao.getProgressForLevel(1)
        assertEquals(3, result!!.stars)
    }

    // ── AC-3: getMaxCompletedLevel aggregate ──────────────────────────────

    @Test
    fun test_getMaxCompletedLevel_returnsHighestCompletedStageId() {
        dao.saveProgress(UserProgress(stageId = 1,  stars = 3, completed = true,  catUnlocked = null))
        dao.saveProgress(UserProgress(stageId = 5,  stars = 2, completed = true,  catUnlocked = null))
        dao.saveProgress(UserProgress(stageId = 10, stars = 1, completed = true,  catUnlocked = null))
        dao.saveProgress(UserProgress(stageId = 20, stars = 0, completed = false, catUnlocked = null))
        assertEquals(10, dao.getMaxCompletedLevel())
    }

    @Test
    fun test_getMaxCompletedLevel_emptyTable_returnsNull() {
        assertNull(dao.getMaxCompletedLevel())
    }

    @Test
    fun test_getMaxCompletedLevel_onlyIncompleteRows_returnsNull() {
        dao.saveProgress(UserProgress(stageId = 5, stars = 0, completed = false, catUnlocked = null))
        assertNull(dao.getMaxCompletedLevel())
    }

    // ── AC-4: getAll + three-star filter ──────────────────────────────────

    @Test
    fun test_getAllProgress_threeStarFilter_returnsCorrectCount() {
        listOf(
            UserProgress(stageId = 1, stars = 3, completed = true,  catUnlocked = null),
            UserProgress(stageId = 2, stars = 3, completed = true,  catUnlocked = null),
            UserProgress(stageId = 3, stars = 2, completed = true,  catUnlocked = null),
            UserProgress(stageId = 4, stars = 1, completed = true,  catUnlocked = null),
            UserProgress(stageId = 5, stars = 0, completed = false, catUnlocked = null),
        ).forEach { dao.saveProgress(it) }

        val threeStarCount = dao.getAllProgress().count { it.stars >= 3 }
        assertEquals(2, threeStarCount)
    }

    @Test
    fun test_getAllProgress_emptyTable_returnsEmptyList() {
        assertTrue(dao.getAllProgress().isEmpty())
    }

    @Test
    fun test_getAllProgress_allZeroStars_threeStarCountIsZero() {
        dao.saveProgress(UserProgress(stageId = 1, stars = 0, completed = false, catUnlocked = null))
        dao.saveProgress(UserProgress(stageId = 2, stars = 1, completed = true,  catUnlocked = null))
        val threeStarCount = dao.getAllProgress().count { it.stars >= 3 }
        assertEquals(0, threeStarCount)
    }

    // ── AC-2 edge: write-default-value survives round-trip ────────────────

    @Test
    fun test_saveProgress_defaultBestScore_roundTrip() {
        dao.saveProgress(UserProgress(stageId = 7, stars = 2, completed = true, catUnlocked = null))
        val result = dao.getProgressForLevel(7)
        assertEquals(0, result!!.bestScore)
    }
}
