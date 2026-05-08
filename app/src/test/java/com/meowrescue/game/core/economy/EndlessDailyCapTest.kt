package com.meowrescue.game.core.economy

import com.meowrescue.game.data.FakeGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EndlessDailyCapTest {

    private lateinit var repo: FakeGameRepository
    private lateinit var economy: EconomyManager

    @Before
    fun setUp() = runTest {
        repo = FakeGameRepository()
        economy = EconomyManager(repo)
        economy.init()
    }

    // ── Full cap available ────────────────────────────────────────────────

    @Test
    fun `dailyEarned=0 3star awards full 30 coins`() = runTest {
        val awarded = economy.awardEndlessCoins(30)
        assertEquals(30, awarded)
        assertEquals(30, repo.getCoins())
        assertEquals(30, repo.getDailyEndlessCoins())
    }

    @Test
    fun `dailyEarned=0 1star awards full 10 coins`() = runTest {
        val awarded = economy.awardEndlessCoins(10)
        assertEquals(10, awarded)
    }

    // ── Partial award ─────────────────────────────────────────────────────

    @Test
    fun `dailyEarned=140 award=30 gives partial 10 coins`() = runTest {
        // Seed daily counter to 140
        repeat(14) { economy.awardEndlessCoins(10) }
        assertEquals(140, repo.getDailyEndlessCoins())

        val awarded = economy.awardEndlessCoins(30)
        assertEquals(10, awarded)
        assertEquals(150, repo.getDailyEndlessCoins())
        assertEquals(150, repo.getCoins())
    }

    @Test
    fun `dailyEarned=149 award=1 gives full 1 coin (exactly hits cap)`() = runTest {
        repeat(14) { economy.awardEndlessCoins(10) }
        economy.awardEndlessCoins(9)
        assertEquals(149, repo.getDailyEndlessCoins())

        val awarded = economy.awardEndlessCoins(1)
        assertEquals(1, awarded)
        assertEquals(150, repo.getDailyEndlessCoins())
    }

    @Test
    fun `dailyEarned=149 award=2 gives partial 1 coin`() = runTest {
        repeat(14) { economy.awardEndlessCoins(10) }
        economy.awardEndlessCoins(9)
        assertEquals(149, repo.getDailyEndlessCoins())

        val awarded = economy.awardEndlessCoins(2)
        assertEquals(1, awarded)
    }

    // ── Cap exhausted ─────────────────────────────────────────────────────

    @Test
    fun `dailyEarned=150 award=30 gives 0 coins and addCoins not called`() = runTest {
        repeat(5) { economy.awardEndlessCoins(30) }
        assertEquals(150, repo.getDailyEndlessCoins())
        val balanceBefore = repo.getCoins()

        val awarded = economy.awardEndlessCoins(30)
        assertEquals(0, awarded)
        assertEquals(balanceBefore, repo.getCoins())
    }

    // ── Achievement coins bypass cap ──────────────────────────────────────

    @Test
    fun `addCoins directly not affected by endless daily cap`() = runTest {
        repeat(5) { economy.awardEndlessCoins(30) }
        assertEquals(150, repo.getDailyEndlessCoins())

        // Achievement coin goes through addCoins — bypasses cap
        economy.addCoins(50)
        assertEquals(200, repo.getCoins())
        assertEquals(150, repo.getDailyEndlessCoins()) // cap counter unchanged
    }

    // ── require guard ─────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `amount=0 throws IllegalArgumentException`() = runTest {
        economy.awardEndlessCoins(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative amount throws IllegalArgumentException`() = runTest {
        economy.awardEndlessCoins(-1)
    }

    // ── StateFlow refresh ────────────────────────────────────────────────

    @Test
    fun `coins StateFlow reflects awarded amount`() = runTest {
        economy.awardEndlessCoins(20)
        assertEquals(20, economy.coins.value)
        assertEquals(20, economy.totalCoinsEarned.value)
    }

    @Test
    fun `coins StateFlow does not update when cap exhausted`() = runTest {
        repeat(5) { economy.awardEndlessCoins(30) }
        val flowBefore = economy.coins.value

        economy.awardEndlessCoins(30)
        assertEquals(flowBefore, economy.coins.value)
    }
}
