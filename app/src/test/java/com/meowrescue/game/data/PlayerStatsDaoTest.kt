package com.meowrescue.game.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 004 (T-1-6) retrofit tests for [PlayerStatsDao] atomic coin operations.
 *
 * Validates that the three ADR-0003 SQL queries (addCoins, spendCoins, refundCoins)
 * shipping in v1.6.0 match ADR-0003 invariants verbatim. Production SQL is
 * unchanged — this file fills the test-coverage gap identified in the
 * persistence as-built audit (2026-04-19).
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests com.meowrescue.game.data.PlayerStatsDaoTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlayerStatsDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PlayerStatsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dao = db.playerStatsDao()
        dao.saveStats(PlayerStats(id = 1, coins = 0, totalCoinsEarned = 0))
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Baseline ──────────────────────────────────────────────────────────

    @Test
    fun test_getStats_returnsSeededRow() {
        val stats = dao.getStats()
        assertNotNull("Row with id=1 must exist after setUp", stats)
        assertEquals(0, stats!!.coins)
        assertEquals(0, stats.totalCoinsEarned)
    }

    @Test
    fun test_getCoins_returnsNullWhenNoRowExists() {
        db.openHelper.writableDatabase.execSQL("DELETE FROM player_stats")
        assertNull(dao.getCoins())
    }

    // ── AC-1: addCoins atomicity ──────────────────────────────────────────

    @Test
    fun test_addCoins_incrementsBothColumnsAtomically() {
        dao.saveStats(PlayerStats(id = 1, coins = 100, totalCoinsEarned = 200))

        dao.addCoins(50)

        val stats = dao.getStats()!!
        assertEquals(150, stats.coins)
        assertEquals(250, stats.totalCoinsEarned)
    }

    @Test
    fun test_addCoins_accumulatesAcrossMultipleCalls() {
        dao.addCoins(30)
        dao.addCoins(20)

        val stats = dao.getStats()!!
        assertEquals(50, stats.coins)
        assertEquals(50, stats.totalCoinsEarned)
    }

    @Test
    fun test_addCoins_withZeroAmount_leavesBalanceUnchanged() {
        dao.addCoins(0)

        val stats = dao.getStats()!!
        assertEquals(0, stats.coins)
        assertEquals(0, stats.totalCoinsEarned)
    }

    // ── AC-2: spendCoins success path ─────────────────────────────────────

    @Test
    fun test_spendCoins_sufficientBalance_returnsOneAndDecrements() {
        dao.saveStats(PlayerStats(id = 1, coins = 100, totalCoinsEarned = 500))

        val rowsAffected = dao.spendCoins(40)

        assertEquals("spendCoins must return 1 on success", 1, rowsAffected)
        assertEquals(60, dao.getCoins())
        // totalCoinsEarned must never be touched by spend
        assertEquals(500, dao.getStats()!!.totalCoinsEarned)
    }

    @Test
    fun test_spendCoins_exactBalance_succeedsAndLeavesZero() {
        dao.saveStats(PlayerStats(id = 1, coins = 75, totalCoinsEarned = 300))

        val rowsAffected = dao.spendCoins(75)

        assertEquals("spending exact balance must succeed", 1, rowsAffected)
        assertEquals(0, dao.getCoins())
        assertEquals(300, dao.getStats()!!.totalCoinsEarned)
    }

    // ── AC-3: spendCoins insufficient path ────────────────────────────────

    @Test
    fun test_spendCoins_insufficientBalance_returnsZeroAndUnchanged() {
        dao.saveStats(PlayerStats(id = 1, coins = 30, totalCoinsEarned = 500))

        val rowsAffected = dao.spendCoins(50)

        assertEquals("spendCoins must return 0 on insufficient funds", 0, rowsAffected)
        assertEquals(30, dao.getCoins())
        assertEquals(500, dao.getStats()!!.totalCoinsEarned)

        // Off-by-one boundary: spending balance+1 still fails
        val offByOne = dao.spendCoins(31)
        assertEquals(0, offByOne)
        assertEquals(30, dao.getCoins())
    }

    @Test
    fun test_spendCoins_onZeroBalance_returnsZero() {
        // player_stats seeded with coins=0 in setUp
        val rowsAffected = dao.spendCoins(1)

        assertEquals(0, rowsAffected)
        assertEquals(0, dao.getCoins())
    }

    // ── AC-4: refundCoins semantics ───────────────────────────────────────

    @Test
    fun test_refundCoins_incrementsCoinsOnly_leavesTotalCoinsEarnedUnchanged() {
        dao.saveStats(PlayerStats(id = 1, coins = 50, totalCoinsEarned = 200))

        dao.refundCoins(40)

        val stats = dao.getStats()!!
        assertEquals("coins must reflect the refund", 90, stats.coins)
        assertEquals(
            "totalCoinsEarned must NEVER change on refund (the bug ADR-0003 prevents)",
            200,
            stats.totalCoinsEarned
        )
    }

    // ── AC-5: Concurrent spendCoins race — the linchpin test ─────────────

    /**
     * Two concurrent spendCoins(50) on balance 50 must produce exactly one
     * success and one failure. This is the whole point of the conditional
     * `WHERE coins >= :amount` UPDATE — without it, both spends would see
     * sufficient funds and both would deduct, violating the non-negative
     * balance invariant.
     *
     * Uses real IO-dispatcher threads (not virtual test time) so the SQL
     * statements genuinely race at SQLite's row-level locking tier.
     */
    @Test
    fun test_concurrentSpendCoins_onBalance50_exactlyOneSucceeds() = runBlocking {
        dao.saveStats(PlayerStats(id = 1, coins = 50, totalCoinsEarned = 100))

        val results = awaitAll(
            async(Dispatchers.IO) { dao.spendCoins(50) },
            async(Dispatchers.IO) { dao.spendCoins(50) }
        )

        assertEquals("Exactly one spend must succeed", 1, results.count { it == 1 })
        assertEquals("Exactly one spend must fail",    1, results.count { it == 0 })
        assertEquals("Final balance must be zero",     0, dao.getCoins())
        assertEquals(
            "totalCoinsEarned must never be touched by spend, even under races",
            100,
            dao.getStats()!!.totalCoinsEarned
        )
    }

    // ── AC-6: Spend-refund cycle preserves both columns ───────────────────

    /**
     * 100 iterations of spendCoins(40) + refundCoins(40) must leave both
     * `coins` and `totalCoinsEarned` untouched. If refundCoins ever touches
     * `totalCoinsEarned`, this test will detect a +4000 leak — the bug
     * ADR-0003 was written to prevent (Ice/Shuffle power-ups refund often,
     * and any leak would corrupt achievement coin thresholds).
     */
    @Test
    fun test_spendRefundCycle_100Iterations_preservesBalanceAndTotalCoinsEarned() {
        dao.saveStats(PlayerStats(id = 1, coins = 100, totalCoinsEarned = 500))

        repeat(100) {
            val spent = dao.spendCoins(40)
            assertEquals("spend must always succeed on balance 100", 1, spent)
            dao.refundCoins(40)
        }

        val stats = dao.getStats()!!
        assertEquals("coins must return to seeded value after every cycle", 100, stats.coins)
        assertEquals(
            "totalCoinsEarned must never change across 100 spend-refund cycles",
            500,
            stats.totalCoinsEarned
        )
    }
}
