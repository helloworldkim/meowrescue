package com.meowrescue.game.core.economy

import com.meowrescue.game.data.FakeGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LaunchCoinEarnTest {

    private lateinit var repo: FakeGameRepository
    private lateinit var economy: EconomyManager

    @Before
    fun setUp() = runTest {
        repo = FakeGameRepository()
        economy = EconomyManager(repo)
        economy.init()
    }

    // ── Difficulty multipliers ────────────────────────────────────────────

    @Test
    fun `Easy x1_0 multiplier 1star awards 10 coins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 1, coinMultiplier = 1.0f, isFirstClear = false, completedLaunchCount = 99)
        assertEquals(10, awarded)
        assertEquals(10, repo.getCoins())
    }

    @Test
    fun `Easy x1_0 multiplier 2star awards 20 coins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 2, coinMultiplier = 1.0f, isFirstClear = false, completedLaunchCount = 99)
        assertEquals(20, awarded)
    }

    @Test
    fun `Easy x1_0 multiplier 3star awards 30 coins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 3, coinMultiplier = 1.0f, isFirstClear = false, completedLaunchCount = 99)
        assertEquals(30, awarded)
    }

    @Test
    fun `Normal x1_5 multiplier 1star awards 15 coins (integer truncation)`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 1, coinMultiplier = 1.5f, isFirstClear = false, completedLaunchCount = 99)
        assertEquals(15, awarded)
    }

    @Test
    fun `Normal x1_5 multiplier 2star awards 30 coins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 2, coinMultiplier = 1.5f, isFirstClear = false, completedLaunchCount = 99)
        assertEquals(30, awarded)
    }

    @Test
    fun `Normal x1_5 multiplier 3star awards 45 coins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 3, coinMultiplier = 1.5f, isFirstClear = false, completedLaunchCount = 99)
        assertEquals(45, awarded)
    }

    @Test
    fun `Hard x2_0 multiplier 1star awards 20 coins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 1, coinMultiplier = 2.0f, isFirstClear = false, completedLaunchCount = 99)
        assertEquals(20, awarded)
    }

    @Test
    fun `Hard x2_0 multiplier 3star awards 60 coins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 3, coinMultiplier = 2.0f, isFirstClear = false, completedLaunchCount = 99)
        assertEquals(60, awarded)
    }

    // ── First-clear bonus ─────────────────────────────────────────────────

    @Test
    fun `first clear within cap adds 50 bonus to Easy 3star (total 80)`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 3, coinMultiplier = 1.0f, isFirstClear = true, completedLaunchCount = 1)
        assertEquals(80, awarded)
    }

    @Test
    fun `first clear at cap boundary (count=49) still awards bonus`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 3, coinMultiplier = 1.0f, isFirstClear = true, completedLaunchCount = 49)
        assertEquals(80, awarded)
    }

    @Test
    fun `first clear at cap (count=50) does NOT award bonus`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 3, coinMultiplier = 1.0f, isFirstClear = true, completedLaunchCount = 50)
        assertEquals(30, awarded)
    }

    @Test
    fun `replay (isFirstClear=false) never awards bonus`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 3, coinMultiplier = 1.0f, isFirstClear = false, completedLaunchCount = 1)
        assertEquals(30, awarded)
    }

    // ── Edge cases ────────────────────────────────────────────────────────

    @Test
    fun `stars=0 awards 0 coins and does not call addCoins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 0, coinMultiplier = 1.0f, isFirstClear = true, completedLaunchCount = 1)
        assertEquals(0, awarded)
        assertEquals(0, repo.getCoins())
    }

    @Test
    fun `Hard 3star first clear within cap awards 110 coins`() = runTest {
        val awarded = economy.awardLaunchCoins(stars = 3, coinMultiplier = 2.0f, isFirstClear = true, completedLaunchCount = 1)
        assertEquals(110, awarded)
    }
}
