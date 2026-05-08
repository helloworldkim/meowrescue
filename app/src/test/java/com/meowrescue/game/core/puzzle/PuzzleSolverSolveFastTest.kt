package com.meowrescue.game.core.puzzle

import com.meowrescue.game.puzzle.engine.PuzzleGrid
import com.meowrescue.game.puzzle.model.ExitDirection
import com.meowrescue.game.puzzle.model.PuzzleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PuzzleSolver.solveFast].
 *
 * Implements: design/gdd/puzzle-system.md — Acceptance Criteria (solver contract)
 * Story: 001 — PuzzleSolver solveFast BFS Core with Configurable State Limits
 *
 * All fixtures are hand-crafted 5x5 grids with a RIGHT exit on row 2.
 * No random seeds, no time-dependent assertions — all tests are deterministic.
 *
 * Grid layout reference (5x5, exitRow=2, exitDirection=RIGHT):
 *   Columns: 0  1  2  3  4
 *   Row 2 is the cat's escape row; col 4 is the rightmost column.
 *   "Solved" means cat's right edge == cols (5), i.e. cat placed at col 4
 *   with length 1, or col 3 with length 2, etc.
 */
class PuzzleSolverSolveFastTest {

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

    // ── Tests ────────────────────────────────────────────────────────────────

    /**
     * A known solvable puzzle must return a positive depth.
     * The two-move fixture requires exactly 2 BFS moves.
     */
    @Test
    fun test_solveFast_knownSolvable_returnsCorrectDepth() {
        val grid = twoMoveSolvableGrid()
        val depth = solver.solveFast(grid)
        assertEquals("Expected solution depth 2 for two-move fixture", 2, depth)
    }

    /**
     * A grid where the cat is already at the exit must return 0.
     */
    @Test
    fun test_solveFast_alreadySolved_returnsZero() {
        val grid = alreadySolvedGrid()
        val depth = solver.solveFast(grid)
        assertEquals("Already-solved grid should return depth 0", 0, depth)
    }

    /**
     * A puzzle that is provably unsolvable must return -1.
     */
    @Test
    fun test_solveFast_unsolvable_returnsNegativeOne() {
        val grid = unsolvableGrid()
        val depth = solver.solveFast(grid)
        assertEquals("Unsolvable puzzle should return -1", -1, depth)
    }

    /**
     * maxStates = 1: the initial state is added to visited (size = 1), then
     * the loop guard `visited.size < 1` is immediately false — no neighbours
     * are ever explored. Returns -1 for any unsolved grid.
     */
    @Test
    fun test_solveFast_maxStatesOne_returnsNegativeOne() {
        val grid = oneMoveSolvableGrid()  // solvable in 1 move with full budget
        val depth = solver.solveFast(grid, maxStates = 1)
        assertEquals("maxStates=1 should yield -1 (no neighbours explored)", -1, depth)
    }

    /**
     * Budget-abort mid-search: the two-move fixture requires exploring at least
     * a few states before the solution is reached. maxStates = 2 allows only 2
     * visited states total — not enough to find the solution — so the solver
     * must return -1, but the same puzzle with the default budget must return 2.
     */
    @Test
    fun test_solveFast_budgetAbortsMidSearch_returnsNegativeOne() {
        val grid = twoMoveSolvableGrid()
        val depthAborted = solver.solveFast(grid, maxStates = 2)
        val depthFull    = solver.solveFast(grid, maxStates = PuzzleConstants.MAX_STATES_DEFAULT)
        assertEquals("Truncated budget should abort mid-search and return -1", -1, depthAborted)
        assertEquals("Full budget should solve the same puzzle at depth 2", 2, depthFull)
    }

    /**
     * With the default state limit (150 K) a normal puzzle must be solved.
     */
    @Test
    fun test_solveFast_withDefaultStateLimit_solvesNormalPuzzle() {
        val grid = twoMoveSolvableGrid()
        val depth = solver.solveFast(grid, maxStates = PuzzleConstants.MAX_STATES_DEFAULT)
        assertTrue("Default state limit should solve a 2-move puzzle; got $depth", depth > 0)
    }

    /**
     * With the checkpoint state limit (250 K) the same puzzle must also be solved
     * and the result must equal the result from the default limit.
     */
    @Test
    fun test_solveFast_withCheckpointStateLimit_allowsDeeperSearch() {
        val grid = twoMoveSolvableGrid()
        val depthDefault = solver.solveFast(grid, maxStates = PuzzleConstants.MAX_STATES_DEFAULT)
        val depthCheckpoint = solver.solveFast(grid, maxStates = PuzzleConstants.MAX_STATES_CHECKPOINT)
        assertEquals(
            "Checkpoint limit should produce the same optimal depth as default for a simple puzzle",
            depthDefault, depthCheckpoint
        )
        assertTrue("Depth must be positive for a solvable puzzle", depthCheckpoint > 0)
    }

    /**
     * Two calls with identical inputs must return the same depth.
     * Verifies the solver is a pure, deterministic function.
     */
    @Test
    fun test_solveFast_isDeterministic_sameInputSameOutput() {
        val grid = twoMoveSolvableGrid()
        val first = solver.solveFast(grid)
        val second = solver.solveFast(grid)
        assertEquals("solveFast must be deterministic: two calls with same input must return same depth", first, second)
    }

    /**
     * A puzzle solvable in exactly one move must return 1.
     */
    @Test
    fun test_solveFast_singleMoveSolution_returnsOne() {
        val grid = oneMoveSolvableGrid()
        val depth = solver.solveFast(grid)
        assertEquals("One-move puzzle should return depth 1", 1, depth)
    }

    /**
     * Verifies all three state-limit constants from [PuzzleConstants] are distinct
     * and in ascending order as the design document specifies.
     * (DEFAULT < CHECKPOINT < MULTI_CAT)
     */
    @Test
    fun test_solveFast_stateLimitConstants_areOrderedCorrectly() {
        assertTrue(
            "MAX_STATES_DEFAULT (${PuzzleConstants.MAX_STATES_DEFAULT}) must be < MAX_STATES_CHECKPOINT (${PuzzleConstants.MAX_STATES_CHECKPOINT})",
            PuzzleConstants.MAX_STATES_DEFAULT < PuzzleConstants.MAX_STATES_CHECKPOINT
        )
        assertTrue(
            "MAX_STATES_CHECKPOINT (${PuzzleConstants.MAX_STATES_CHECKPOINT}) must be < MAX_STATES_MULTI_CAT (${PuzzleConstants.MAX_STATES_MULTI_CAT})",
            PuzzleConstants.MAX_STATES_CHECKPOINT < PuzzleConstants.MAX_STATES_MULTI_CAT
        )
    }
}
