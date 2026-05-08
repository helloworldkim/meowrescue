package com.meowrescue.game.core.progression

import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.data.FakeGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ProgressionManager.checkAchievements] and [AchievementDefs].
 *
 * Implements story-003: Achievement check engine.
 * Implements: design/gdd/progression-system.md — Achievement System acceptance criteria.
 *
 * No Android framework — runs on plain JVM via FakeGameRepository.
 */
class AchievementCheckTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeManagers(initialCoins: Int = 0): Triple<FakeGameRepository, EconomyManager, ProgressionManager> {
        val repo = FakeGameRepository(initialCoins)
        val em = EconomyManager(repo)
        val pm = ProgressionManager(repo, em)
        return Triple(repo, em, pm)
    }

    private fun baseContext(
        maxCompleted: Int = 1,
        threeStarCount: Int = 0,
        unlockedCatCount: Int = 0,
        totalCoinsEarned: Int = 0,
        powerUpUseCount: Int = 0,
        endlessCount: Int = 0,
        completedLaunchCount: Int = 0,
        stageId: Int = 1,
        stars: Int = 1,
        moves: Int = 5,
        optimalMoves: Int = 3,
        timeMs: Long = 30_000L,
        undoUsed: Boolean = false,
        catsUsed: Int = 0,
        tntExplosions: Int = 0,
        isEndless: Boolean = false
    ) = AchievementCheckContext(
        maxCompleted = maxCompleted,
        threeStarCount = threeStarCount,
        unlockedCatCount = unlockedCatCount,
        totalCoinsEarned = totalCoinsEarned,
        powerUpUseCount = powerUpUseCount,
        endlessCount = endlessCount,
        completedLaunchCount = completedLaunchCount,
        stageId = stageId,
        stars = stars,
        moves = moves,
        optimalMoves = optimalMoves,
        timeMs = timeMs,
        undoUsed = undoUsed,
        catsUsed = catsUsed,
        tntExplosions = tntExplosions,
        isEndless = isEndless
    )

    // ── AchievementDefs static tests ─────────────────────────────────────────

    // AC: TOTAL_REWARD == 2180 (enforced at init; this test provides explicit assertion evidence)
    @Test
    fun test_achievementDefs_totalRewardEquals2180() {
        assertEquals(2180, AchievementDefs.TOTAL_REWARD)
    }

    // AC: registry contains exactly 32 achievements
    @Test
    fun test_achievementDefs_hasThirtyTwoAchievements() {
        assertEquals(32, AchievementDefs.ALL.size)
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    // AC: second call with same context returns empty list (already-unlocked guard)
    @Test
    fun test_checkAchievements_idempotency_secondCallReturnsEmpty() = runTest {
        val (_, _, pm) = makeManagers()
        val ctx = baseContext(maxCompleted = 1)
        val first = pm.checkAchievements(ctx)
        assertTrue("Expected clear_1 on first call", first.any { it.id == "clear_1" })
        val second = pm.checkAchievements(ctx)
        assertTrue("Second call must return empty list", second.isEmpty())
    }

    // ── Cumulative progress ───────────────────────────────────────────────────

    // AC: fresh install, maxCompleted=90 → unlocks [clear_1, clear_10, clear_30, clear_60, clear_90]
    @Test
    fun test_checkAchievements_cumulativeProgress_stage90_returnsFive() = runTest {
        val (_, _, pm) = makeManagers()
        val ctx = baseContext(maxCompleted = 90)
        val unlocked = pm.checkAchievements(ctx)
        val ids = unlocked.map { it.id }
        assertEquals(
            listOf("clear_1", "clear_10", "clear_30", "clear_60", "clear_90"),
            ids
        )
    }

    // ── Coins snapshot semantics ──────────────────────────────────────────────

    // AC: snapshot taken from EconomyManager.totalCoinsEarned; coins=990 → coins_1000 NOT triggered
    @Test
    fun test_checkAchievements_coinsSnapshot_below1000_coins1000NotTriggered() = runTest {
        val (_, em, pm) = makeManagers(initialCoins = 990)
        em.init()
        // Pass a context that claims 9999 coins — snapshot must override this
        val ctx = baseContext(totalCoinsEarned = 9999)
        val unlocked = pm.checkAchievements(ctx)
        assertFalse(
            "coins_1000 must not trigger when snapshot is 990",
            unlocked.any { it.id == "coins_1000" }
        )
    }

    // AC: snapshot coins=1010 → coins_1000 triggers
    @Test
    fun test_checkAchievements_coinsSnapshot_above1000_coins1000Triggered() = runTest {
        val (_, em, pm) = makeManagers(initialCoins = 1010)
        em.init()
        val ctx = baseContext(totalCoinsEarned = 0) // caller value ignored
        val unlocked = pm.checkAchievements(ctx)
        assertTrue(
            "coins_1000 must trigger when snapshot is 1010",
            unlocked.any { it.id == "coins_1000" }
        )
    }

    // AC: snapshot is taken once before the unlock cycle; achievement rewards added
    //     during this call do NOT inflate the snapshot used for later checks in the same call
    @Test
    fun test_checkAchievements_coinsSnapshot_takenBeforeCycleRewards() = runTest {
        // Start with 95 coins. coins_100 threshold is 100.
        // During the check cycle, clear_1 (20 coins) fires first and adds 20 coins,
        // bringing the balance to 115 — but the snapshot was taken at 95, so coins_100
        // must NOT fire in this call.
        val (_, em, pm) = makeManagers(initialCoins = 95)
        em.init()
        val ctx = baseContext(maxCompleted = 1, totalCoinsEarned = 0)
        val unlocked = pm.checkAchievements(ctx)
        assertTrue("clear_1 should unlock", unlocked.any { it.id == "clear_1" })
        assertFalse(
            "coins_100 must not trigger — snapshot was 95, taken before cycle rewards",
            unlocked.any { it.id == "coins_100" }
        )
    }

    // ── two_move ──────────────────────────────────────────────────────────────

    // AC: moves=2 → two_move triggers
    @Test
    fun test_checkAchievements_twoMove_triggersOnMovesLte2() = runTest {
        val (_, _, pm) = makeManagers()
        val ctx = baseContext(moves = 2)
        val unlocked = pm.checkAchievements(ctx)
        assertTrue("two_move must trigger at moves=2", unlocked.any { it.id == "two_move" })
    }

    // AC: moves=3 → two_move does NOT trigger
    @Test
    fun test_checkAchievements_twoMove_doesNotTriggerOnMoves3() = runTest {
        val (_, _, pm) = makeManagers()
        val ctx = baseContext(moves = 3)
        val unlocked = pm.checkAchievements(ctx)
        assertFalse("two_move must not trigger at moves=3", unlocked.any { it.id == "two_move" })
    }

    // ── cat_all ───────────────────────────────────────────────────────────────

    // AC: unlockedCatCount=12 → cat_all NOT triggered
    @Test
    fun test_checkAchievements_catAll_requires13_notTriggeredAt12() = runTest {
        val (_, _, pm) = makeManagers()
        val ctx = baseContext(unlockedCatCount = 12)
        val unlocked = pm.checkAchievements(ctx)
        assertFalse("cat_all must not trigger at 12 cats", unlocked.any { it.id == "cat_all" })
    }

    // AC: unlockedCatCount=13 → cat_all triggers
    @Test
    fun test_checkAchievements_catAll_triggersAt13() = runTest {
        val (_, _, pm) = makeManagers()
        val ctx = baseContext(unlockedCatCount = 13)
        val unlocked = pm.checkAchievements(ctx)
        assertTrue("cat_all must trigger at 13 cats", unlocked.any { it.id == "cat_all" })
    }

    // ── Coin reward side-effect ───────────────────────────────────────────────

    // AC: coins are added to EconomyManager for each unlocked achievement
    @Test
    fun test_checkAchievements_achievementCoinsAdded() = runTest {
        val (_, em, pm) = makeManagers(initialCoins = 0)
        em.init()
        val coinsBefore = em.totalCoinsEarned.value
        // clear_1 (20 coins) + clear_10 (30 coins) = 50 coins
        val ctx = baseContext(maxCompleted = 10)
        val unlocked = pm.checkAchievements(ctx)
        val ids = unlocked.map { it.id }
        assertTrue(ids.contains("clear_1"))
        assertTrue(ids.contains("clear_10"))
        val expectedReward = unlocked.sumOf { it.coinReward }
        assertEquals(coinsBefore + expectedReward, em.totalCoinsEarned.value)
    }

    // ── launch_10 ────────────────────────────────────────────────────────────

    // AC: completedLaunchCount=10 → launch_10 triggers
    @Test
    fun test_checkAchievements_launch10_triggersAtCount10() = runTest {
        val (_, _, pm) = makeManagers()
        val ctx = baseContext(completedLaunchCount = 10)
        val unlocked = pm.checkAchievements(ctx)
        assertTrue("launch_10 must trigger at completedLaunchCount=10", unlocked.any { it.id == "launch_10" })
    }

    // AC: achievement coins bypass Endless daily cap — addCoins does not route through addEndlessCoins
    @Test
    fun test_checkAchievements_achievementCoins_bypassEndlessCap() = runTest {
        // Simulate daily Endless cap already reached (150 coins earned today).
        val repo = FakeGameRepository()
        repo.addEndlessCoins(150) // fills the daily cap
        val em = EconomyManager(repo)
        em.init()
        val pm = ProgressionManager(repo, em)
        val coinsBefore = em.coins.value
        // clear_1 (20 coins) should trigger and award coins regardless of daily cap
        val ctx = baseContext(maxCompleted = 1)
        val unlocked = pm.checkAchievements(ctx)
        assertTrue("clear_1 must unlock", unlocked.any { it.id == "clear_1" })
        // Assert full reward credited — not gated by the Endless daily cap.
        // (addEndlessCoins seeded totalCoinsEarned=150, so coins_100 also fires; use sumOf.)
        assertEquals(
            "Achievement coin reward must be credited in full even when Endless cap is reached",
            coinsBefore + unlocked.sumOf { it.coinReward },
            em.coins.value
        )
    }

    // AC: coins_100 triggers at exactly totalCoinsEarned == 100 (positive boundary)
    @Test
    fun test_checkAchievements_coins100_triggersAtExactly100() = runTest {
        val (_, em, pm) = makeManagers(initialCoins = 100)
        em.init()
        val ctx = baseContext(totalCoinsEarned = 0) // caller value ignored; snapshot = 100
        val unlocked = pm.checkAchievements(ctx)
        assertTrue("coins_100 must trigger when snapshot is exactly 100", unlocked.any { it.id == "coins_100" })
    }
}
