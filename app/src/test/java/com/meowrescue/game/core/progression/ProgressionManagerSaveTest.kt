package com.meowrescue.game.core.progression

import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.data.FakeGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionManagerSaveTest {

    private fun manager(): Pair<ProgressionManager, FakeGameRepository> {
        val repo = FakeGameRepository()
        return ProgressionManager(repo, EconomyManager(repo)) to repo
    }

    // AC: first clear returns true
    @Test
    fun test_saveProgress_firstClear_returnsTrue() = runTest {
        val (pm, _) = manager()
        assertTrue(pm.saveProgress(5, 2, 800))
    }

    // AC: stores stars and score
    @Test
    fun test_saveProgress_storesStarsAndScore() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(5, 2, 800)
        val p = pm.getProgress(5)
        assertNotNull(p)
        assertEquals(2, p!!.stars)
        assertEquals(800, p.bestScore)
    }

    // AC: replay returns false
    @Test
    fun test_saveProgress_replay_returnsFalse() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(5, 2, 800)
        assertFalse(pm.saveProgress(5, 1, 900))
    }

    // AC: best-ever stars — replay with fewer stars never downgrades
    @Test
    fun test_saveProgress_bestEverStars_neverDowngrades() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(5, 3, 1000)
        pm.saveProgress(5, 1, 1200)
        assertEquals(3, pm.getProgress(5)!!.stars)
    }

    // AC: best-ever score — replay with lower score never downgrades
    @Test
    fun test_saveProgress_bestEverScore_neverDowngrades() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(5, 2, 1000)
        pm.saveProgress(5, 3, 500)
        assertEquals(1000, pm.getProgress(5)!!.bestScore)
    }

    // AC: best-ever score — higher score does update
    @Test
    fun test_saveProgress_higherScore_updates() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(5, 2, 500)
        pm.saveProgress(5, 2, 1500)
        assertEquals(1500, pm.getProgress(5)!!.bestScore)
    }

    // AC: require stageId >= 1
    @Test(expected = IllegalArgumentException::class)
    fun test_saveProgress_stageIdZero_throws() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(0, 2, 100)
    }

    // AC: require stars in 1..3 — zero throws
    @Test(expected = IllegalArgumentException::class)
    fun test_saveProgress_starsZero_throws() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(1, 0, 100)
    }

    // AC: require stars in 1..3 — four throws
    @Test(expected = IllegalArgumentException::class)
    fun test_saveProgress_starsFour_throws() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(1, 4, 100)
    }

    // AC: getMaxCompletedLevel fresh install returns 0
    @Test
    fun test_getMaxCompletedLevel_freshInstall_returnsZero() = runTest {
        val (pm, _) = manager()
        assertEquals(0, pm.getMaxCompletedLevel())
    }

    // AC: getMaxCompletedLevel returns highest cleared stage
    @Test
    fun test_getMaxCompletedLevel_returnsHighest() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(1, 2, 100)
        pm.saveProgress(3, 3, 200)
        pm.saveProgress(2, 1, 50)
        assertEquals(3, pm.getMaxCompletedLevel())
    }

    // AC: getThreeStarCount counts only 3-star stages
    @Test
    fun test_getThreeStarCount_countsCorrectly() = runTest {
        val (pm, _) = manager()
        pm.saveProgress(1, 3, 100)
        pm.saveProgress(5, 3, 200)
        pm.saveProgress(10, 3, 300)
        pm.saveProgress(2, 2, 50)   // 2 stars — should not count
        assertEquals(3, pm.getThreeStarCount())
    }
}
