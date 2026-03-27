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
        // Place a 1-cell block at (0, 0)
        grid.placeBlock(PuzzleBlock(0, 2, 0, 2, true, true)) // cat
        grid.placeBlock(PuzzleBlock(1, 0, 2, 1, true))       // 1-cell block

        // Should be able to move horizontally
        assertTrue("1-cell block should move right", grid.canMoveInDir(1, 1, true))
        // Should be able to move vertically
        assertTrue("1-cell block should move down", grid.canMoveInDir(1, 1, false))
    }

    @Test
    fun multiCellBlocksRestrictedToAxis() {
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT)
        grid.placeBlock(PuzzleBlock(0, 2, 0, 2, true, true))  // cat horizontal
        grid.placeBlock(PuzzleBlock(1, 0, 4, 2, false))       // 2-cell vertical

        // Vertical block should move vertically
        assertTrue("Vertical 2-cell should move down", grid.canMoveInDir(1, 1, false))
        // Vertical block should NOT move horizontally
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
}
