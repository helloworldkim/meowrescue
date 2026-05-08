package com.meowrescue.game.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 007 (T-1-9) integration tests for GameRepository facade.
 *
 * Uses Robolectric context; GameRepository creates its own in-process Room DB
 * (AppDatabase.getInstance). Tests use only repo.* methods — no raw DAO access —
 * so both always target the same database instance.
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests com.meowrescue.game.data.GameRepositoryTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GameRepositoryTest {

    private lateinit var repo: GameRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repo = GameRepository(context)
    }

    @After
    fun tearDown() {
        AppDatabase.closeForTesting()
    }

    // ── AC-1: addCoins happy path ──────────────────────────────────────────

    @Test
    fun test_addCoins_increasesBalance() = runTest {
        repo.addCoins(50)
        assertEquals(50, repo.getCoins())
    }

    @Test
    fun test_addCoins_increasesTotalEarned() = runTest {
        repo.addCoins(30)
        repo.addCoins(20)
        assertEquals(50, repo.getTotalCoinsEarned())
    }

    // ── AC-2: coin guards throw on non-positive ────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun test_addCoins_zeroAmount_throws() = runTest {
        repo.addCoins(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_addCoins_negativeAmount_throws() = runTest {
        repo.addCoins(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_spendCoins_zeroAmount_throws() = runTest {
        repo.spendCoins(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_refundCoins_zeroAmount_throws() = runTest {
        repo.refundCoins(0)
    }

    // ── AC-3: spendCoins boolean conversion ──────────────────────────────

    @Test
    fun test_spendCoins_sufficientBalance_returnsTrue() = runTest {
        repo.addCoins(50)
        assertTrue(repo.spendCoins(40))
        assertEquals(10, repo.getCoins())
    }

    @Test
    fun test_spendCoins_insufficientBalance_returnsFalse() = runTest {
        repo.addCoins(20)
        assertFalse(repo.spendCoins(50))
        assertEquals(20, repo.getCoins())
    }

    @Test
    fun test_refundCoins_doesNotIncreaseTotalEarned() = runTest {
        repo.addCoins(50)
        repo.spendCoins(30)
        val totalBefore = repo.getTotalCoinsEarned()
        repo.refundCoins(20)
        assertEquals(totalBefore, repo.getTotalCoinsEarned())
    }

    // ── AC-5: saveProgress first-clear detection ──────────────────────────

    @Test
    fun test_saveProgress_firstClear_returnsTrue() = runTest {
        assertTrue(repo.saveProgress(stageId = 5, stars = 2, score = 100))
    }

    @Test
    fun test_saveProgress_secondClear_returnsFalse() = runTest {
        repo.saveProgress(stageId = 5, stars = 2, score = 100)
        assertFalse(repo.saveProgress(stageId = 5, stars = 3, score = 200))
    }

    @Test
    fun test_saveProgress_storesRowCorrectly() = runTest {
        repo.saveProgress(stageId = 3, stars = 2, score = 500)
        val row = repo.getProgress(3)
        assertNotNull(row)
        assertEquals(2, row!!.stars)
        assertEquals(500, row.bestScore)
    }

    // ── AC-6: saveProgress stars guard ───────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun test_saveProgress_starsZero_throws() = runTest {
        repo.saveProgress(stageId = 1, stars = 0, score = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_saveProgress_starsFour_throws() = runTest {
        repo.saveProgress(stageId = 1, stars = 4, score = 0)
    }

    // ── AC-7: saveProgress best-score / best-star rules ───────────────────

    @Test
    fun test_saveProgress_bestScoreNeverDemotes() = runTest {
        repo.saveProgress(stageId = 1, stars = 3, score = 500)
        repo.saveProgress(stageId = 1, stars = 3, score = 200)
        assertEquals(500, repo.getProgress(1)!!.bestScore)
    }

    @Test
    fun test_saveProgress_bestStarNeverDemotes() = runTest {
        repo.saveProgress(stageId = 2, stars = 3, score = 100)
        repo.saveProgress(stageId = 2, stars = 1, score = 999)
        assertEquals(3, repo.getProgress(2)!!.stars)
    }

    // ── AC-4: unlockAchievement idempotent ────────────────────────────────

    @Test
    fun test_unlockAchievement_firstCall_returnsTrue() = runTest {
        repo.seedAchievements()
        assertTrue(repo.unlockAchievement("clear_1"))
    }

    @Test
    fun test_unlockAchievement_secondCall_returnsFalse() = runTest {
        repo.seedAchievements()
        repo.unlockAchievement("clear_1")
        assertFalse(repo.unlockAchievement("clear_1"))
    }

    @Test
    fun test_unlockAchievement_unknownId_firstCallReturnsTrue() = runTest {
        // Current impl inserts then unlocks any String ID; no coin reward for unknown IDs
        assertTrue(repo.unlockAchievement("nonexistent_id"))
        // Second call is idempotent — returns false
        assertFalse(repo.unlockAchievement("nonexistent_id"))
    }

    // ── getUnlockedCats computed from maxCompletedLevel ───────────────────

    @Test
    fun test_getUnlockedCats_reflectsCompletedStages() = runTest {
        repo.saveProgress(stageId = 1, stars = 3, score = 0)
        val cats = repo.getUnlockedCats()
        assertTrue(cats.any { it.id == 1 })
    }

    @Test
    fun test_getUnlockedCats_emptyDb_returnsEmpty() = runTest {
        assertTrue(repo.getUnlockedCats().isEmpty())
    }

    // ── getThreeStarCount ─────────────────────────────────────────────────

    @Test
    fun test_getThreeStarCount_countsOnlyThreeStar() = runTest {
        repo.saveProgress(stageId = 1, stars = 3, score = 0)
        repo.saveProgress(stageId = 2, stars = 3, score = 0)
        repo.saveProgress(stageId = 3, stars = 2, score = 0)
        assertEquals(2, repo.getThreeStarCount())
    }

    // ── SharedPrefs round-trips ───────────────────────────────────────────

    @Test
    fun test_setTutorialCompleted_persists() {
        repo.setTutorialCompleted()
        assertTrue(repo.isTutorialCompleted())
    }

    @Test
    fun test_setTutorialCompleted_withExplicitFalse_persists() {
        repo.setTutorialCompleted(true)
        repo.setTutorialCompleted(false)
        assertFalse(repo.isTutorialCompleted())
    }

    @Test
    fun test_selectedCatId_roundTrip() {
        repo.setSelectedCatId(3)
        assertEquals(3, repo.getSelectedCatId())
    }

    @Test
    fun test_endlessCount_roundTrip() {
        repo.setEndlessCount(42)
        assertEquals(42, repo.getEndlessCount())
    }
}
