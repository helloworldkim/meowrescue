package com.meowrescue.game.core.puzzle

import com.meowrescue.game.puzzle.engine.PuzzleGrid
import com.meowrescue.game.puzzle.model.ExitDirection
import com.meowrescue.game.puzzle.model.PuzzleBlock
import java.util.ArrayDeque

/**
 * BFS-based puzzle solver for the Meow Rescue sliding-block puzzle.
 *
 * Implements: design/gdd/puzzle-system.md — Detailed Rules / solver contract
 *
 * This class is a pure function — deterministic, no side effects, no
 * Android UI imports. All numeric limits are caller-supplied; use
 * [PuzzleConstants.MAX_STATES_DEFAULT] for normal puzzles.
 *
 * Dispatcher responsibility: callers must invoke on [kotlinx.coroutines.Dispatchers.Default]
 * for CPU-bound BFS work; this class does not manage threading.
 *
 * Migration note: this is the Core-layer successor to
 * com.meowrescue.game.puzzle.engine.PuzzleSolver (ADR-0007 step 6 will
 * remove the old copy once all call sites are migrated).
 */
class PuzzleSolver {

    data class MoveStep(val blockId: Int, val dRow: Int, val dCol: Int)

    private class BlockInfo(
        val length: Int, val isHorizontal: Boolean, val isCat: Boolean,
        val isKey: Boolean = false, val isWall: Boolean = false, val linkId: Int = -1
    )

    private data class BfsResult(val depth: Int, val path: List<MoveStep>?)

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the optimal BFS solution depth (≥ 1) for a solvable puzzle,
     * 0 if the grid is already in a solved state, or -1 if unsolvable or if
     * the visited-state count exceeds [maxStates] before a solution is found.
     *
     * @param grid      The puzzle grid to solve (not mutated).
     * @param maxStates Maximum number of unique grid states to enqueue (visited-set size)
     *                  before aborting with -1. Must be > 0.
     *                  Use [PuzzleConstants.MAX_STATES_DEFAULT],
     *                  [PuzzleConstants.MAX_STATES_CHECKPOINT], or
     *                  [PuzzleConstants.MAX_STATES_MULTI_CAT] as appropriate.
     *                  A value of 1 means only the initial state is ever added — no
     *                  neighbours are explored, so any unsolved grid returns -1 immediately.
     */
    fun solveFast(grid: PuzzleGrid, maxStates: Int = PuzzleConstants.MAX_STATES_DEFAULT): Int =
        bfsCore(grid, false, maxStates).depth

    @Deprecated(
        message = "Use solveFast; this alias is temporary for migration from game.puzzle.engine.PuzzleSolver",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("solveFast(grid, maxStates)")
    )
    fun solve(grid: PuzzleGrid, maxStates: Int = PuzzleConstants.MAX_STATES_DEFAULT): Int =
        solveFast(grid, maxStates)

    /**
     * Returns the optimal solution path as a list of [MoveStep], or null if
     * unsolvable or if [maxStates] is exceeded before a solution is found.
     * Each step describes moving a block by (dRow, dCol) grid cells.
     *
     * @param grid      The puzzle grid to solve (not mutated).
     * @param maxStates Maximum BFS state budget (visited-set size). See [solveFast] for guidance.
     *                  Note: path-tracking BFS stores one [IntArray] + parent pointer per visited
     *                  state. At [PuzzleConstants.MAX_STATES_MULTI_CAT] (300K) this can allocate
     *                  ~20–30 MB transiently. Run on a background dispatcher, not Main.
     */
    fun solveSteps(grid: PuzzleGrid, maxStates: Int = PuzzleConstants.MAX_STATES_DEFAULT): List<MoveStep>? =
        bfsCore(grid, true, maxStates).path

    // ── Quality checker ──────────────────────────────────────────────────────

    fun isQualityPuzzle(grid: PuzzleGrid, stage: Int, optimalMoves: Int): Boolean {
        if (optimalMoves < 2) return false

        val cat = grid.blocks.firstOrNull { it.isCat } ?: return false
        val gridArr = grid.getGrid()

        val directBlockers = findDirectBlockers(grid, cat, gridArr)

        if (directBlockers.isNotEmpty()) {
            val exitAxisH = (grid.exitDirection == ExitDirection.RIGHT || grid.exitDirection == ExitDirection.LEFT)
            val allSameOrientation = directBlockers.all { it.isHorizontal == exitAxisH }
            if (allSameOrientation) return false
        }

        if (stage > 5 && directBlockers.size <= 1) return false
        if (stage > 30 && directBlockers.size <= 2) return false

        if (directBlockers.size >= 2) {
            val anyTrapped = directBlockers.any { blocker ->
                isBlockerTrapped(blocker, gridArr, grid.rows, grid.cols)
            }
            if (!anyTrapped) return false
        }

        return true
    }

    fun findDirectBlockers(grid: PuzzleGrid, cat: PuzzleBlock, gridArr: Array<IntArray>): List<PuzzleBlock> {
        val blockerIds = mutableSetOf<Int>()
        when (grid.exitDirection) {
            ExitDirection.RIGHT -> {
                for (c in (cat.col + cat.length) until grid.cols) {
                    val id = gridArr[cat.row][c]
                    if (id != -1 && id != cat.id) blockerIds.add(id)
                }
            }
            ExitDirection.LEFT -> {
                for (c in 0 until cat.col) {
                    val id = gridArr[cat.row][c]
                    if (id != -1 && id != cat.id) blockerIds.add(id)
                }
            }
            ExitDirection.BOTTOM -> {
                for (r in (cat.row + cat.length) until grid.rows) {
                    val id = gridArr[r][cat.col]
                    if (id != -1 && id != cat.id) blockerIds.add(id)
                }
            }
            ExitDirection.TOP -> {
                for (r in 0 until cat.row) {
                    val id = gridArr[r][cat.col]
                    if (id != -1 && id != cat.id) blockerIds.add(id)
                }
            }
        }
        return grid.blocks.filter { it.id in blockerIds }
    }

    fun isBlockerTrapped(block: PuzzleBlock, gridArr: Array<IntArray>, rows: Int, cols: Int): Boolean {
        if (block.isHorizontal) {
            val canLeft = block.col > 0 && gridArr[block.row][block.col - 1] == -1
            val endCol = block.col + block.length
            val canRight = endCol < cols && gridArr[block.row][endCol] == -1
            val horizTrapped = !canLeft && !canRight
            if (block.length == 1) {
                val canUp = block.row > 0 && gridArr[block.row - 1][block.col] == -1
                val canDown = block.row + 1 < rows && gridArr[block.row + 1][block.col] == -1
                return horizTrapped && !canUp && !canDown
            }
            return horizTrapped
        } else {
            val canUp = block.row > 0 && gridArr[block.row - 1][block.col] == -1
            val endRow = block.row + block.length
            val canDown = endRow < rows && gridArr[endRow][block.col] == -1
            val vertTrapped = !canUp && !canDown
            if (block.length == 1) {
                val canLeft = block.col > 0 && gridArr[block.row][block.col - 1] == -1
                val canRight = block.col + 1 < cols && gridArr[block.row][block.col + 1] == -1
                return vertTrapped && !canLeft && !canRight
            }
            return vertTrapped
        }
    }

    fun hasClearPath(grid: PuzzleGrid): Boolean {
        val gridArr = grid.getGrid()
        val cat = grid.blocks.firstOrNull { it.isCat } ?: return true
        return when (grid.exitDirection) {
            ExitDirection.RIGHT -> {
                val startCol = cat.col + cat.length
                (startCol until grid.cols).all { c -> gridArr[cat.row][c] == -1 }
            }
            ExitDirection.LEFT -> {
                (0 until cat.col).all { c -> gridArr[cat.row][c] == -1 }
            }
            ExitDirection.BOTTOM -> {
                val startRow = cat.row + cat.length
                (startRow until grid.rows).all { r -> gridArr[r][cat.col] == -1 }
            }
            ExitDirection.TOP -> {
                (0 until cat.row).all { r -> gridArr[r][cat.col] == -1 }
            }
        }
    }

    // ── Core BFS ──────────────────────────────────────────────────────────────

    /**
     * @param maxStates Caller-supplied BFS state budget. The loop exits (returning
     *                  depth = -1) when [visited.size >= maxStates] before a
     *                  solution is found.
     */
    private fun bfsCore(grid: PuzzleGrid, trackPath: Boolean, maxStates: Int): BfsResult {
        require(maxStates > 0) { "maxStates must be > 0, got $maxStates" }
        val blocks = grid.blocks.sortedBy { it.id }
        val n = blocks.size
        val rows = grid.rows
        val cols = grid.cols
        val exitDir = grid.exitDirection
        val exitRow = grid.exitRow
        val exitCol = grid.exitCol

        val infos = Array(n) { BlockInfo(blocks[it].length, blocks[it].isHorizontal, blocks[it].isCat,
            blocks[it].isKey, blocks[it].isWall, blocks[it].linkId) }
        val catIndices = (0 until n).filter { infos[it].isCat }
        if (catIndices.isEmpty()) return BfsResult(-1, null)
        val catIdx = catIndices[0]

        val keyIdx = if (grid.hasKeyLock) infos.indexOfFirst { it.isKey } else -1
        val lockPos = if (keyIdx >= 0) grid.lockRow * cols + grid.lockCol else -1

        val hasCheckpoint = grid.hasCheckpoint
        val cpRow = grid.checkpointRow
        val cpCol = grid.checkpointCol
        val cpStateIdx = if (hasCheckpoint) n else -1
        val stateSize = n + (if (hasCheckpoint) 1 else 0)

        val portalA = grid.portalA
        val portalB = grid.portalB
        val exitRow2 = grid.exitRow2
        val exitCol2 = grid.exitCol2
        val exitDir2 = grid.exitDirection2

        val linkPartner = IntArray(n) { -1 }
        for (i in 0 until n) {
            if (infos[i].linkId >= 0 && linkPartner[i] == -1) {
                for (j in i + 1 until n) {
                    if (infos[j].linkId == infos[i].linkId) {
                        linkPartner[i] = j; linkPartner[j] = i; break
                    }
                }
            }
        }

        val initState = IntArray(stateSize) { if (it < n) blocks[it].row * cols + blocks[it].col else 0 }
        if (hasCheckpoint && cpStateIdx >= 0) {
            val catPos = initState[catIdx]
            if (catPos / cols == cpRow && catPos % cols == cpCol) initState[cpStateIdx] = 1
        }

        if (isSolvedState(initState, infos, catIndices, rows, cols, exitDir, exitRow, exitCol, keyIdx, lockPos, cpStateIdx, exitRow2, exitCol2, exitDir2)) {
            return if (trackPath) BfsResult(0, emptyList()) else BfsResult(0, null)
        }

        val hasLinkedBlocks = infos.any { it.linkId >= 0 }

        if (!trackPath) {
            // Depth-only BFS
            val visited = HashSet<Long>(4096)
            visited.add(hashState(initState))
            val queue = ArrayDeque<Pair<IntArray, Int>>(4096)
            queue.add(initState to 0)

            while (queue.isNotEmpty() && visited.size < maxStates) {
                val (state, depth) = queue.poll() ?: break
                if (depth >= MAX_BFS_DEPTH) continue

                val g = buildGrid(state, infos, rows, cols)
                val processedLinks = if (hasLinkedBlocks) mutableSetOf<Int>() else null

                for (i in 0 until n) {
                    val info = infos[i]
                    if (info.isWall) continue
                    if (info.linkId >= 0) {
                        if (processedLinks?.contains(info.linkId) == true) continue
                        processedLinks?.add(info.linkId)
                    }
                    val partnerId = linkPartner[i]
                    val tryHoriz = info.isHorizontal || info.length == 1
                    val tryVert  = !info.isHorizontal || info.length == 1

                    for (dir in DIRECTIONS) {
                        val dRow = dir[0]; val dCol = dir[1]
                        val isHoriz = dCol != 0
                        if (isHoriz && !tryHoriz) continue
                        if (!isHoriz && !tryVert) continue
                        val maxDist = if (isHoriz) cols else rows
                        for (d in 1..maxDist) {
                            val ns = tryMoveInDirection(
                                state, i, infos, dRow * d, dCol * d, d,
                                g, rows, cols, partnerId, linkPartner, catIndices,
                                hasCheckpoint, cpStateIdx, cpRow, cpCol, portalA, portalB
                            ) ?: break
                            if (isSolvedState(ns, infos, catIndices, rows, cols, exitDir, exitRow, exitCol, keyIdx, lockPos, cpStateIdx, exitRow2, exitCol2, exitDir2)) return BfsResult(depth + 1, null)
                            val h = hashState(ns)
                            if (h !in visited) { visited.add(h); queue.add(ns to depth + 1) }
                        }
                    }
                }
            }
            return BfsResult(-1, null)
        } else {
            // Path-tracking BFS
            data class Node(val state: IntArray, val parent: Int, val move: MoveStep?)

            val visited = HashMap<Long, Int>(4096)
            val nodes = mutableListOf<Node>()
            nodes.add(Node(initState, -1, null))
            visited[hashState(initState)] = 0

            val queue = ArrayDeque<Pair<Int, Int>>(4096)
            queue.add(0 to 0)
            var solvedNodeIdx = -1

            outer@ while (queue.isNotEmpty() && visited.size < maxStates) {
                val (nodeIdx, depth) = queue.poll() ?: break
                if (depth >= MAX_BFS_DEPTH) continue
                val state = nodes[nodeIdx].state
                val g = buildGrid(state, infos, rows, cols)
                val processedLinks = if (hasLinkedBlocks) mutableSetOf<Int>() else null

                for (i in 0 until n) {
                    val info = infos[i]
                    if (info.isWall) continue
                    if (info.linkId >= 0) {
                        if (processedLinks?.contains(info.linkId) == true) continue
                        processedLinks?.add(info.linkId)
                    }
                    val partnerId = linkPartner[i]
                    val tryHoriz = info.isHorizontal || info.length == 1
                    val tryVert  = !info.isHorizontal || info.length == 1

                    for (dir in DIRECTIONS) {
                        val dRow = dir[0]; val dCol = dir[1]
                        val isHoriz = dCol != 0
                        if (isHoriz && !tryHoriz) continue
                        if (!isHoriz && !tryVert) continue
                        val maxDist = if (isHoriz) cols else rows
                        for (d in 1..maxDist) {
                            val ns = tryMoveInDirection(
                                state, i, infos, dRow * d, dCol * d, d,
                                g, rows, cols, partnerId, linkPartner, catIndices,
                                hasCheckpoint, cpStateIdx, cpRow, cpCol, portalA, portalB
                            ) ?: break
                            val h = hashState(ns)
                            if (h !in visited) {
                                val newIdx = nodes.size
                                nodes.add(Node(ns, nodeIdx, MoveStep(blocks[i].id, dRow * d, dCol * d)))
                                visited[h] = newIdx
                                if (isSolvedState(ns, infos, catIndices, rows, cols, exitDir, exitRow, exitCol, keyIdx, lockPos, cpStateIdx, exitRow2, exitCol2, exitDir2)) {
                                    solvedNodeIdx = newIdx; break@outer
                                }
                                queue.add(newIdx to depth + 1)
                            }
                        }
                    }
                }
            }

            if (solvedNodeIdx < 0) return BfsResult(-1, null)

            val path = mutableListOf<MoveStep>()
            var idx = solvedNodeIdx
            while (idx > 0) {
                val node = nodes[idx]
                node.move?.let { path.add(0, it) }
                idx = node.parent
            }
            return BfsResult(path.size, path)
        }
    }

    // ── BFS helpers ──────────────────────────────────────────────────────────

    private fun isCellOccupied(
        state: IntArray, infos: Array<BlockInfo>,
        targetRow: Int, targetCol: Int, cols: Int, excludeIdx: Int
    ): Boolean {
        for (i in infos.indices) {
            if (i == excludeIdx) continue
            val pos = state[i]
            val bRow = pos / cols
            val bCol = pos % cols
            val info = infos[i]
            if (info.isHorizontal) {
                if (bRow == targetRow && targetCol in bCol until (bCol + info.length)) return true
            } else {
                if (bCol == targetCol && targetRow in bRow until (bRow + info.length)) return true
            }
        }
        return false
    }

    private fun tryMoveInDirection(
        state: IntArray, blockIdx: Int, infos: Array<BlockInfo>,
        dRow: Int, dCol: Int, distance: Int,
        g: Array<IntArray>, rows: Int, cols: Int,
        partnerIdx: Int, linkPartner: IntArray,
        catIndices: List<Int>,
        hasCheckpoint: Boolean, cpStateIdx: Int, cpRow: Int, cpCol: Int,
        portalA: Int, portalB: Int
    ): IntArray? {
        val info = infos[blockIdx]
        val pos = state[blockIdx]
        val bRow = pos / cols
        val bCol = pos % cols

        if (dCol > 0) {
            // right
            val nc = bCol + distance
            if (nc + info.length > cols) return null
            if (g[bRow][nc + info.length - 1] != -1 && g[bRow][nc + info.length - 1] != blockIdx
                && (partnerIdx < 0 || g[bRow][nc + info.length - 1] != partnerIdx)) return null
            if (partnerIdx >= 0) {
                val pInfo = infos[partnerIdx]
                if (pInfo.length >= 2 && !pInfo.isHorizontal) return null
                val pPos = state[partnerIdx]; val pCol = pPos % cols
                if (pCol + distance + pInfo.length > cols) return null
                for (dd in 1..distance) {
                    val checkC = pCol + dd + pInfo.length - 1
                    if (checkC >= cols) return null
                    val cell = g[pPos / cols][checkC]
                    if (cell != -1 && cell != partnerIdx && cell != blockIdx) return null
                }
            }
        } else if (dCol < 0) {
            // left
            val nc = bCol + dCol
            if (nc < 0) return null
            if (g[bRow][nc] != -1 && g[bRow][nc] != blockIdx
                && (partnerIdx < 0 || g[bRow][nc] != partnerIdx)) return null
            if (partnerIdx >= 0) {
                val pInfo = infos[partnerIdx]
                if (pInfo.length >= 2 && !pInfo.isHorizontal) return null
                val pPos = state[partnerIdx]; val pCol = pPos % cols
                if (pCol - distance < 0) return null
                for (dd in 1..distance) {
                    val checkC = pCol - dd
                    if (checkC < 0) return null
                    val cell = g[pPos / cols][checkC]
                    if (cell != -1 && cell != partnerIdx && cell != blockIdx) return null
                }
            }
        } else if (dRow > 0) {
            // down
            val endCheck = bRow + info.length - 1 + distance
            if (endCheck >= rows) return null
            if (g[endCheck][bCol] != -1 && g[endCheck][bCol] != blockIdx
                && (partnerIdx < 0 || g[endCheck][bCol] != partnerIdx)) return null
            if (partnerIdx >= 0) {
                val pInfo = infos[partnerIdx]
                if (pInfo.length >= 2 && pInfo.isHorizontal) return null
                val pPos = state[partnerIdx]; val pRow = pPos / cols; val pCol = pPos % cols
                if (pRow + pInfo.length - 1 + distance >= rows) return null
                for (dd in 1..distance) {
                    val checkR = pRow + pInfo.length - 1 + dd
                    if (checkR >= rows) return null
                    val cell = g[checkR][pCol]
                    if (cell != -1 && cell != partnerIdx && cell != blockIdx) return null
                }
            }
        } else {
            // up
            val nr = bRow - distance
            if (nr < 0) return null
            if (g[nr][bCol] != -1 && g[nr][bCol] != blockIdx
                && (partnerIdx < 0 || g[nr][bCol] != partnerIdx)) return null
            if (partnerIdx >= 0) {
                val pInfo = infos[partnerIdx]
                if (pInfo.length >= 2 && pInfo.isHorizontal) return null
                val pPos = state[partnerIdx]; val pRow = pPos / cols; val pCol = pPos % cols
                if (pRow - distance < 0) return null
                for (dd in 1..distance) {
                    val checkR = pRow - dd
                    if (checkR < 0) return null
                    val cell = g[checkR][pCol]
                    if (cell != -1 && cell != partnerIdx && cell != blockIdx) return null
                }
            }
        }

        val ns = state.copyOf()
        ns[blockIdx] = (bRow + dRow) * cols + (bCol + dCol)
        if (partnerIdx >= 0) {
            val pPos = state[partnerIdx]
            ns[partnerIdx] = (pPos / cols + dRow) * cols + (pPos % cols + dCol)
        }

        // Checkpoint tracking
        if (hasCheckpoint && cpStateIdx >= 0 && ns[cpStateIdx] == 0) {
            if (blockIdx in catIndices) {
                val passed = if (dCol > 0) bRow == cpRow && cpCol in bCol..(bCol + distance)
                    else if (dCol < 0) bRow == cpRow && cpCol in (bCol - distance)..bCol
                    else if (dRow > 0) bCol == cpCol && cpRow in bRow..(bRow + distance)
                    else bCol == cpCol && cpRow in (bRow - distance)..bRow
                if (passed) ns[cpStateIdx] = 1
            }
            if (partnerIdx >= 0 && partnerIdx in catIndices) {
                val pPos = state[partnerIdx]; val pRow = pPos / cols; val pCol = pPos % cols
                val passed = if (dCol > 0) pRow == cpRow && cpCol in pCol..(pCol + distance)
                    else if (dCol < 0) pRow == cpRow && cpCol in (pCol - distance)..pCol
                    else if (dRow > 0) pCol == cpCol && cpRow in pRow..(pRow + distance)
                    else pCol == cpCol && cpRow in (pRow - distance)..pRow
                if (passed) ns[cpStateIdx] = 1
            }
        }

        // Portal teleport (cat only)
        if (portalA >= 0 && portalB >= 0 && blockIdx in catIndices) {
            val warpTo = when (ns[blockIdx]) { portalA -> portalB; portalB -> portalA; else -> -1 }
            if (warpTo >= 0) {
                val wr = warpTo / cols; val wc = warpTo % cols
                if (wr < rows && wc < cols && !isCellOccupied(ns, infos, wr, wc, cols, blockIdx)) {
                    ns[blockIdx] = warpTo
                }
            }
        }

        return ns
    }

    private fun isSolvedState(
        state: IntArray, infos: Array<BlockInfo>, catIndices: List<Int>,
        rows: Int, cols: Int, exitDir: ExitDirection,
        exitRow: Int, exitCol: Int,
        keyIdx: Int, lockPos: Int,
        cpStateIdx: Int,
        exitRow2: Int = -1, exitCol2: Int = -1, exitDir2: ExitDirection? = null
    ): Boolean {
        if (cpStateIdx >= 0 && state[cpStateIdx] == 0) return false
        if (keyIdx >= 0 && state[keyIdx] != lockPos) return false
        // Primary cat
        val catIdx = catIndices[0]
        if (!isCatAtExit(state[catIdx], infos[catIdx], rows, cols, exitDir, exitRow, exitCol)) return false
        // Second cat (multi-cat)
        if (exitDir2 != null && catIndices.size >= 2) {
            if (!isCatAtExit(state[catIndices[1]], infos[catIndices[1]], rows, cols, exitDir2, exitRow2, exitCol2)) return false
        }
        return true
    }

    private fun isCatAtExit(catPos: Int, info: BlockInfo, rows: Int, cols: Int,
                            exitDir: ExitDirection, exitRow: Int, exitCol: Int): Boolean {
        val catRow = catPos / cols; val catCol = catPos % cols
        return when (exitDir) {
            ExitDirection.RIGHT  -> catRow == exitRow && catCol + info.length == cols
            ExitDirection.LEFT   -> catRow == exitRow && catCol == 0
            ExitDirection.BOTTOM -> catCol == exitCol && catRow + info.length == rows
            ExitDirection.TOP    -> catCol == exitCol && catRow == 0
        }
    }

    private fun hashState(state: IntArray): Long {
        var h = -0x340d631b7bdddcdbL // FNV offset basis
        for (i in state.indices) {
            h = h xor (state[i].toLong() * (i + 1))
            h *= 0x100000001b3L // FNV prime
        }
        return h
    }

    private fun buildGrid(state: IntArray, infos: Array<BlockInfo>, rows: Int, cols: Int): Array<IntArray> {
        val g = Array(rows) { IntArray(cols) { -1 } }
        for (i in state.indices) {
            if (i >= infos.size) break  // skip checkpoint state element
            val info = infos[i]
            val bRow = state[i] / cols
            val bCol = state[i] % cols
            if (info.isHorizontal) {
                for (c in bCol until minOf(bCol + info.length, cols)) g[bRow][c] = i
            } else {
                for (r in bRow until minOf(bRow + info.length, rows)) g[r][bCol] = i
            }
        }
        return g
    }

    companion object {
        private const val MAX_BFS_DEPTH = PuzzleConstants.MAX_ACCEPTED_DEPTH
        private val DIRECTIONS = arrayOf(intArrayOf(0, 1), intArrayOf(0, -1), intArrayOf(1, 0), intArrayOf(-1, 0))
    }
}
