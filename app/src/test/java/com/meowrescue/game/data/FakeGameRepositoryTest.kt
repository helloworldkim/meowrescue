package com.meowrescue.game.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Story 009 — FakeGameRepository test double.
 *
 * Verifies all acceptance criteria (AC-1 through AC-11) on plain JVM.
 * No Robolectric, no Android framework dependencies.
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests "com.meowrescue.game.data.FakeGameRepositoryTest"
 */
class FakeGameRepositoryTest {

    // Each test creates its own instance — no shared state, no @Before needed.

    // ── AC-10: Constructor initial state ──────────────────────────────────

    @Test
    fun constructor_defaultArgs_zeroCoinsAndNoAchievements() = runTest {
        val repo = FakeGameRepository()
        assertEquals(0, repo.getCoins())
        assertEquals(0, repo.getTotalCoinsEarned())
        assertEquals(0, repo.getUnlockedAchievementCount())
    }

    @Test
    fun constructor_initialCoins_seedsBothCoinFields() = runTest {
        val repo = FakeGameRepository(initialCoins = 100)
        assertEquals(100, repo.getCoins())
        assertEquals(100, repo.getTotalCoinsEarned())
    }

    @Test
    fun constructor_initialAchievements_seedsUnlockedSet() = runTest {
        val repo = FakeGameRepository(initialAchievements = setOf("ach_1", "ach_2"))
        assertEquals(2, repo.getUnlockedAchievementCount())
        assertTrue(repo.isAchievementUnlocked("ach_1"))
        assertTrue(repo.isAchievementUnlocked("ach_2"))
        assertFalse(repo.isAchievementUnlocked("ach_3"))
    }

    // ── AC-3: Coin op invariants ──────────────────────────────────────────

    @Test
    fun addCoins_increasesBothCoinFields() = runTest {
        val repo = FakeGameRepository()
        repo.addCoins(50)
        assertEquals(50, repo.getCoins())
        assertEquals(50, repo.getTotalCoinsEarned())
    }

    @Test
    fun spendCoins_decreasesBalanceOnly_notTotalEarned() = runTest {
        val repo = FakeGameRepository()
        repo.addCoins(50)
        assertTrue(repo.spendCoins(40))
        assertEquals(10, repo.getCoins())
        assertEquals(50, repo.getTotalCoinsEarned())
    }

    @Test
    fun refundCoins_increasesBalanceOnly_notTotalEarned() = runTest {
        val repo = FakeGameRepository()
        repo.addCoins(50)
        repo.spendCoins(40)
        val totalBefore = repo.getTotalCoinsEarned()
        repo.refundCoins(40)
        assertEquals(50, repo.getCoins())
        assertEquals(totalBefore, repo.getTotalCoinsEarned())
    }

    @Test
    fun coinInvariant_addSpendRefund_expectedFinalState() = runTest {
        // addCoins(50) → spendCoins(40) → refundCoins(40)
        // Expected: coins = 50, totalCoinsEarned = 50
        val repo = FakeGameRepository()
        repo.addCoins(50)
        repo.spendCoins(40)
        repo.refundCoins(40)
        assertEquals(50, repo.getCoins())
        assertEquals(50, repo.getTotalCoinsEarned())
    }

    @Test
    fun spendCoins_insufficientBalance_returnsFalseAndBalanceUnchanged() = runTest {
        val repo = FakeGameRepository()
        repo.addCoins(20)
        assertFalse(repo.spendCoins(50))
        assertEquals(20, repo.getCoins())
        assertEquals(20, repo.getTotalCoinsEarned())
    }

    @Test
    fun spendCoins_exactBalance_returnsTrueAndZeroesBalance() = runTest {
        val repo = FakeGameRepository()
        repo.addCoins(30)
        assertTrue(repo.spendCoins(30))
        assertEquals(0, repo.getCoins())
    }

    // ── AC-6: require() guards ────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun addCoins_zero_throwsIllegalArgument() = runTest {
        FakeGameRepository().addCoins(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addCoins_negative_throwsIllegalArgument() = runTest {
        FakeGameRepository().addCoins(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun spendCoins_negative_throwsIllegalArgument() = runTest {
        FakeGameRepository().spendCoins(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun spendCoins_zero_throwsIllegalArgument() = runTest {
        FakeGameRepository().spendCoins(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refundCoins_zero_throwsIllegalArgument() = runTest {
        FakeGameRepository().refundCoins(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveProgress_starsZero_throwsIllegalArgument() = runTest {
        FakeGameRepository().saveProgress(stageId = 1, stars = 0, score = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveProgress_starsFour_throwsIllegalArgument() = runTest {
        FakeGameRepository().saveProgress(stageId = 1, stars = 4, score = 0)
    }

    // ── AC-5: saveProgress first-clear semantics ──────────────────────────

    @Test
    fun saveProgress_firstClear_returnsTrue() = runTest {
        val repo = FakeGameRepository()
        assertTrue(repo.saveProgress(stageId = 5, stars = 2, score = 100))
    }

    @Test
    fun saveProgress_secondClear_returnsFalse() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 5, stars = 2, score = 100)
        assertFalse(repo.saveProgress(stageId = 5, stars = 3, score = 200))
    }

    @Test
    fun saveProgress_promotesStarsNeverDemotes() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 1, stars = 3, score = 100)
        repo.saveProgress(stageId = 1, stars = 1, score = 999)
        assertEquals(3, repo.getProgress(1)!!.stars)
    }

    @Test
    fun saveProgress_promotesScoreNeverDemotes() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 1, stars = 3, score = 500)
        repo.saveProgress(stageId = 1, stars = 3, score = 200)
        assertEquals(500, repo.getProgress(1)!!.bestScore)
    }

    @Test
    fun saveProgress_storesRowCorrectly() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 3, stars = 2, score = 500)
        val row = repo.getProgress(3)
        assertNotNull(row)
        assertEquals(2, row!!.stars)
        assertEquals(500, row.bestScore)
        assertTrue(row.completed)
    }

    @Test
    fun getProgress_unknownStage_returnsNull() = runTest {
        assertNull(FakeGameRepository().getProgress(99))
    }

    @Test
    fun getMaxCompletedLevel_returnsHighestClearedStageId() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 2, stars = 1, score = 0)
        repo.saveProgress(stageId = 5, stars = 1, score = 0)
        assertEquals(5, repo.getMaxCompletedLevel())
    }

    @Test
    fun getMaxCompletedLevel_noProgress_returnsZero() = runTest {
        assertEquals(0, FakeGameRepository().getMaxCompletedLevel())
    }

    @Test
    fun getThreeStarCount_countsOnlyThreeStar() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 1, stars = 3, score = 0)
        repo.saveProgress(stageId = 2, stars = 3, score = 0)
        repo.saveProgress(stageId = 3, stars = 2, score = 0)
        assertEquals(2, repo.getThreeStarCount())
    }

    @Test
    fun getBestScore_returnsStoredScore() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 7, stars = 2, score = 1234)
        assertEquals(1234, repo.getBestScore(7))
    }

    @Test
    fun getBestScore_unknownStage_returnsZero() = runTest {
        assertEquals(0, FakeGameRepository().getBestScore(99))
    }

    // ── AC-4: unlockAchievement idempotency ───────────────────────────────

    @Test
    fun unlockAchievement_firstCall_returnsTrue() = runTest {
        val repo = FakeGameRepository()
        assertTrue(repo.unlockAchievement("clear_1"))
    }

    @Test
    fun unlockAchievement_secondCall_returnsFalse() = runTest {
        val repo = FakeGameRepository()
        repo.unlockAchievement("clear_1")
        assertFalse(repo.unlockAchievement("clear_1"))
    }

    @Test
    fun unlockAchievement_preSeededViaConstructor_secondCallReturnsFalse() = runTest {
        val repo = FakeGameRepository(initialAchievements = setOf("ach_pre"))
        assertFalse(repo.unlockAchievement("ach_pre"))
    }

    @Test
    fun getUnlockedAchievementCount_tracksUnlocks() = runTest {
        val repo = FakeGameRepository()
        repo.unlockAchievement("a1")
        repo.unlockAchievement("a2")
        repo.unlockAchievement("a1") // duplicate — should not increment
        assertEquals(2, repo.getUnlockedAchievementCount())
    }

    // ── Launch progress ───────────────────────────────────────────────────

    @Test
    fun saveLaunchProgress_storesAndPromotesBestStars() = runTest {
        val repo = FakeGameRepository()
        repo.saveLaunchProgress(stageId = 3, stars = 2, score = 100)
        repo.saveLaunchProgress(stageId = 3, stars = 1, score = 200)
        val row = repo.getLaunchProgress(3)
        assertNotNull(row)
        assertEquals(2, row!!.stars)
        assertEquals(200, row.bestScore)
    }

    @Test
    fun getCompletedLaunchCount_countsCompletedStages() = runTest {
        val repo = FakeGameRepository()
        repo.saveLaunchProgress(stageId = 1, stars = 1, score = 0)
        repo.saveLaunchProgress(stageId = 2, stars = 1, score = 0)
        assertEquals(2, repo.getCompletedLaunchCount())
    }

    @Test
    fun getMaxCompletedLaunchStage_returnsHighest() = runTest {
        val repo = FakeGameRepository()
        repo.saveLaunchProgress(stageId = 1, stars = 1, score = 0)
        repo.saveLaunchProgress(stageId = 4, stars = 2, score = 0)
        assertEquals(4, repo.getMaxCompletedLaunchStage())
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveLaunchProgress_starsZero_throwsIllegalArgument() = runTest {
        FakeGameRepository().saveLaunchProgress(stageId = 1, stars = 0)
    }

    // ── SharedPrefs round-trips ───────────────────────────────────────────

    @Test
    fun selectedCatId_defaultIsOne() {
        assertEquals(1, FakeGameRepository().getSelectedCatId())
    }

    @Test
    fun selectedCatId_roundTrip() {
        val repo = FakeGameRepository()
        repo.setSelectedCatId(5)
        assertEquals(5, repo.getSelectedCatId())
    }

    @Test
    fun endlessCount_roundTrip() {
        val repo = FakeGameRepository()
        repo.setEndlessCount(42)
        assertEquals(42, repo.getEndlessCount())
    }

    @Test
    fun endlessBest_roundTrip() {
        val repo = FakeGameRepository()
        repo.setEndlessBest(9999)
        assertEquals(9999, repo.getEndlessBest())
    }

    @Test
    fun tutorial_defaultFalse_thenSetTrue() {
        val repo = FakeGameRepository()
        assertFalse(repo.isTutorialCompleted())
        repo.setTutorialCompleted()
        assertTrue(repo.isTutorialCompleted())
    }

    @Test
    fun tutorial_setTrueThemFalse_persists() {
        val repo = FakeGameRepository()
        repo.setTutorialCompleted(true)
        repo.setTutorialCompleted(false)
        assertFalse(repo.isTutorialCompleted())
    }

    @Test
    fun sound_defaultTrue() {
        assertTrue(FakeGameRepository().isSoundEnabled())
    }

    @Test
    fun sound_roundTrip() {
        val repo = FakeGameRepository()
        repo.setSoundEnabled(false)
        assertFalse(repo.isSoundEnabled())
    }

    @Test
    fun powerUpUseCount_incrementsCorrectly() {
        val repo = FakeGameRepository()
        assertEquals(0, repo.getPowerUpUseCount())
        repo.incrementPowerUpUseCount()
        repo.incrementPowerUpUseCount()
        assertEquals(2, repo.getPowerUpUseCount())
    }

    // ── addEndlessCoins — daily cap enforcement ───────────────────────────

    @Test
    fun addEndlessCoins_underCap_addsFullAmount() = runTest {
        val repo = FakeGameRepository()
        val added = repo.addEndlessCoins(50)
        assertEquals(50, added)
        assertEquals(50, repo.getCoins())
        assertEquals(50, repo.getDailyEndlessCoins())
    }

    @Test
    fun addEndlessCoins_exceedsCap_clampedToRemaining() = runTest {
        val repo = FakeGameRepository()
        repo.addEndlessCoins(140)
        val added = repo.addEndlessCoins(30) // only 10 remains before cap
        assertEquals(10, added)
        assertEquals(150, repo.getDailyEndlessCoins())
    }

    @Test
    fun addEndlessCoins_atCap_returnsZero() = runTest {
        val repo = FakeGameRepository()
        repo.addEndlessCoins(150)
        val added = repo.addEndlessCoins(1)
        assertEquals(0, added)
        assertEquals(150, repo.getDailyEndlessCoins())
    }

    // ── AC-11: No android imports (structural — verified by compiler) ──────
    // The fact that this test file compiles and runs without Robolectric
    // confirms FakeGameRepository has no android.* dependencies.

    @Test
    fun fake_compilesAndRunsOnPlainJvm_noAndroidDependencies() = runTest {
        // If this test reaches here, the fake has no android.* imports blocking JVM execution.
        val repo = FakeGameRepository(initialCoins = 10, initialAchievements = setOf("x"))
        assertEquals(10, repo.getCoins())
        assertTrue(repo.isAchievementUnlocked("x"))
    }

    // ── AC-9: Public class — accessible from all test source sets ─────────
    // Verified by the fact that this test in the same package compiles.
    // Cross-package tests in other epics can also reference FakeGameRepository
    // because it is declared `public`.

    // ── Example Core-style flow (story AC-8 demonstrating fake in use) ─────

    @Test
    fun coreStyleFlow_spendCoins_balanceDecreasesTotalEarnedUnchanged() = runTest {
        val repo = FakeGameRepository(initialCoins = 100)
        repo.spendCoins(30)
        assertEquals(70, repo.getCoins())
        assertEquals(100, repo.getTotalCoinsEarned()) // total unchanged by spend
    }

    @Test
    fun coreStyleFlow_saveProgressDoesNotAwardCoins() = runTest {
        // Fake is pure persistence — saveProgress has no coin side effects.
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 1, stars = 3, score = 500)
        assertEquals(0, repo.getCoins())
        assertEquals(0, repo.getTotalCoinsEarned())
    }
}
