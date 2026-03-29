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
    fun debugStageInfo() {
        val gen = PuzzleGenerator()
        // Key stages
        val keyStages = (1..120).filter { gen.featuresForStage(it).hasKey }
        println("KEY STAGES: $keyStages")
        // Checkpoint stages
        val cpStages = (1..120).filter { gen.featuresForStage(it).hasCheckpoint }
        println("CHECKPOINT STAGES: $cpStages")
        // Detailed info for problematic stages
        for (s in listOf(16, 20, 25, 30, 35, 40, 50, 60, 80, 100, 105, 110, 115, 120)) {
            val result = gen.generateWithResult(s)
            val f = gen.featuresForStage(s)
            val g = result.grid
            println("Stage $s: blocks=${g.blocks.size} moves=${result.optimalMoves} dir=${g.exitDirection} " +
                    "key=${f.hasKey} cp=${f.hasCheckpoint} hasKeyLock=${g.hasKeyLock} hasCp=${g.hasCheckpoint} " +
                    "cat=${g.blocks.firstOrNull { it.isCat }}")
        }
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

        // Stages 51+: walls appear
        val wallStages = (51..70).count { gen.featuresForStage(it).hasWalls }
        assertTrue("All stages 51-70 should have walls", wallStages == 20)

        // Stages 71+: some have ice
        val iceStages = (71..90).count { gen.featuresForStage(it).hasIce }
        assertTrue("Some stages 71-90 should have ice", iceStages > 0)

        // Stages 91+: some have linked blocks
        val linkStages = (91..130).count { gen.featuresForStage(it).hasLinkedBlocks }
        assertTrue("Some stages 91-130 should have linked blocks", linkStages > 0)

        // Stages 111+: some have portals
        val portalStages = (111..150).count { gen.featuresForStage(it).hasPortals }
        assertTrue("Some stages 111-150 should have portals", portalStages > 0)

        // Stages 131+: some have multi-cat
        val mcStages = (131..160).count { gen.featuresForStage(it).hasMultiCat }
        assertTrue("Some stages 131-160 should have multi-cat", mcStages > 0)
    }

    // ── New mechanic tests ─────────────────────────────────────────────

    @Test
    fun wallBlocksImmovable() {
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT)
        grid.placeBlock(PuzzleBlock(0, 2, 0, 1, true, true))       // cat
        grid.placeBlock(PuzzleBlock(1, 0, 2, 1, true, isWall = true)) // wall

        assertFalse("Wall block should NOT be movable right", grid.canMoveInDir(1, 1, true))
        assertFalse("Wall block should NOT be movable left", grid.canMoveInDir(1, -1, true))
        assertFalse("Wall block should NOT be movable down", grid.canMoveInDir(1, 1, false))
        assertFalse("Wall block should NOT be movable up", grid.canMoveInDir(1, -1, false))
    }

    @Test
    fun linkedBlocksMoveTogether() {
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT)
        grid.placeBlock(PuzzleBlock(0, 2, 0, 1, true, true))                // cat
        grid.placeBlock(PuzzleBlock(1, 0, 0, 2, true, linkId = 1))          // linked A
        grid.placeBlock(PuzzleBlock(2, 4, 0, 2, true, linkId = 1))          // linked B

        assertTrue("Linked block should be movable", grid.canMoveInDir(1, 1, true))
        grid.moveBlockInDir(1, 1, true)

        // Both should have moved
        val blockA = grid.blocks.first { it.id == 1 }
        val blockB = grid.blocks.first { it.id == 2 }
        assertEquals("Block A col should be 1", 1, blockA.col)
        assertEquals("Block B col should be 1", 1, blockB.col)
    }

    @Test
    fun linkedBlockUndoRestoresBoth() {
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT)
        grid.placeBlock(PuzzleBlock(0, 2, 0, 1, true, true))
        grid.placeBlock(PuzzleBlock(1, 0, 0, 2, true, linkId = 1))
        grid.placeBlock(PuzzleBlock(2, 4, 0, 2, true, linkId = 1))

        grid.moveBlockInDir(1, 1, true)
        grid.undoLastMove()

        val blockA = grid.blocks.first { it.id == 1 }
        val blockB = grid.blocks.first { it.id == 2 }
        assertEquals("Block A col restored to 0", 0, blockA.col)
        assertEquals("Block B col restored to 0", 0, blockB.col)
    }

    @Test
    fun portalTeleportsCat() {
        // Portal A at (1,1), Portal B at (3,3)
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT,
            portalA = 1 * 5 + 1, portalB = 3 * 5 + 3)
        grid.placeBlock(PuzzleBlock(0, 1, 0, 1, true, true)) // cat at (1,0)

        // Move cat right by 1 → lands on (1,1) = portalA → warps to (3,3)
        grid.moveBlockInDir(0, 1, true)
        val cat = grid.blocks.first { it.isCat }
        assertEquals("Cat should warp to portal B row", 3, cat.row)
        assertEquals("Cat should warp to portal B col", 3, cat.col)
    }

    @Test
    fun portalUndoRestoresPosition() {
        val grid = PuzzleGrid(5, 5, 2, exitDirection = ExitDirection.RIGHT,
            portalA = 1 * 5 + 1, portalB = 3 * 5 + 3)
        grid.placeBlock(PuzzleBlock(0, 1, 0, 1, true, true))

        grid.moveBlockInDir(0, 1, true)
        grid.undoLastMove()
        val cat = grid.blocks.first { it.isCat }
        assertEquals("Cat should be back at original row", 1, cat.row)
        assertEquals("Cat should be back at original col", 0, cat.col)
    }

    @Test
    fun multiCatBothMustExit() {
        // 5x5 grid, primary exit RIGHT row 1, secondary exit LEFT row 3
        val grid = PuzzleGrid(5, 5, 1, exitDirection = ExitDirection.RIGHT,
            exitRow2 = 3, exitCol2 = -1, exitDirection2 = ExitDirection.LEFT)
        grid.placeBlock(PuzzleBlock(0, 1, 4, 1, true, isCat = true))  // cat1 at exit1
        grid.placeBlock(PuzzleBlock(1, 3, 2, 1, true, isCat = true))  // cat2 NOT at exit2

        assertFalse("Should NOT be solved: cat2 not at exit", grid.isSolved())

        // Place cat2 at left edge
        val grid2 = PuzzleGrid(5, 5, 1, exitDirection = ExitDirection.RIGHT,
            exitRow2 = 3, exitCol2 = -1, exitDirection2 = ExitDirection.LEFT)
        grid2.placeBlock(PuzzleBlock(0, 1, 4, 1, true, isCat = true))  // cat1 at exit1
        grid2.placeBlock(PuzzleBlock(1, 3, 0, 1, true, isCat = true))  // cat2 at exit2

        assertTrue("Should be solved: both cats at their exits", grid2.isSolved())
    }

    @Test
    fun newFeaturesStagesSolvable() {
        val gen = PuzzleGenerator()
        val failures = mutableListOf<Int>()
        for (stage in 51..140) {
            val result = gen.generateWithResult(stage)
            if (result.optimalMoves < 1) failures.add(stage)
        }
        assertTrue("Stages 51-140 should be solvable. Failures: $failures", failures.isEmpty())
    }
}
