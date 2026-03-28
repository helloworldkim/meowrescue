package com.meowrescue.game.puzzle

import org.junit.Test
import org.junit.Assert.*

class StageTest {

    @Test
    fun stage82IsSolvable() {
        val gen = PuzzleGenerator()
        val result = gen.generateWithResult(82)
        assertTrue("Stage 82 should have optimal moves >= 1", result.optimalMoves >= 1)
        assertFalse("Stage 82 should not start solved", result.grid.isSolved())
        println("Stage 82: dir=${result.grid.exitDirection} moves=${result.optimalMoves} blocks=${result.grid.blocks.size}")
    }

    @Test
    fun allDirectionsAppear() {
        val gen = PuzzleGenerator()
        val dirs = (1..50).map { gen.generateWithResult(it).grid.exitDirection }.toSet()
        assertTrue("Should have at least 2 different exit directions in first 50 stages",
            dirs.size >= 2)
        println("Directions in stages 1-50: $dirs")
    }

    @Test
    fun oneCellBlocksMoveBothAxes() {
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT)
        grid.placeBlock(PuzzleBlock(0, 2, 0, 1, true, true)) // 1x1 cat
        grid.placeBlock(PuzzleBlock(1, 0, 2, 1, true))       // 1-cell block

        assertTrue("1-cell block should move right", grid.canMoveInDir(1, 1, true))
        assertTrue("1-cell block should move down", grid.canMoveInDir(1, 1, false))
    }

    @Test
    fun multiCellBlocksRestrictedToAxis() {
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT)
        grid.placeBlock(PuzzleBlock(0, 2, 0, 1, true, true))  // 1x1 cat
        grid.placeBlock(PuzzleBlock(1, 0, 4, 2, false))       // 2-cell vertical

        assertTrue("Vertical 2-cell should move down", grid.canMoveInDir(1, 1, false))
        assertFalse("Vertical 2-cell should NOT move right", grid.canMoveInDir(1, 1, true))
    }

    @Test
    fun sampleStagesSolvable() {
        val gen = PuzzleGenerator()
        val failures = mutableListOf<Int>()
        for (stage in 1..100) {
            val result = gen.generateWithResult(stage)
            if (result.optimalMoves < 1) failures.add(stage)
        }
        assertTrue("All stages 1-100 should be solvable. Failures: $failures", failures.isEmpty())
    }

    // ── New tests ────────────────────────────────────────────────────────

    @Test
    fun oneCellCatSolvesAtExit() {
        // 1x1 cat at (2, 4) on a 5x5 grid, exit RIGHT at row 2
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT)
        grid.placeBlock(PuzzleBlock(0, 2, 4, 1, true, true))
        assertTrue("1x1 cat at exit edge should be solved", grid.isSolved())
    }

    @Test
    fun oneCellCatWrongRowNotSolved() {
        // 1x1 cat at (1, 4) but exit is at row 2
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT)
        grid.placeBlock(PuzzleBlock(0, 1, 4, 1, true, true))
        assertFalse("1x1 cat on wrong row should NOT be solved", grid.isSolved())
    }

    @Test
    fun keyLockRequiresKeyAtLock() {
        // Grid with key-lock: lock at (1, 4), key starts at (3, 0)
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT,
            hasKeyLock = true, lockRow = 1, lockCol = 4)
        grid.placeBlock(PuzzleBlock(0, 2, 4, 1, true, true))  // cat at exit
        grid.placeBlock(PuzzleBlock(1, 3, 0, 1, true, isKey = true))  // key far away

        assertFalse("Should NOT be solved: key not at lock", grid.isSolved())

        // Move key to lock position
        val grid2 = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT,
            hasKeyLock = true, lockRow = 1, lockCol = 4)
        grid2.placeBlock(PuzzleBlock(0, 2, 4, 1, true, true))
        grid2.placeBlock(PuzzleBlock(1, 1, 4, 1, true, isKey = true))  // key at lock

        assertTrue("Should be solved: key at lock + cat at exit", grid2.isSolved())
    }

    @Test
    fun checkpointMustBeReached() {
        // Grid with checkpoint at (1, 2)
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT,
            checkpointRow = 1, checkpointCol = 2)
        grid.placeBlock(PuzzleBlock(0, 2, 4, 1, true, true))  // cat at exit

        assertFalse("Should NOT be solved: checkpoint not reached", grid.isSolved())

        // Manually set checkpoint reached
        grid.checkpointReached = true
        assertTrue("Should be solved: checkpoint reached + cat at exit", grid.isSolved())
    }

    @Test
    fun checkpointReachedByMoving() {
        // Cat at (1, 0), checkpoint at (1, 2), exit RIGHT at row 1
        val grid = PuzzleGrid(5, 5, 1, exitDirection = ExitDirection.RIGHT,
            checkpointRow = 1, checkpointCol = 2)
        grid.placeBlock(PuzzleBlock(0, 1, 0, 1, true, true))

        assertFalse("Checkpoint should not be reached initially", grid.checkpointReached)

        // Move cat to checkpoint position
        grid.moveBlockInDir(0, 2, true)  // move right 2 → col 2
        assertTrue("Checkpoint should be reached after moving cat there", grid.checkpointReached)
    }

    @Test
    fun undoRevertsCheckpoint() {
        val grid = PuzzleGrid(5, 5, 1, exitDirection = ExitDirection.RIGHT,
            checkpointRow = 1, checkpointCol = 2)
        grid.placeBlock(PuzzleBlock(0, 1, 0, 1, true, true))

        grid.moveBlockInDir(0, 2, true) // move to col 2 (checkpoint)
        assertTrue("Checkpoint should be reached", grid.checkpointReached)

        grid.undoLastMove()
        assertFalse("Checkpoint should be reverted after undo", grid.checkpointReached)
    }

    @Test
    fun checkpointPassThrough() {
        // Cat at (1,0), checkpoint at (1,2), move cat right by 3 → should detect pass-through
        val grid = PuzzleGrid(5, 5, 1, exitDirection = ExitDirection.RIGHT,
            checkpointRow = 1, checkpointCol = 2)
        grid.placeBlock(PuzzleBlock(0, 1, 0, 1, true, true))

        grid.moveBlockInDir(0, 3, true) // move to col 3, passing through col 2
        assertTrue("Checkpoint should be reached via pass-through", grid.checkpointReached)
    }

    @Test
    fun keyAndCheckpointStagesSolvable() {
        val gen = PuzzleGenerator()
        val failures = mutableListOf<Int>()
        // Test stages that should have key/checkpoint features
        for (stage in listOf(20, 25, 35, 40, 55, 60, 70, 80, 90, 100)) {
            val result = gen.generateWithResult(stage)
            if (result.optimalMoves < 1) failures.add(stage)
        }
        assertTrue("Key/checkpoint stages should be solvable. Failures: $failures", failures.isEmpty())
    }

    @Test
    fun featuresForStageDistribution() {
        val gen = PuzzleGenerator()
        // Stages 1-15: no features
        for (s in 1..15) {
            val f = gen.featuresForStage(s)
            assertFalse("Stage $s should have no key", f.hasKey)
            assertFalse("Stage $s should have no checkpoint", f.hasCheckpoint)
        }
        // Stages 16-30: some have key, none have checkpoint
        var hasAnyKey = false
        for (s in 16..30) {
            val f = gen.featuresForStage(s)
            if (f.hasKey) hasAnyKey = true
            assertFalse("Stage $s should have no checkpoint", f.hasCheckpoint)
        }
        assertTrue("Some stages 16-30 should have key", hasAnyKey)

        // Stages 31-50: key XOR checkpoint
        for (s in 31..50) {
            val f = gen.featuresForStage(s)
            assertTrue("Stage $s: key XOR checkpoint", f.hasKey != f.hasCheckpoint)
        }
    }
}
