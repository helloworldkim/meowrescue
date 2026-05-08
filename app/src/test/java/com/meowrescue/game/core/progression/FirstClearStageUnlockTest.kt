package com.meowrescue.game.core.progression

import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.data.FakeGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirstClearStageUnlockTest {

    private lateinit var repo: FakeGameRepository
    private lateinit var manager: ProgressionManager

    @Before
    fun setUp() = runTest {
        repo = FakeGameRepository()
        manager = ProgressionManager(repo, EconomyManager(repo))
    }

    // ── isStageUnlocked predicate ─────────────────────────────────────────

    @Test
    fun `stage 1 is unlocked on fresh install (maxCompleted=0)`() {
        assertTrue(ProgressionManager.isStageUnlocked(1, 0))
    }

    @Test
    fun `stage 2 is locked on fresh install`() {
        assertFalse(ProgressionManager.isStageUnlocked(2, 0))
    }

    @Test
    fun `stage N+1 unlocked after clearing stage N`() {
        assertTrue(ProgressionManager.isStageUnlocked(2, 1))
        assertTrue(ProgressionManager.isStageUnlocked(10, 9))
        assertTrue(ProgressionManager.isStageUnlocked(200, 199))
    }

    @Test
    fun `stage N+2 stays locked after clearing stage N`() {
        assertFalse(ProgressionManager.isStageUnlocked(3, 1))
        assertFalse(ProgressionManager.isStageUnlocked(201, 199))
    }

    @Test
    fun `stage 200 is the last unlockable stage`() {
        assertTrue(ProgressionManager.isStageUnlocked(200, 199))
        // Stage 201 should not be rendered — predicate returns true but UI caps at 200
        assertTrue(ProgressionManager.isStageUnlocked(201, 200))
    }

    // ── saveProgress + maxCompletedLevel round-trip ───────────────────────

    @Test
    fun `first clear of stage 1 sets maxCompletedLevel to 1`() = runTest {
        manager.saveProgress(stageId = 1, stars = 3)
        assertEquals(1, manager.getMaxCompletedLevel())
    }

    @Test
    fun `first clear returns true (isFirstClear)`() = runTest {
        val isFirst = manager.saveProgress(stageId = 1, stars = 3)
        assertTrue(isFirst)
    }

    @Test
    fun `replay of stage 1 returns false (not first clear)`() = runTest {
        manager.saveProgress(stageId = 1, stars = 3)
        val isFirst = manager.saveProgress(stageId = 1, stars = 2)
        assertFalse(isFirst)
    }

    @Test
    fun `replay does not decrease maxCompletedLevel`() = runTest {
        manager.saveProgress(stageId = 30, stars = 3)
        assertEquals(30, manager.getMaxCompletedLevel())

        manager.saveProgress(stageId = 1, stars = 3) // replay old stage
        assertEquals(30, manager.getMaxCompletedLevel())
    }

    @Test
    fun `fresh install stage 1 is only accessible stage`() = runTest {
        val max = manager.getMaxCompletedLevel()
        assertEquals(0, max)
        assertTrue(ProgressionManager.isStageUnlocked(1, max))
        assertFalse(ProgressionManager.isStageUnlocked(2, max))
    }

    @Test
    fun `clearing stage 1 unlocks stage 2`() = runTest {
        manager.saveProgress(stageId = 1, stars = 1)
        val max = manager.getMaxCompletedLevel()
        assertTrue(ProgressionManager.isStageUnlocked(2, max))
    }

    // ── Cat unlock ────────────────────────────────────────────────────────

    @Test
    fun `first clear of stage 1 unlocks cat 나비 (requiredStage=1)`() = runTest {
        val prevMax = manager.getMaxCompletedLevel() // 0
        manager.saveProgress(stageId = 1, stars = 3)
        val cat = manager.getNewlyUnlockedCat(clearedStage = 1, prevMaxCompletedLevel = prevMax)
        assertNotNull(cat)
        assertEquals("나비", cat!!.name)
    }

    @Test
    fun `replay of stage 1 when maxCompleted is already 30 does not trigger unlock`() = runTest {
        manager.saveProgress(stageId = 30, stars = 3)
        val prevMax = manager.getMaxCompletedLevel() // 30
        manager.saveProgress(stageId = 1, stars = 3)
        val cat = manager.getNewlyUnlockedCat(clearedStage = 1, prevMaxCompletedLevel = prevMax)
        assertNull(cat)
    }

    @Test
    fun `stage with no cat milestone returns null`() = runTest {
        val cat = manager.getNewlyUnlockedCat(clearedStage = 2, prevMaxCompletedLevel = 1)
        assertNull(cat)
    }
}
