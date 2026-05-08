package com.meowrescue.game.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepositoryNewMethodsTest {

    // ── getAllStarRatings ─────────────────────────────────────────────────

    @Test
    fun getAllStarRatings_sizeIs211() = runTest {
        assertEquals(211, FakeGameRepository().getAllStarRatings().size)
    }

    @Test
    fun getAllStarRatings_allZeroWhenNoProgress() = runTest {
        assertTrue(FakeGameRepository().getAllStarRatings().all { it == 0 })
    }

    @Test
    fun getAllStarRatings_reflectsClearedStages() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 5, stars = 3, score = 100)
        repo.saveProgress(stageId = 10, stars = 1, score = 50)
        val ratings = repo.getAllStarRatings()
        assertEquals(3, ratings[5])
        assertEquals(1, ratings[10])
        assertEquals(0, ratings[1])
    }

    @Test
    fun getAllStarRatings_promotesToBestStars() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 3, stars = 1, score = 0)
        repo.saveProgress(stageId = 3, stars = 3, score = 0)
        assertEquals(3, repo.getAllStarRatings()[3])
    }

    @Test
    fun getAllStarRatings_stageIdsAtBoundaries() = runTest {
        val repo = FakeGameRepository()
        repo.saveProgress(stageId = 1, stars = 2, score = 0)
        repo.saveProgress(stageId = 210, stars = 3, score = 0)
        val ratings = repo.getAllStarRatings()
        assertEquals(2, ratings[1])
        assertEquals(3, ratings[210])
    }

    // ── getLaunchBestScore / updateLaunchBestScore ────────────────────────

    @Test
    fun getLaunchBestScore_returnsZeroWhenNeverPlayed() {
        val repo = FakeGameRepository()
        Difficulty.values().forEach { d -> assertEquals(0, repo.getLaunchBestScore(d)) }
    }

    @Test
    fun updateLaunchBestScore_setsScoreOnFirstPlay() {
        val repo = FakeGameRepository()
        repo.updateLaunchBestScore(Difficulty.NORMAL, 500)
        assertEquals(500, repo.getLaunchBestScore(Difficulty.NORMAL))
    }

    @Test
    fun updateLaunchBestScore_promotesToHigherScore() {
        val repo = FakeGameRepository()
        repo.updateLaunchBestScore(Difficulty.HARD, 300)
        repo.updateLaunchBestScore(Difficulty.HARD, 800)
        assertEquals(800, repo.getLaunchBestScore(Difficulty.HARD))
    }

    @Test
    fun updateLaunchBestScore_doesNotDemoteOnLowerScore() {
        val repo = FakeGameRepository()
        repo.updateLaunchBestScore(Difficulty.EASY, 1000)
        repo.updateLaunchBestScore(Difficulty.EASY, 200)
        assertEquals(1000, repo.getLaunchBestScore(Difficulty.EASY))
    }

    @Test
    fun updateLaunchBestScore_eachDifficultyIsIndependent() {
        val repo = FakeGameRepository()
        repo.updateLaunchBestScore(Difficulty.EASY, 100)
        repo.updateLaunchBestScore(Difficulty.HARD, 999)
        assertEquals(100, repo.getLaunchBestScore(Difficulty.EASY))
        assertEquals(0, repo.getLaunchBestScore(Difficulty.NORMAL))
        assertEquals(999, repo.getLaunchBestScore(Difficulty.HARD))
    }

    // ── Difficulty enum ───────────────────────────────────────────────────

    @Test
    fun difficulty_coinMultipliers() {
        assertEquals(1.0f, Difficulty.EASY.coinMultiplier)
        assertEquals(1.5f, Difficulty.NORMAL.coinMultiplier)
        assertEquals(2.0f, Difficulty.HARD.coinMultiplier)
    }
}
