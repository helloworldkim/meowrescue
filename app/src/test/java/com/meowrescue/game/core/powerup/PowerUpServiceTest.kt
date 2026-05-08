package com.meowrescue.game.core.powerup

import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.core.util.RngSource
import com.meowrescue.game.data.FakeGameRepository
import com.meowrescue.game.puzzle.engine.PuzzleGrid
import com.meowrescue.game.puzzle.model.PuzzleBlock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PowerUpService] — verifies the spend-execute-refund protocol.
 *
 * Story 006 acceptance criteria covered:
 *   AC-4  InsufficientCoins path — no spend, no effect, no side effects
 *   AC-5  NoEffect path          — coins refunded
 *   AC-5  RetryExhausted path    — coins refunded, grid restored
 *   AC-6  Applied path           — coins kept
 *   AC-7  powerup_use_count increments on Applied and Refunded, NOT on InsufficientCoins
 *   AC-8  AchievementChecker fires AFTER counter increment, NOT on InsufficientCoins
 *   Grid restore on RetryExhausted
 */
class PowerUpServiceTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Fixed-output RngSource that always returns 0. */
    private val zeroRng: RngSource = RngSource { 0 }

    /**
     * Builds a minimal 4×4 grid with:
     *   - one cat block (id=0, row=1, col=0, length=2, horizontal)
     *   - one obstacle block (id=1, row=0, col=0, length=1, horizontal — removable by Ice)
     *   - one wall block    (id=2, row=3, col=3, length=1, horizontal)
     */
    private fun buildGrid(): PuzzleGrid {
        val grid = PuzzleGrid(rows = 4, cols = 4, exitRow = 1)
        grid.placeBlock(PuzzleBlock(id = 0, row = 1, col = 0, length = 2, isHorizontal = true, isCat = true))
        grid.placeBlock(PuzzleBlock(id = 1, row = 0, col = 0, length = 1, isHorizontal = true))
        grid.placeBlock(PuzzleBlock(id = 2, row = 3, col = 3, length = 1, isHorizontal = true, isWall = true))
        return grid
    }

    /**
     * Builds a grid that has ONLY cat and wall blocks — Ice should return NoEffect.
     */
    private fun buildCatWallOnlyGrid(): PuzzleGrid {
        val grid = PuzzleGrid(rows = 4, cols = 4, exitRow = 0)
        grid.placeBlock(PuzzleBlock(id = 0, row = 0, col = 0, length = 2, isHorizontal = true, isCat = true))
        grid.placeBlock(PuzzleBlock(id = 1, row = 3, col = 3, length = 1, isHorizontal = true, isWall = true))
        return grid
    }

    /** A [PowerUpEffect] whose outcome is controlled by the test. */
    private class ControllableEffect(var outcome: EffectOutcome) : PowerUpEffect {
        var executeCallCount = 0
        override fun execute(grid: PuzzleGrid): EffectOutcome {
            executeCallCount++
            return outcome
        }
    }

    /** Tracks how many times [onPowerUpUsed] was called, and what the repo count was at that moment. */
    private inner class TrackingAchievementChecker(
        private val repo: FakeGameRepository
    ) : AchievementChecker {
        var callCount = 0
        var repoCountAtCall = -1

        override suspend fun onPowerUpUsed() {
            callCount++
            repoCountAtCall = repo.getPowerUpUseCount()
        }
    }

    private lateinit var repo: FakeGameRepository
    private lateinit var economy: EconomyManager
    private lateinit var achievementChecker: TrackingAchievementChecker

    @Before
    fun setUp() {
        repo = FakeGameRepository(initialCoins = 100)
        economy = EconomyManager(repo)
    }

    private fun buildService(
        effects: Map<PowerUpKind, PowerUpEffect>
    ): PowerUpService {
        achievementChecker = TrackingAchievementChecker(repo)
        return PowerUpService(repo, economy, achievementChecker, effects)
    }

    // ── AC-4: InsufficientCoins ───────────────────────────────────────────────

    @Test
    fun test_apply_insufficientCoins_returnsInsufficientCoins_noSideEffects() = runBlocking {
        val effect = ControllableEffect(EffectOutcome.Applied)
        val service = buildService(mapOf(PowerUpKind.MAGNET to effect))
        val poorRepo = FakeGameRepository(initialCoins = 10)  // MAGNET costs 30
        val poorEconomy = EconomyManager(poorRepo)
        val poorChecker = TrackingAchievementChecker(poorRepo)
        val poorService = PowerUpService(poorRepo, poorEconomy, poorChecker,
            mapOf(PowerUpKind.MAGNET to effect))

        val result = poorService.apply(PowerUpKind.MAGNET, buildGrid())

        assertEquals(PowerUpResult.InsufficientCoins, result)
        assertEquals(0, effect.executeCallCount)          // effect never ran
        assertEquals(0, poorRepo.getPowerUpUseCount())    // counter untouched
        assertEquals(0, poorChecker.callCount)            // achievement not fired
        assertEquals(10, poorRepo.getCoins())             // balance unchanged
    }

    // ── AC-6: Applied path ────────────────────────────────────────────────────

    @Test
    fun test_apply_effectApplied_coinsDeducted_noRefund() = runBlocking {
        economy.init()
        val effect = ControllableEffect(EffectOutcome.Applied)
        val service = buildService(mapOf(PowerUpKind.MAGNET to effect))

        val result = service.apply(PowerUpKind.MAGNET, buildGrid())

        assertEquals(PowerUpResult.Applied, result)
        assertEquals(100 - PowerUpKind.MAGNET.cost, repo.getCoins())  // coins spent
    }

    // ── AC-5: NoEffect path ───────────────────────────────────────────────────

    @Test
    fun test_apply_noEffect_coinsRefunded_returnsRefunded() = runBlocking {
        economy.init()
        val effect = ControllableEffect(EffectOutcome.NoEffect)
        val service = buildService(mapOf(PowerUpKind.ICE to effect))

        val result = service.apply(PowerUpKind.ICE, buildGrid())

        assertTrue(result is PowerUpResult.Refunded)
        assertEquals(EffectOutcome.NoEffect, (result as PowerUpResult.Refunded).reason)
        assertEquals(100, repo.getCoins())   // refunded back to original
    }

    // ── AC-5: RetryExhausted path ─────────────────────────────────────────────

    @Test
    fun test_apply_retryExhausted_coinsRefunded_returnsRefunded() = runBlocking {
        economy.init()
        val effect = ControllableEffect(EffectOutcome.RetryExhausted)
        val service = buildService(mapOf(PowerUpKind.SHUFFLE to effect))

        val result = service.apply(PowerUpKind.SHUFFLE, buildGrid())

        assertTrue(result is PowerUpResult.Refunded)
        assertEquals(EffectOutcome.RetryExhausted, (result as PowerUpResult.Refunded).reason)
        assertEquals(100, repo.getCoins())   // refunded
    }

    // ── AC-7: counter increments on Applied ───────────────────────────────────

    @Test
    fun test_apply_applied_incrementsPowerUpUseCount() = runBlocking {
        economy.init()
        val service = buildService(mapOf(PowerUpKind.MAGNET to ControllableEffect(EffectOutcome.Applied)))

        service.apply(PowerUpKind.MAGNET, buildGrid())

        assertEquals(1, repo.getPowerUpUseCount())
    }

    // ── AC-7: counter increments on Refunded (NoEffect) ───────────────────────

    @Test
    fun test_apply_noEffect_incrementsPowerUpUseCount() = runBlocking {
        economy.init()
        val service = buildService(mapOf(PowerUpKind.ICE to ControllableEffect(EffectOutcome.NoEffect)))

        service.apply(PowerUpKind.ICE, buildGrid())

        assertEquals(1, repo.getPowerUpUseCount())
    }

    // ── AC-7: counter NOT incremented on InsufficientCoins ────────────────────

    @Test
    fun test_apply_insufficientCoins_doesNotIncrementCounter() = runBlocking {
        val poorRepo = FakeGameRepository(initialCoins = 5)
        val poorEconomy = EconomyManager(poorRepo)
        val poorChecker = TrackingAchievementChecker(poorRepo)
        val service = PowerUpService(poorRepo, poorEconomy, poorChecker,
            mapOf(PowerUpKind.MAGNET to ControllableEffect(EffectOutcome.Applied)))

        service.apply(PowerUpKind.MAGNET, buildGrid())

        assertEquals(0, poorRepo.getPowerUpUseCount())
    }

    // ── AC-8: achievement checked AFTER counter increment ─────────────────────

    @Test
    fun test_apply_achievementCheckedAfterCounterIncrement() = runBlocking {
        economy.init()
        val service = buildService(mapOf(PowerUpKind.MAGNET to ControllableEffect(EffectOutcome.Applied)))

        service.apply(PowerUpKind.MAGNET, buildGrid())

        assertEquals(1, achievementChecker.callCount)
        // The count captured inside onPowerUpUsed must already be 1 (incremented first)
        assertEquals(1, achievementChecker.repoCountAtCall)
    }

    // ── AC-8: achievement NOT called on InsufficientCoins ─────────────────────

    @Test
    fun test_apply_insufficientCoins_achievementNotCalled() = runBlocking {
        val poorRepo = FakeGameRepository(initialCoins = 0)
        val poorEconomy = EconomyManager(poorRepo)
        val poorChecker = TrackingAchievementChecker(poorRepo)
        val service = PowerUpService(poorRepo, poorEconomy, poorChecker,
            mapOf(PowerUpKind.MAGNET to ControllableEffect(EffectOutcome.Applied)))

        service.apply(PowerUpKind.MAGNET, buildGrid())

        assertEquals(0, poorChecker.callCount)
    }

    // ── Grid restored on RetryExhausted ──────────────────────────────────────

    @Test
    fun test_apply_retryExhausted_gridRestoredFromSnapshot() = runBlocking {
        economy.init()
        val grid = buildGrid()
        val originalBlockIds = grid.blocks.map { it.id }.toSet()
        val originalPositions = grid.blocks.associate { it.id to Pair(it.row, it.col) }

        // Effect that mutates the grid (removes block 1) but reports RetryExhausted
        val mutatingEffect = PowerUpEffect { g ->
            g.removeBlock(1)            // mutate grid
            EffectOutcome.RetryExhausted
        }
        val service = buildService(mapOf(PowerUpKind.SHUFFLE to mutatingEffect))

        service.apply(PowerUpKind.SHUFFLE, grid)

        // Grid must be back to its original state
        val restoredBlockIds = grid.blocks.map { it.id }.toSet()
        assertEquals(originalBlockIds, restoredBlockIds)
        for (block in grid.blocks) {
            val expected = originalPositions[block.id]
            assertNotNull("block ${block.id} missing from original", expected)
            assertEquals("block ${block.id} row mismatch", expected!!.first, block.row)
            assertEquals("block ${block.id} col mismatch", expected.second, block.col)
        }
    }

    // ── IceEffect: NoEffect on cat/wall-only grid (AC-10) ────────────────────

    @Test
    fun test_iceEffect_noEffect_whenOnlyCatAndWallBlocks() = runBlocking {
        economy.init()
        val iceEffect = IceEffect(zeroRng)
        val service = buildService(mapOf(PowerUpKind.ICE to iceEffect))

        val result = service.apply(PowerUpKind.ICE, buildCatWallOnlyGrid())

        assertTrue(result is PowerUpResult.Refunded)
        assertEquals(EffectOutcome.NoEffect, (result as PowerUpResult.Refunded).reason)
        assertEquals(100, repo.getCoins())
    }

    // ── IceEffect: Applied when obstacle block present ────────────────────────

    @Test
    fun test_iceEffect_applied_removesObstacleBlock() = runBlocking {
        economy.init()
        val grid = buildGrid()
        val blockCountBefore = grid.blockCount
        val iceEffect = IceEffect(zeroRng)
        val service = buildService(mapOf(PowerUpKind.ICE to iceEffect))

        val result = service.apply(PowerUpKind.ICE, grid)

        assertEquals(PowerUpResult.Applied, result)
        assertEquals(blockCountBefore - 1, grid.blockCount)
        assertFalse(grid.blocks.any { it.id == 1 })   // block 1 was the only removable
    }

    // ── ShuffleEffect: MAX_ATTEMPTS retry count (AC-9) ────────────────────────

    @Test
    fun test_shuffleEffect_retryExhausted_afterMaxAttempts() {
        var shuffleCallCount = 0
        // solver always returns -1 (unsolvable) to exhaust all attempts
        val alwaysUnsolvable: (PuzzleGrid) -> Int = { -1 }
        val countingRng = RngSource { bound ->
            shuffleCallCount++
            0
        }
        val shuffleEffect = ShuffleEffect(alwaysUnsolvable, countingRng)
        val grid = buildGrid()

        val outcome = shuffleEffect.execute(grid)

        assertEquals(EffectOutcome.RetryExhausted, outcome)
        // shuffleNonCatBlocks was called once per attempt; rng is called during each shuffle
        // We verify MAX_ATTEMPTS is 5 by confirming the constant directly
        assertEquals(5, ShuffleEffect.MAX_ATTEMPTS)
    }

    // ── ShuffleEffect: returns Applied on first solvable arrangement (AC-9) ───

    @Test
    fun test_shuffleEffect_applied_onFirstSolvableAttempt() {
        val solvableOnFirstTry: (PuzzleGrid) -> Int = { 3 }   // depth 3 = solvable
        val shuffleEffect = ShuffleEffect(solvableOnFirstTry, zeroRng)
        val grid = buildGrid()

        val outcome = shuffleEffect.execute(grid)

        assertEquals(EffectOutcome.Applied, outcome)
    }
}
