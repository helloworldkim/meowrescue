package com.meowrescue.game.core.puzzle

import com.meowrescue.game.puzzle.engine.PuzzleGrid
import com.meowrescue.game.puzzle.model.ExitDirection
import com.meowrescue.game.puzzle.model.PuzzleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PuzzleSolver.solveSteps].
 *
 * Implements: design/gdd/puzzle-system.md — Acceptance Criteria (solver contract)
 * Story: 002 — PuzzleSolver solveSteps Full Solution Path Reconstruction
 *
 * All fixtures are hand-crafted 5x5 grids with a RIGHT exit on row 2.
 * No random seeds, no time-dependent assertions — all tests are deterministic.
 *
 * Grid layout reference (5x5, exitRow=2, exitDirection=RIGHT):
 *   Columns: 0  1  2  3  4
 *   Row 2 is the cat's escape row; col 4 is the rightmost column.
 *   "Solved" means cat's right edge == cols (5), i.e. cat placed at col 4
 *   with length 1, or col 3 with length 2, etc.
 *
 * AC-3 note: MoveApplier does not yet exist. Path correctness is verified by:
 *   (a) list size matching the known optimal depth, and
 *   (b) each MoveStep having a blockId that matches a block placed in the grid.
 */
class PuzzleSolverSolveStepsTest {

    private val solver = PuzzleSolver()

    // ── Fixture helpers ──────────────────────────────────────────────────────

    /**
     * Builds a plain 5x5 grid with a RIGHT exit on row 2.
     * No special features (no key/lock, no checkpoint, no portals).
     */
    private fun baseGrid(): PuzzleGrid =
        PuzzleGrid(rows = 5, cols = 5, exitRow = 2, exitDirection = ExitDirection.RIGHT)

    /**
     * Places [block] on [grid] and asserts success.
     * Returns [grid] for chaining.
     */
    private fun place(grid: PuzzleGrid, block: PuzzleBlock): PuzzleGrid {
        check(grid.placeBlock(block)) {
            "Test fixture error: failed to place block id=${block.id} at (${block.row},${block.col})"
        }
        return grid
    }

    /**
     * One-move solvable: cat at (2,3), length=1, horizontal.
     * Exit condition: catCol + length == 5  →  3 + 1 = 4, needs one slide right by 1.
     *
     * Layout row 2:  . . . C .   (C = cat, exit is right edge)
     * Cat slides right 1 cell → col 4, then col+length=5 == cols → solved.
     */
    private fun oneMoveSolvableGrid(): PuzzleGrid {
        val grid = baseGrid()
        place(grid, PuzzleBlock(id = 0, row = 2, col = 3, length = 1, isHorizontal = true, isCat = true))
        return grid
    }

    /**
     * Already-solved: cat at (2,4), length=1.
     * catCol + length = 4 + 1 = 5 == cols → solved on initial state.
     */
    private fun alreadySolvedGrid(): PuzzleGrid {
        val grid = baseGrid()
        place(grid, PuzzleBlock(id = 0, row = 2, col = 4, length = 1, isHorizontal = true, isCat = true))
        return grid
    }

    /**
     * Two-move solvable:
     *   - Cat at (2,1), length=1, horizontal.
     *   - Vertical blocker at (1,3), length=2 (occupies rows 1 and 2, col 3).
     *     Blocker is vertical so it can slide up or down to clear row 2.
     *
     * Layout:
     *   Row 1: . . . B .
     *   Row 2: . C . B .   ← blocker occupies col 3 on cat's row
     *
     * Move 1: blocker slides up 1 (to rows 0-1), clearing col 3 on row 2.
     * Move 2: cat slides right from col 1 → col 4.
     */
    private fun twoMoveSolvableGrid(): PuzzleGrid {
        val grid = baseGrid()
        place(grid, PuzzleBlock(id = 0, row = 2, col = 1, length = 1, isHorizontal = true, isCat = true))
        place(grid, PuzzleBlock(id = 1, row = 1, col = 3, length = 2, isHorizontal = false, isCat = false))
        return grid
    }

    /**
     * Unsolvable: cat at (2,0), four wall blocks sealing cols 1-4 on row 2.
     * The cat cannot move right and cannot exit left (it's already at col 0,
     * and the exit is RIGHT). No other moves unblock it.
     *
     * Layout row 2:  C W W W W   (W = wall, immovable)
     */
    private fun unsolvableGrid(): PuzzleGrid {
        val grid = baseGrid()
        place(grid, PuzzleBlock(id = 0, row = 2, col = 0, length = 1, isHorizontal = true, isCat = true))
        place(grid, PuzzleBlock(id = 1, row = 2, col = 1, length = 1, isHorizontal = true, isWall = true))
        place(grid, PuzzleBlock(id = 2, row = 2, col = 2, length = 1, isHorizontal = true, isWall = true))
        place(grid, PuzzleBlock(id = 3, row = 2, col = 3, length = 1, isHorizontal = true, isWall = true))
        place(grid, PuzzleBlock(id = 4, row = 2, col = 4, length = 1, isHorizontal = true, isWall = true))
        return grid
    }

    // ── AC-1: solveSteps returns non-null list for solvable puzzles ───────────

    /**
     * AC-1: A one-move solvable puzzle must produce a non-null list of size 1.
     */
    @Test
    fun test_solveSteps_oneMovePuzzle_returnsNonNullListOfSizeOne() {
        val grid = oneMoveSolvableGrid()
        val steps = solver.solveSteps(grid)
        assertNotNull("solveSteps must return non-null for a solvable puzzle", steps)
        assertEquals("One-move puzzle must produce a path of length 1", 1, steps!!.size)
    }

    /**
     * AC-1: A two-move solvable puzzle must produce a non-null list of size 2.
     */
    @Test
    fun test_solveSteps_twoMovePuzzle_returnsNonNullListOfSizeTwo() {
        val grid = twoMoveSolvableGrid()
        val steps = solver.solveSteps(grid)
        assertNotNull("solveSteps must return non-null for a solvable puzzle", steps)
        assertEquals("Two-move puzzle must produce a path of length 2", 2, steps!!.size)
    }

    /**
     * AC-1: An already-solved grid must produce a non-null empty list (depth 0).
     */
    @Test
    fun test_solveSteps_alreadySolved_returnsNonNullEmptyList() {
        val grid = alreadySolvedGrid()
        val steps = solver.solveSteps(grid)
        assertNotNull("solveSteps must return non-null (empty list) for an already-solved grid", steps)
        assertEquals("Already-solved grid must produce an empty path", 0, steps!!.size)
    }

    // ── AC-2: solveSteps path size agrees with solveFast depth ───────────────

    /**
     * AC-2: For a one-move puzzle, solveSteps(grid).size == solveFast(grid).
     */
    @Test
    fun test_solveSteps_oneMovePuzzle_pathSizeMatchesSolveFast() {
        val grid = oneMoveSolvableGrid()
        val fastDepth = solver.solveFast(grid)
        val steps = solver.solveSteps(grid)
        assertNotNull("solveSteps must not return null for a solvable puzzle", steps)
        assertEquals(
            "solveSteps path size must equal solveFast depth for same grid (one-move)",
            fastDepth, steps!!.size
        )
    }

    /**
     * AC-2: For a two-move puzzle, solveSteps(grid).size == solveFast(grid).
     */
    @Test
    fun test_solveSteps_twoMovePuzzle_pathSizeMatchesSolveFast() {
        val grid = twoMoveSolvableGrid()
        val fastDepth = solver.solveFast(grid)
        val steps = solver.solveSteps(grid)
        assertNotNull("solveSteps must not return null for a solvable puzzle", steps)
        assertEquals(
            "solveSteps path size must equal solveFast depth for same grid (two-move)",
            fastDepth, steps!!.size
        )
    }

    /**
     * AC-2: For an already-solved grid (depth 0), both return 0 / empty list.
     */
    @Test
    fun test_solveSteps_alreadySolved_pathSizeMatchesSolveFastDepthZero() {
        val grid = alreadySolvedGrid()
        val fastDepth = solver.solveFast(grid)
        val steps = solver.solveSteps(grid)
        assertNotNull("solveSteps must not return null for an already-solved grid", steps)
        assertEquals(
            "solveSteps path size must equal solveFast depth for already-solved grid",
            fastDepth, steps!!.size
        )
        assertEquals("solveFast must return 0 for an already-solved grid", 0, fastDepth)
    }

    // ── AC-3: Path leads to win state (validated via size + valid block IDs) ──

    /**
     * AC-3: The returned path has the correct length and each MoveStep references
     * a block that exists in the grid.
     *
     * Since MoveApplier does not yet exist, correctness is verified by:
     *   (a) path length matches known optimal depth, and
     *   (b) each step's blockId matches the id of a block placed in the grid.
     */
    @Test
    fun test_solveSteps_oneMovePuzzle_eachMoveHasValidBlockId() {
        val grid = oneMoveSolvableGrid()
        val validIds = grid.blocks.map { it.id }.toSet()
        val steps = solver.solveSteps(grid)
        assertNotNull("Steps must not be null for a solvable puzzle", steps)
        assertEquals("Path length must match optimal depth", 1, steps!!.size)
        for (step in steps) {
            assertTrue(
                "MoveStep.blockId ${step.blockId} must reference a block in the grid (valid ids: $validIds)",
                step.blockId in validIds
            )
        }
        // The cat is the only block and can only move right — assert exact direction
        assertEquals("Cat's single move must be rightward (dCol=1)", 1, steps[0].dCol)
        assertEquals("Cat's single move must not be vertical (dRow=0)", 0, steps[0].dRow)
    }

    /**
     * AC-3: Two-move puzzle — both steps reference valid block IDs.
     * Additionally, the first step must move the blocker (id=1) and the second
     * step must move the cat (id=0), or the cat must be the one moved in a step
     * that achieves the win. At minimum, at least one step must reference the cat.
     */
    @Test
    fun test_solveSteps_twoMovePuzzle_eachMoveHasValidBlockIdAndCatIsInPath() {
        val grid = twoMoveSolvableGrid()
        val validIds = grid.blocks.map { it.id }.toSet()
        val steps = solver.solveSteps(grid)
        assertNotNull("Steps must not be null for a solvable puzzle", steps)
        assertEquals("Path length must match optimal depth of 2", 2, steps!!.size)
        for (step in steps) {
            assertTrue(
                "MoveStep.blockId ${step.blockId} must reference a block in the grid (valid ids: $validIds)",
                step.blockId in validIds
            )
        }
        // The cat (id=0) must appear somewhere in the path — it's required to reach the exit
        val catId = grid.blocks.first { it.isCat }.id
        assertTrue(
            "At least one MoveStep must move the cat (id=$catId) to reach the exit",
            steps.any { it.blockId == catId }
        )
    }

    /**
     * AC-3: Verify that applying the path steps effectively reaches the win state
     * by confirming solveFast on the grid after manually validating path structure.
     * Since MoveApplier does not exist yet, this test confirms that the path returned
     * has exactly the optimal depth that solveFast reports, ensuring they describe
     * the same solution.
     */
    @Test
    fun test_solveSteps_pathLengthEqualsOptimalDepth_impliesWinReachability() {
        val grid = twoMoveSolvableGrid()
        val optimalDepth = solver.solveFast(grid)
        val steps = solver.solveSteps(grid)
        assertNotNull("Steps must not be null for a solvable puzzle", steps)
        assertEquals(
            "Path size ${ steps!!.size } must equal optimal depth $optimalDepth — " +
            "any shorter path would not reach WinState, any longer path would not be optimal",
            optimalDepth, steps.size
        )
        assertTrue("Optimal depth must be positive for a non-trivial solvable puzzle", optimalDepth > 0)
    }

    // ── AC-4: Returns null when state limit exceeded ──────────────────────────

    /**
     * AC-4: solveSteps with maxStates=2 on a multi-move puzzle returns null.
     * The two-move fixture needs more than 2 visited states to find the solution.
     */
    @Test
    fun test_solveSteps_maxStatesTwoOnTwoMovePuzzle_returnsNull() {
        val grid = twoMoveSolvableGrid()
        val steps = solver.solveSteps(grid, maxStates = 2)
        assertNull(
            "solveSteps must return null when the state budget is exhausted before finding a solution",
            steps
        )
    }

    /**
     * AC-4: maxStates=1 on any unsolved grid returns null (no neighbours explored).
     */
    @Test
    fun test_solveSteps_maxStatesOne_returnsNull() {
        val grid = oneMoveSolvableGrid()
        val steps = solver.solveSteps(grid, maxStates = 1)
        assertNull(
            "solveSteps with maxStates=1 must return null — initial state is added but no neighbours explored",
            steps
        )
    }

    /**
     * AC-4: The same puzzle succeeds with the default budget but fails with a tight
     * budget, confirming that null is specifically a budget-exhaustion signal.
     */
    @Test
    fun test_solveSteps_budgetExhausted_returnsNullWhileFullBudgetSucceeds() {
        val grid = twoMoveSolvableGrid()
        val stepsAborted = solver.solveSteps(grid, maxStates = 2)
        val stepsFull = solver.solveSteps(grid, maxStates = PuzzleConstants.MAX_STATES_DEFAULT)
        assertNull("Tight budget must cause null return", stepsAborted)
        assertNotNull("Default budget must find a solution", stepsFull)
        assertEquals("Default-budget path must have length 2", 2, stepsFull!!.size)
    }

    // ── AC-5: Returns null for an unsolvable puzzle ───────────────────────────

    /**
     * AC-5: solveSteps on the unsolvable fixture returns null.
     */
    @Test
    fun test_solveSteps_unsolvablePuzzle_returnsNull() {
        val grid = unsolvableGrid()
        val steps = solver.solveSteps(grid)
        assertNull(
            "solveSteps must return null for a provably unsolvable puzzle",
            steps
        )
    }

    /**
     * AC-5: solveFast and solveSteps agree — both return -1 / null for unsolvable.
     */
    @Test
    fun test_solveSteps_unsolvable_agreeWithSolveFast() {
        val grid = unsolvableGrid()
        val fastDepth = solver.solveFast(grid)
        val steps = solver.solveSteps(grid)
        assertEquals("solveFast must return -1 for unsolvable puzzle", -1, fastDepth)
        assertNull("solveSteps must return null when solveFast returns -1", steps)
    }

    // ── AC-6: Pure function — identical inputs produce identical outputs ───────

    /**
     * AC-6: Two consecutive calls with the same grid return equal lists.
     */
    @Test
    fun test_solveSteps_isDeterministic_sameInputSameOutput() {
        val grid = twoMoveSolvableGrid()
        val first = solver.solveSteps(grid)
        val second = solver.solveSteps(grid)
        assertNotNull("First call must return a non-null path", first)
        assertNotNull("Second call must return a non-null path", second)
        assertEquals(
            "solveSteps must be deterministic: two calls with same input must return equal path sizes",
            first!!.size, second!!.size
        )
        for (i in first.indices) {
            assertEquals(
                "MoveStep[$i].blockId must be identical across calls",
                first[i].blockId, second[i].blockId
            )
            assertEquals(
                "MoveStep[$i].dRow must be identical across calls",
                first[i].dRow, second[i].dRow
            )
            assertEquals(
                "MoveStep[$i].dCol must be identical across calls",
                first[i].dCol, second[i].dCol
            )
        }
    }

    /**
     * AC-6: Determinism also holds for one-move puzzles.
     */
    @Test
    fun test_solveSteps_isDeterministic_oneMovePuzzle() {
        val grid = oneMoveSolvableGrid()
        val first = solver.solveSteps(grid)
        val second = solver.solveSteps(grid)
        assertNotNull("First call must be non-null", first)
        assertNotNull("Second call must be non-null", second)
        assertEquals("Path sizes must be equal across calls", first!!.size, second!!.size)
        assertEquals(
            "MoveStep[0].blockId must be identical across calls",
            first[0].blockId, second[0].blockId
        )
        assertEquals(
            "MoveStep[0].dRow must be identical across calls",
            first[0].dRow, second[0].dRow
        )
        assertEquals(
            "MoveStep[0].dCol must be identical across calls",
            first[0].dCol, second[0].dCol
        )
    }

    /**
     * Precondition: maxStates=0 must throw IllegalArgumentException.
     * The bfsCore has require(maxStates > 0) — callers must not pass 0.
     */
    @Test(expected = IllegalArgumentException::class)
    fun test_solveSteps_maxStatesZero_throwsIllegalArgumentException() {
        solver.solveSteps(oneMoveSolvableGrid(), maxStates = 0)
    }

    /**
     * AC-6: Null results are also deterministic — unsolvable grids always return null.
     */
    @Test
    fun test_solveSteps_isDeterministic_unsolvableAlwaysReturnsNull() {
        val grid = unsolvableGrid()
        val first = solver.solveSteps(grid)
        val second = solver.solveSteps(grid)
        assertNull("First call on unsolvable grid must return null", first)
        assertNull("Second call on unsolvable grid must return null", second)
    }
}
