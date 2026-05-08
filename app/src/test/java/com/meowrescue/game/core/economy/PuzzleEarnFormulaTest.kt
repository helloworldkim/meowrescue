package com.meowrescue.game.core.economy

import com.meowrescue.game.data.FakeGameRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EconomyManager.awardPuzzleClearCoins.
 *
 * Implements story-003: Puzzle earn formula in EconomyManager.
 * Covers TR-economy-005: Puzzle star coin mapping {1:10, 2:20, 3:30} + first-clear bonus 50.
 */
class PuzzleEarnFormulaTest {

    private lateinit var repo: FakeGameRepository
    private lateinit var economy: EconomyManager

    @Before
    fun setUp() = runBlocking {
        repo = FakeGameRepository()
        economy = EconomyManager(repo)
        economy.init()
    }

    // AC-2: 1★ → 10 coins
    @Test
    fun test_awardPuzzleClearCoins_oneStar_returns10() = runBlocking {
        val awarded = economy.awardPuzzleClearCoins(1, "stage-1", false)
        assertEquals(10, awarded)
        assertEquals(10, economy.coins.value)
    }

    // AC-2: 2★ → 20 coins
    @Test
    fun test_awardPuzzleClearCoins_twoStar_returns20() = runBlocking {
        val awarded = economy.awardPuzzleClearCoins(2, "stage-1", false)
        assertEquals(20, awarded)
        assertEquals(20, economy.coins.value)
    }

    // AC-2: 3★ → 30 coins
    @Test
    fun test_awardPuzzleClearCoins_threeStar_returns30() = runBlocking {
        val awarded = economy.awardPuzzleClearCoins(3, "stage-1", false)
        assertEquals(30, awarded)
        assertEquals(30, economy.coins.value)
    }

    // AC-3: first-clear bonus adds 50
    @Test
    fun test_awardPuzzleClearCoins_threeStarFirstClear_returns80() = runBlocking {
        val awarded = economy.awardPuzzleClearCoins(3, "stage-1", true)
        assertEquals(80, awarded)
        assertEquals(80, economy.coins.value)
    }

    // AC-3: replay — no bonus
    @Test
    fun test_awardPuzzleClearCoins_twoStarReplay_returns20() = runBlocking {
        val awarded = economy.awardPuzzleClearCoins(2, "stage-1", false)
        assertEquals(20, awarded)
    }

    // AC-4: 1★ first clear = 10 + 50 = 60
    @Test
    fun test_awardPuzzleClearCoins_oneStarFirstClear_returns60() = runBlocking {
        val awarded = economy.awardPuzzleClearCoins(1, "stage-1", true)
        assertEquals(60, awarded)
        assertEquals(60, economy.coins.value)
    }

    // AC-5: addCoins consolidates base + bonus into single amount
    @Test
    fun test_awardPuzzleClearCoins_threeStarFirstClear_singleConsolidatedAmount() = runBlocking {
        val coinsBefore = economy.coins.value
        economy.awardPuzzleClearCoins(3, "stage-1", true)
        // Balance jumps by exactly 80 (30+50 consolidated, not two separate calls)
        assertEquals(coinsBefore + 80, economy.coins.value)
    }

    // AC-6: stars=0 returns 0, no coins added
    @Test
    fun test_awardPuzzleClearCoins_zeroStars_returns0_noCoinsAdded() = runBlocking {
        val coinsBefore = economy.coins.value
        val awarded = economy.awardPuzzleClearCoins(0, "stage-1", false)
        assertEquals(0, awarded)
        assertEquals(coinsBefore, economy.coins.value)
    }

    // AC-7: stars out of range throws
    @Test
    fun test_awardPuzzleClearCoins_invalidStars_throws() = runBlocking {
        try {
            economy.awardPuzzleClearCoins(4, "stage-1", false)
            fail("Expected IllegalArgumentException for stars=4")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // Constants must match GDD spec
    @Test
    fun test_puzzleCoinConstants_matchGddSpec() {
        assertEquals(10, EconomyManager.PUZZLE_COINS_1_STAR)
        assertEquals(20, EconomyManager.PUZZLE_COINS_2_STAR)
        assertEquals(30, EconomyManager.PUZZLE_COINS_3_STAR)
        assertEquals(50, EconomyManager.FIRST_CLEAR_BONUS)
    }
}
