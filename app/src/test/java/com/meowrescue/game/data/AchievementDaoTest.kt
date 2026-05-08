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
 * Story 005 (T-1-7) Logic tests for AchievementDao idempotent unlock.
 *
 * Uses Robolectric + in-memory Room. Tests work against the live v3 schema
 * (achievementId PK, unlockedAt TEXT). AC references map to QA Test Cases.
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests com.meowrescue.game.data.AchievementDaoTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AchievementDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AchievementDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.achievementDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── TOTAL_REWARD compile-time assertion ───────────────────────────────

    @Test
    fun test_achievementDefs_totalReward_equals2180() {
        assertEquals(2180, AchievementDefs.TOTAL_REWARD)
    }

    // ── AC-1: unlock first call returns 1 ─────────────────────────────────

    @Test
    fun test_unlock_firstCall_returnsOne() {
        dao.insert(Achievement(achievementId = "clear_1"))
        val result = dao.unlock("clear_1", "2026-04-20 00:00:00")
        assertEquals(1, result)
    }

    @Test
    fun test_unlock_firstCall_setsUnlockedStateAndTime() {
        dao.insert(Achievement(achievementId = "clear_1"))
        dao.unlock("clear_1", "2026-04-20 12:00:00")
        val row = dao.get("clear_1")
        assertNotNull(row)
        assertTrue(row!!.unlocked)
        assertEquals("2026-04-20 12:00:00", row.unlockedAt)
    }

    // ── AC-2: unlock second call is no-op ─────────────────────────────────

    @Test
    fun test_unlock_secondCall_returnsZero() {
        dao.insert(Achievement(achievementId = "clear_1"))
        dao.unlock("clear_1", "2026-04-20 10:00:00")
        val result = dao.unlock("clear_1", "2026-04-20 11:00:00")
        assertEquals(0, result)
    }

    @Test
    fun test_unlock_secondCall_doesNotOverwriteUnlockedAt() {
        dao.insert(Achievement(achievementId = "clear_1"))
        dao.unlock("clear_1", "2026-04-20 10:00:00")
        dao.unlock("clear_1", "2026-04-20 11:00:00")
        val row = dao.get("clear_1")
        assertEquals("2026-04-20 10:00:00", row!!.unlockedAt)
    }

    // ── AC-3: unlock non-seeded id returns 0 ─────────────────────────────

    @Test
    fun test_unlock_nonSeededId_returnsZero() {
        val result = dao.unlock("nonexistent", "2026-04-20 00:00:00")
        assertEquals(0, result)
    }

    @Test
    fun test_unlock_nonSeededId_rowDoesNotAppear() {
        dao.unlock("nonexistent", "2026-04-20 00:00:00")
        assertNull(dao.get("nonexistent"))
    }

    // ── AC-4: get() returns correct row ──────────────────────────────────

    @Test
    fun test_get_unlockedRow_returnsCorrectState() {
        dao.insert(Achievement(achievementId = "cat_3"))
        dao.unlock("cat_3", "2026-04-20 09:00:00")
        val row = dao.get("cat_3")
        assertNotNull(row)
        assertTrue(row!!.unlocked)
    }

    @Test
    fun test_get_lockedRow_returnsUnlockedFalse() {
        dao.insert(Achievement(achievementId = "cat_3"))
        val row = dao.get("cat_3")
        assertNotNull(row)
        assertFalse(row!!.unlocked)
    }

    @Test
    fun test_get_missingRow_returnsNull() {
        assertNull(dao.get("missing"))
    }

    // ── AC-5: getUnlocked() filters correctly ────────────────────────────

    @Test
    fun test_getUnlocked_returnsOnlyUnlockedRows() {
        dao.insert(Achievement(achievementId = "clear_1"))
        dao.insert(Achievement(achievementId = "clear_10"))
        dao.insert(Achievement(achievementId = "clear_30"))
        dao.unlock("clear_1",  "2026-04-20 08:00:00")
        dao.unlock("clear_10", "2026-04-20 09:00:00")

        val unlocked = dao.getUnlocked()
        assertEquals(2, unlocked.size)
        assertTrue(unlocked.all { it.unlocked })
        assertFalse(unlocked.any { it.achievementId == "clear_30" })
    }

    @Test
    fun test_getUnlocked_emptyTable_returnsEmpty() {
        assertTrue(dao.getUnlocked().isEmpty())
    }

    // ── AC-7: getUnlockedCount() ──────────────────────────────────────────

    @Test
    fun test_getUnlockedCount_matchesGetUnlockedSize() {
        dao.insert(Achievement(achievementId = "clear_1"))
        dao.insert(Achievement(achievementId = "clear_10"))
        dao.insert(Achievement(achievementId = "clear_30"))
        dao.insert(Achievement(achievementId = "clear_60"))
        dao.unlock("clear_1",  "2026-04-20 08:00:00")
        dao.unlock("clear_10", "2026-04-20 09:00:00")
        dao.unlock("clear_30", "2026-04-20 10:00:00")

        assertEquals(dao.getUnlocked().size, dao.getUnlockedCount())
    }

    @Test
    fun test_getUnlockedCount_emptyTable_returnsZero() {
        assertEquals(0, dao.getUnlockedCount())
    }

    // ── AC-6: insert IGNORE does not reset unlocked state ────────────────

    @Test
    fun test_insert_ignore_doesNotResetUnlockedState() {
        dao.insert(Achievement(achievementId = "clear_1"))
        dao.unlock("clear_1", "2026-04-20 10:00:00")
        dao.insert(Achievement(achievementId = "clear_1"))  // re-seed with IGNORE
        assertTrue(dao.get("clear_1")!!.unlocked)
    }
}
