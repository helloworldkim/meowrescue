package com.meowrescue.game.core.progression

import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.data.FakeGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CatUnlockTest {

    private fun manager(): ProgressionManager {
        val repo = FakeGameRepository()
        return ProgressionManager(repo, EconomyManager(repo))
    }

    // AC: unlock fires on first clear of milestone stage
    @Test
    fun test_getNewlyUnlockedCat_firstClearMilestone_returnsCat() {
        val pm = manager()
        val cat = pm.getNewlyUnlockedCat(clearedStage = 30, prevMaxCompletedLevel = 29)
        assertNotNull(cat)
        assertEquals(3, cat!!.id)
        assertEquals("여름", cat.name)
        assertEquals(30, cat.requiredStage)
        assertEquals("Normal", cat.ability)
    }

    // AC: replay guard — cleared stage <= prevMax returns null
    @Test
    fun test_getNewlyUnlockedCat_replay_returnsNull() {
        val pm = manager()
        val cat = pm.getNewlyUnlockedCat(clearedStage = 30, prevMaxCompletedLevel = 60)
        assertNull(cat)
    }

    // AC: non-milestone stage returns null
    @Test
    fun test_getNewlyUnlockedCat_nonMilestoneStage_returnsNull() {
        val pm = manager()
        val cat = pm.getNewlyUnlockedCat(clearedStage = 10, prevMaxCompletedLevel = 9)
        assertNull(cat)
    }

    // AC: boundary — first cat (stage 1, prevMax 0)
    @Test
    fun test_getNewlyUnlockedCat_firstCat_returnsNabi() {
        val pm = manager()
        val cat = pm.getNewlyUnlockedCat(clearedStage = 1, prevMaxCompletedLevel = 0)
        assertNotNull(cat)
        assertEquals(1, cat!!.id)
        assertEquals("나비", cat.name)
    }

    // AC: boundary — last cat (stage 200)
    @Test
    fun test_getNewlyUnlockedCat_lastCat_returnsPrincess() {
        val pm = manager()
        val cat = pm.getNewlyUnlockedCat(clearedStage = 200, prevMaxCompletedLevel = 199)
        assertNotNull(cat)
        assertEquals(13, cat!!.id)
        assertEquals("공주", cat.name)
        assertEquals("Charge", cat.ability)
    }

    // AC: exact tie (cleared == prevMax) returns null (not a new clear)
    @Test
    fun test_getNewlyUnlockedCat_exactTie_returnsNull() {
        val pm = manager()
        val cat = pm.getNewlyUnlockedCat(clearedStage = 30, prevMaxCompletedLevel = 30)
        assertNull(cat)
    }

    // AC: CatDefinitions.ALL has exactly 13 entries
    @Test
    fun test_catDefinitions_hasThirteenCats() {
        assertEquals(13, CatDefinitions.ALL.size)
    }

    // AC: CatDefinitions milestone stages match GDD exactly
    @Test
    fun test_catDefinitions_milestoneStagesMatchGdd() {
        val expectedStages = listOf(1, 15, 30, 45, 60, 75, 90, 110, 130, 150, 170, 185, 200)
        assertEquals(expectedStages, CatDefinitions.ALL.map { it.requiredStage })
    }

    // AC: getUnlockedCats returns correct subset
    @Test
    fun test_getUnlockedCats_returnsSubsetForMaxLevel() = runTest {
        val repo = FakeGameRepository()
        val pm = ProgressionManager(repo, EconomyManager(repo))
        // Unlock stages 1-45 (cats 1-4)
        repeat(45) { i -> pm.saveProgress(i + 1, 1, 0) }
        val unlocked = pm.getUnlockedCats()
        assertEquals(4, unlocked.size)
        assertEquals(listOf(1, 2, 3, 4), unlocked.map { it.id })
    }

    // AC: getUnlockedCatCount matches getUnlockedCats size
    @Test
    fun test_getUnlockedCatCount_matchesSize() = runTest {
        val repo = FakeGameRepository()
        val pm = ProgressionManager(repo, EconomyManager(repo))
        repeat(90) { i -> pm.saveProgress(i + 1, 1, 0) }
        assertEquals(pm.getUnlockedCats().size, pm.getUnlockedCatCount())
        assertEquals(7, pm.getUnlockedCatCount())
    }

    // AC: cat_all — 12 cats at level 199, not 13
    @Test
    fun test_getUnlockedCatCount_level199_returns12() = runTest {
        val repo = FakeGameRepository()
        val pm = ProgressionManager(repo, EconomyManager(repo))
        repeat(199) { i -> pm.saveProgress(i + 1, 1, 0) }
        assertEquals(12, pm.getUnlockedCatCount())
    }

    // AC: cat_all — all 13 cats at level 200
    @Test
    fun test_getUnlockedCatCount_level200_returns13() = runTest {
        val repo = FakeGameRepository()
        val pm = ProgressionManager(repo, EconomyManager(repo))
        repeat(200) { i -> pm.saveProgress(i + 1, 1, 0) }
        assertEquals(13, pm.getUnlockedCatCount())
    }

    // AC: Cat Launch progress does NOT unlock cats (getNewlyUnlockedCat takes raw ints — caller controls)
    @Test
    fun test_getNewlyUnlockedCat_launchStage_isNullIfNotMilestone() {
        val pm = manager()
        // Simulate a Cat Launch clear being mistakenly passed: stage 131, prevMax 0
        // Stage 131 is not a cat milestone — should return null
        val cat = pm.getNewlyUnlockedCat(clearedStage = 131, prevMaxCompletedLevel = 0)
        assertNull(cat)
    }
}
