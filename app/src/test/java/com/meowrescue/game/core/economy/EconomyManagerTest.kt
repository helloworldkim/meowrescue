package com.meowrescue.game.core.economy

import com.meowrescue.game.data.FakeGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyManagerTest {

    private fun manager(initialCoins: Int = 0): Pair<EconomyManager, FakeGameRepository> {
        val repo = FakeGameRepository(initialCoins = initialCoins)
        return EconomyManager(repo) to repo
    }

    // AC-1 / AC-2: init() seeds StateFlows from repository
    @Test
    fun test_init_seedsStateFlowFromRepository() = runTest {
        val (em, _) = manager(initialCoins = 50)
        em.init()
        assertEquals(50, em.coins.value)
        assertEquals(50, em.totalCoinsEarned.value)
    }

    // AC-4: addCoins updates both StateFlows
    @Test
    fun test_addCoins_updatesCoinsAndTotalCoinsEarned() = runTest {
        val (em, _) = manager()
        em.init()
        em.addCoins(100)
        assertEquals(100, em.coins.value)
        assertEquals(100, em.totalCoinsEarned.value)
    }

    // AC-4: require(amount > 0) on addCoins
    @Test(expected = IllegalArgumentException::class)
    fun test_addCoins_zeroAmountThrows() = runTest {
        val (em, _) = manager()
        em.init()
        em.addCoins(0)
    }

    // AC-5: spendCoins sufficient balance
    @Test
    fun test_spendCoins_sufficientBalance_returnsTrueAndUpdatesStateFlow() = runTest {
        val (em, _) = manager(initialCoins = 100)
        em.init()
        val result = em.spendCoins(40)
        assertTrue(result)
        assertEquals(60, em.coins.value)
    }

    // AC-5 / AC-7: spendCoins insufficient balance
    @Test
    fun test_spendCoins_insufficientBalance_returnsFalseAndBalanceUnchanged() = runTest {
        val (em, _) = manager(initialCoins = 10)
        em.init()
        val result = em.spendCoins(50)
        assertFalse(result)
        assertEquals(10, em.coins.value)
    }

    // AC-5: boundary — spend exactly the available balance
    @Test
    fun test_spendCoins_exactBalance_succeeds() = runTest {
        val (em, _) = manager(initialCoins = 75)
        em.init()
        val result = em.spendCoins(75)
        assertTrue(result)
        assertEquals(0, em.coins.value)
    }

    // AC-6 / AC-8: refundCoins updates coins only, not totalCoinsEarned
    @Test
    fun test_refundCoins_updatesCoinOnly_notTotalCoinsEarned() = runTest {
        val (em, _) = manager(initialCoins = 100)
        em.init()
        em.spendCoins(60)                       // coins = 40, totalEarned = 100
        em.refundCoins(60)                      // coins = 100, totalEarned must still = 100
        assertEquals(100, em.coins.value)
        assertEquals(100, em.totalCoinsEarned.value)
    }

    // AC-7: balance never goes below zero
    @Test
    fun test_balanceNeverBelowZero() = runTest {
        val (em, _) = manager(initialCoins = 0)
        em.init()
        repeat(5) {
            val result = em.spendCoins(10)
            assertFalse(result)
        }
        assertEquals(0, em.coins.value)
    }

    // AC-8: totalCoinsEarned never decreased by spend or refund
    @Test
    fun test_totalCoinsEarned_neverDecreasedBySpendOrRefund() = runTest {
        val (em, _) = manager(initialCoins = 200)
        em.init()
        val baseline = em.totalCoinsEarned.value
        em.spendCoins(100)
        em.refundCoins(50)
        em.spendCoins(50)
        assertEquals(baseline, em.totalCoinsEarned.value)
    }

    // AC-5: require(amount > 0) on spendCoins
    @Test(expected = IllegalArgumentException::class)
    fun test_spendCoins_zeroAmountThrows() = runTest {
        val (em, _) = manager()
        em.init()
        em.spendCoins(0)
    }
}
