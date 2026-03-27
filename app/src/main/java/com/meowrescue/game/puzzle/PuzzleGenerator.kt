package com.meowrescue.game.puzzle

import java.util.LinkedList
import kotlin.math.sqrt

class PuzzleGenerator {

    data class DifficultyParams(
        val gridSize: Int,
        val blockCountMin: Int,
        val blockCountMax: Int,
        val minMoves: Int
    )

    data class GenerateResult(val grid: PuzzleGrid, val optimalMoves: Int)

    // Cache seed offsets for deterministic re-entry
    private val seedOffsetCache = mutableMapOf<Int, Int>()

    private fun difficultyFor(stage: Int): DifficultyParams {
        // sqrt-based scaling: smooth curve from 2 moves (stage 1) to 30 moves (stage 150+)
        val minMoves = if (stage <= 5) {
            stage + 1
        } else {
            (sqrt(stage.toDouble()) * 2.5 + 1).toInt().coerceAtMost(30)
        }
        return when {
            stage <= 5   -> DifficultyParams(5, 3, 5, minMoves)    // Tutorial
            stage <= 15  -> DifficultyParams(5, 5, 8, minMoves)    // Beginner
            stage <= 30  -> DifficultyParams(6, 6, 10, minMoves)   // Easy (6x6)
            stage <= 50  -> DifficultyParams(6, 8, 12, minMoves)   // Medium
            stage <= 75  -> DifficultyParams(7, 9, 14, minMoves)   // Hard (7x7)
            stage <= 100 -> DifficultyParams(7, 10, 15, minMoves)  // Expert
            else         -> DifficultyParams(7, 11, 16, minMoves)  // Master
        }
    }

    fun generateWithResult(stage: Int): GenerateResult {
        // Check cache for deterministic re-entry
        val cachedOffset = seedOffsetCache[stage]
        if (cachedOffset != null) {
            return generateCore(stage, cachedOffset)
        }

        // Try with quality filter, up to 80 seed offsets (3-second time budget)
        val deadline = System.currentTimeMillis() + 3000L
        var bestResult: GenerateResult? = null
        var bestMoves = 0
        var bestOffset = 0
        for (offset in 0 until 80) {
            if (System.currentTimeMillis() > deadline) break
            val result = generateCore(stage, offset, deadline)
            if (result.optimalMoves > bestMoves) {
                bestMoves = result.optimalMoves
                bestResult = result
                bestOffset = offset
            }
            if (result.optimalMoves >= 2 && isQualityPuzzle(result.grid, stage, result.optimalMoves)) {
                seedOffsetCache[stage] = offset
                return result
            }
        }

        // Fallback: use the best puzzle found and cache its actual offset
        seedOffsetCache[stage] = bestOffset
        return bestResult!!
    }

    fun generate(stage: Int): PuzzleGrid = generateWithResult(stage).grid

    // ── Core generation (extracted from old generateWithResult) ──────────

    private fun generateCore(stage: Int, seedOffset: Int, deadline: Long = Long.MAX_VALUE): GenerateResult {
        val params = difficultyFor(stage)
        val size = params.gridSize
        val baseSeed = stage.toLong() + seedOffset.toLong() * 10000

        // Deterministic exit direction per stage (not affected by seedOffset)
        val dirRng = java.util.Random(stage.toLong() * 7919)
        val exitDir = ExitDirection.entries[dirRng.nextInt(4)]
        val exitLine = (1 + dirRng.nextInt(maxOf(1, size - 2))).coerceIn(1, size - 2)

        var bestGrid: PuzzleGrid? = null
        var bestMoves = 0
        val acceptThreshold = maxOf(2, (params.minMoves * 0.85).toInt())

        repeat(60) { attempt ->
            if (System.currentTimeMillis() > deadline) return@repeat
            val rng = java.util.Random(baseSeed * 1000 + attempt)
            val grid = tryGenerate(size, params, rng, exitDir, exitLine) ?: return@repeat

            if (hasClearPath(grid)) return@repeat

            val minMoves = solveFast(grid)
            if (minMoves < 0) return@repeat

            if (minMoves > bestMoves) {
                bestMoves = minMoves
                bestGrid = grid
            }
            if (minMoves >= acceptThreshold) {
                return GenerateResult(grid, minMoves)
            }
        }

        if (bestGrid != null && bestMoves >= 2) {
            return GenerateResult(bestGrid!!, bestMoves)
        }

        val fallback = buildFallback(size, exitDir, exitLine)
        return GenerateResult(fallback, solveFast(fallback).coerceAtLeast(1))
    }

    // ── Quality filter ──────────────────────────────────────────────────

    private fun isQualityPuzzle(grid: PuzzleGrid, stage: Int, optimalMoves: Int): Boolean {
        if (optimalMoves < 2) return false

        val cat = grid.blocks.firstOrNull { it.isCat } ?: return false
        val gridArr = grid.getGrid()

        // Find blocks directly blocking cat's path to exit
        val directBlockers = findDirectBlockers(grid, cat, gridArr)

        // 1. 단방향 풀이: all blockers same orientation as cat → single-direction solution
        if (directBlockers.isNotEmpty()) {
            val allSameOrientation = directBlockers.all { it.isHorizontal == cat.isHorizontal }
            if (allSameOrientation) return false
        }

        // 2. 단일 블록 풀이: only 1 blocker (skip stages 1-5 입문 스테이지)
        if (stage > 5 && directBlockers.size <= 1) return false
        // 2b. 중급 이상: 최소 3개 직접 차단 블록 필요
        if (stage > 30 && directBlockers.size <= 2) return false

        // 3. 독립 이동 풀이: check if at least one blocker is trapped by another block
        if (directBlockers.size >= 2) {
            val anyTrapped = directBlockers.any { blocker ->
                isBlockerTrapped(blocker, gridArr, grid.rows, grid.cols)
            }
            if (!anyTrapped) return false
        }

        return true
    }

    private fun findDirectBlockers(grid: PuzzleGrid, cat: PuzzleBlock, gridArr: Array<IntArray>): List<PuzzleBlock> {
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

    private fun isBlockerTrapped(block: PuzzleBlock, gridArr: Array<IntArray>, rows: Int, cols: Int): Boolean {
        // Check if block can move at least 1 cell in either direction along its axis
        // For 1-cell blocks, also check perpendicular axis (they can move in both)
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

    /** Quick check: does the cat have a clear path to exit? If so, puzzle is trivial. */
    private fun hasClearPath(grid: PuzzleGrid): Boolean {
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

    // ── Fast BFS solver using compact state representation ──────────────

    private class BlockInfo(val length: Int, val isHorizontal: Boolean, val isCat: Boolean)

    fun solveFast(grid: PuzzleGrid): Int {
        val blocks = grid.blocks.sortedBy { it.id }
        val n = blocks.size
        val rows = grid.rows
        val cols = grid.cols
        val exitDir = grid.exitDirection

        val infos = Array(n) { BlockInfo(blocks[it].length, blocks[it].isHorizontal, blocks[it].isCat) }
        val catIdx = infos.indexOfFirst { it.isCat }
        if (catIdx < 0) return -1

        val initState = IntArray(n) { blocks[it].row * cols + blocks[it].col }

        if (isSolvedState(initState, infos, catIdx, rows, cols, exitDir)) return 0

        val visited = HashSet<Long>(4096)
        visited.add(hashState(initState))

        val queue = LinkedList<Pair<IntArray, Int>>()
        queue.add(initState to 0)

        val maxDepth = 35
        val maxStates = 80_000

        while (queue.isNotEmpty() && visited.size < maxStates) {
            val (state, depth) = queue.poll() ?: break
            if (depth >= maxDepth) continue

            val g = buildGrid(state, infos, rows, cols)

            for (i in 0 until n) {
                val info = infos[i]
                val pos = state[i]
                val bRow = pos / cols
                val bCol = pos % cols

                // 1-cell blocks can move in both axes
                val tryHoriz = info.isHorizontal || info.length == 1
                val tryVert  = !info.isHorizontal || info.length == 1

                if (tryHoriz) {
                    // Try move right
                    for (d in 1..cols) {
                        val nc = bCol + d
                        if (nc + info.length > cols) break
                        if (g[bRow][nc + info.length - 1] != -1 && g[bRow][nc + info.length - 1] != i) break

                        val ns = state.copyOf()
                        ns[i] = bRow * cols + nc
                        if (isSolvedState(ns, infos, catIdx, rows, cols, exitDir)) return depth + 1
                        val h = hashState(ns)
                        if (h !in visited) {
                            visited.add(h)
                            queue.add(ns to depth + 1)
                        }
                    }
                    // Try move left
                    for (d in 1..cols) {
                        val nc = bCol - d
                        if (nc < 0) break
                        if (g[bRow][nc] != -1 && g[bRow][nc] != i) break

                        val ns = state.copyOf()
                        ns[i] = bRow * cols + nc
                        if (isSolvedState(ns, infos, catIdx, rows, cols, exitDir)) return depth + 1
                        val h = hashState(ns)
                        if (h !in visited) {
                            visited.add(h)
                            queue.add(ns to depth + 1)
                        }
                    }
                }
                if (tryVert) {
                    // Try move down
                    for (d in 1..rows) {
                        val nr = bRow + d
                        val endCheck = bRow + info.length - 1 + d
                        if (endCheck >= rows) break
                        if (g[endCheck][bCol] != -1 && g[endCheck][bCol] != i) break

                        val ns = state.copyOf()
                        ns[i] = nr * cols + bCol
                        if (isSolvedState(ns, infos, catIdx, rows, cols, exitDir)) return depth + 1
                        val h = hashState(ns)
                        if (h !in visited) {
                            visited.add(h)
                            queue.add(ns to depth + 1)
                        }
                    }
                    // Try move up
                    for (d in 1..rows) {
                        val nr = bRow - d
                        if (nr < 0) break
                        if (g[nr][bCol] != -1 && g[nr][bCol] != i) break

                        val ns = state.copyOf()
                        ns[i] = nr * cols + bCol
                        if (isSolvedState(ns, infos, catIdx, rows, cols, exitDir)) return depth + 1
                        val h = hashState(ns)
                        if (h !in visited) {
                            visited.add(h)
                            queue.add(ns to depth + 1)
                        }
                    }
                }
            }
        }
        return -1
    }

    private fun isSolvedState(
        state: IntArray, infos: Array<BlockInfo>, catIdx: Int,
        rows: Int, cols: Int, exitDir: ExitDirection
    ): Boolean {
        val catPos = state[catIdx]
        val catRow = catPos / cols
        val catCol = catPos % cols
        return when (exitDir) {
            ExitDirection.RIGHT  -> catCol + infos[catIdx].length == cols
            ExitDirection.LEFT   -> catCol == 0
            ExitDirection.BOTTOM -> catRow + infos[catIdx].length == rows
            ExitDirection.TOP    -> catRow == 0
        }
    }

    private fun hashState(state: IntArray): Long {
        var h = 0L
        for (v in state) {
            h = h * 31 + v
        }
        return h
    }

    private fun buildGrid(state: IntArray, infos: Array<BlockInfo>, rows: Int, cols: Int): Array<IntArray> {
        val g = Array(rows) { IntArray(cols) { -1 } }
        for (i in state.indices) {
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

    fun solve(grid: PuzzleGrid): Int = solveFast(grid)

    // ── Puzzle generation ───────────────────────────────────────────────

    /** Random block length: 20% → 1, 50% → 2, 30% → 3 */
    private fun randomLength(rng: java.util.Random): Int {
        val r = rng.nextInt(10)
        return when {
            r < 2 -> 1
            r < 7 -> 2
            else  -> 3
        }
    }

    /** For direct blockers, at least 2 cells to actually block */
    private fun randomBlockerLength(rng: java.util.Random): Int {
        return if (rng.nextInt(3) == 0) 3 else 2
    }

    private fun tryGenerate(
        size: Int, params: DifficultyParams, rng: java.util.Random,
        exitDir: ExitDirection, exitLine: Int
    ): PuzzleGrid? {
        val catHoriz = (exitDir == ExitDirection.RIGHT || exitDir == ExitDirection.LEFT)
        val exitPositive = (exitDir == ExitDirection.RIGHT || exitDir == ExitDirection.BOTTOM)

        val grid = PuzzleGrid(
            size, size,
            exitRow = if (catHoriz) exitLine else -1,
            exitCol = if (!catHoriz) exitLine else -1,
            exitDirection = exitDir
        )

        // Cat start position (col for horiz, row for vert) — varied
        val catStart = if (exitPositive) {
            rng.nextInt(maxOf(1, size / 2))
        } else {
            val lo = size / 2
            val hi = size - 2
            lo + rng.nextInt(maxOf(1, hi - lo + 1))
        }

        val catRow = if (catHoriz) exitLine else catStart
        val catCol = if (catHoriz) catStart else exitLine
        if (!grid.placeBlock(PuzzleBlock(0, catRow, catCol, 2, catHoriz, true))) return null

        var nextId = 1

        // Path cells between cat front and exit edge
        val pathCells = if (exitPositive) {
            (catStart + 2 until size).toMutableList()
        } else {
            (0 until catStart).toMutableList()
        }
        pathCells.shuffle(rng)

        val maxChain = when {
            params.minMoves <= 10 -> 2
            params.minMoves <= 17 -> 3
            params.minMoves <= 25 -> 4
            else -> 5
        }
        val chainDepth = 1 + rng.nextInt(minOf(maxChain, pathCells.size.coerceAtLeast(1)))

        for (i in 0 until minOf(chainDepth, pathCells.size)) {
            val pathPos = pathCells[i]
            val blockerLen = randomBlockerLength(rng)

            if (catHoriz) {
                // Direct blocker: vertical block at column pathPos, crossing exitLine
                val vRow = maxOf(0, exitLine - rng.nextInt(blockerLen))
                if (grid.placeBlock(PuzzleBlock(nextId, vRow, pathPos, blockerLen, false))) {
                    nextId++
                    // Indirect above: horizontal block restricting vertical blocker upward
                    if (vRow > 0) {
                        val hLen = randomLength(rng)
                        val hCol = maxOf(0, pathPos - rng.nextInt(maxOf(1, hLen)))
                        if (grid.placeBlock(PuzzleBlock(nextId, vRow - 1, hCol, hLen, true))) {
                            nextId++
                            // Tertiary: vertical block blocking the horizontal
                            if (rng.nextInt(2) == 0) {
                                val v2Col = hCol + hLen
                                if (v2Col < size) {
                                    val v2Len = randomLength(rng)
                                    if (grid.placeBlock(PuzzleBlock(nextId, maxOf(0, vRow - 1 - rng.nextInt(maxOf(1, v2Len))), v2Col, v2Len, false)))
                                        nextId++
                                }
                            }
                        }
                    }
                    // Indirect below: horizontal block restricting vertical blocker downward
                    val bottomRow = vRow + blockerLen
                    if (bottomRow < size) {
                        val hLen = randomLength(rng)
                        val hCol = maxOf(0, pathPos - rng.nextInt(maxOf(1, hLen)))
                        if (grid.placeBlock(PuzzleBlock(nextId, bottomRow, hCol, hLen, true)))
                            nextId++
                    }
                }
            } else {
                // Direct blocker: horizontal block at row pathPos, crossing exitLine
                val hCol = maxOf(0, exitLine - rng.nextInt(blockerLen))
                if (grid.placeBlock(PuzzleBlock(nextId, pathPos, hCol, blockerLen, true))) {
                    nextId++
                    // Indirect left: vertical block restricting horizontal blocker leftward
                    if (hCol > 0) {
                        val vLen = randomLength(rng)
                        val vRow = maxOf(0, pathPos - rng.nextInt(maxOf(1, vLen)))
                        if (grid.placeBlock(PuzzleBlock(nextId, vRow, hCol - 1, vLen, false))) {
                            nextId++
                            // Tertiary: horizontal block blocking the vertical
                            if (rng.nextInt(2) == 0) {
                                val h2Row = vRow + vLen
                                if (h2Row < size) {
                                    val h2Len = randomLength(rng)
                                    if (grid.placeBlock(PuzzleBlock(nextId, h2Row, maxOf(0, hCol - 1 - rng.nextInt(maxOf(1, h2Len))), h2Len, true)))
                                        nextId++
                                }
                            }
                        }
                    }
                    // Indirect right: vertical block restricting horizontal blocker rightward
                    val rightCol = hCol + blockerLen
                    if (rightCol < size) {
                        val vLen = randomLength(rng)
                        val vRow = maxOf(0, pathPos - rng.nextInt(maxOf(1, vLen)))
                        if (grid.placeBlock(PuzzleBlock(nextId, vRow, rightCol, vLen, false)))
                            nextId++
                    }
                }
            }
        }

        // Random fill to reach target block count
        val target = params.blockCountMin + rng.nextInt(maxOf(1, params.blockCountMax - params.blockCountMin + 1))
        while (grid.blocks.size < target) {
            if (!tryPlaceRandom(grid, nextId, size, rng)) break
            nextId++
        }

        if (grid.blocks.size < 3 || grid.isSolved()) return null
        return grid
    }

    private fun tryPlaceRandom(grid: PuzzleGrid, id: Int, size: Int, rng: java.util.Random): Boolean {
        repeat(40) {
            val h = rng.nextBoolean()
            val len = randomLength(rng)
            if (grid.placeBlock(PuzzleBlock(id, rng.nextInt(size), rng.nextInt(size), len, h)))
                return true
        }
        return false
    }

    private fun buildFallback(size: Int, exitDir: ExitDirection, exitLine: Int): PuzzleGrid {
        val catHoriz = (exitDir == ExitDirection.RIGHT || exitDir == ExitDirection.LEFT)
        val exitPositive = (exitDir == ExitDirection.RIGHT || exitDir == ExitDirection.BOTTOM)

        val grid = PuzzleGrid(
            size, size,
            exitRow = if (catHoriz) exitLine else -1,
            exitCol = if (!catHoriz) exitLine else -1,
            exitDirection = exitDir
        )

        val catStart = if (exitPositive) 0 else size - 2
        val catRow = if (catHoriz) exitLine else catStart
        val catCol = if (catHoriz) catStart else exitLine
        grid.placeBlock(PuzzleBlock(0, catRow, catCol, 2, catHoriz, true))

        val mid = size / 2
        if (catHoriz) {
            val bRow = maxOf(0, exitLine - 1)
            grid.placeBlock(PuzzleBlock(1, bRow, mid, 2, false))
            if (bRow > 0) {
                grid.placeBlock(PuzzleBlock(2, bRow - 1, maxOf(0, mid - 1), 2, true))
            }
            val bRow2 = minOf(size - 2, exitLine + 1)
            if (bRow2 != bRow) {
                grid.placeBlock(PuzzleBlock(3, bRow2, if (exitPositive) size - 2 else 1, 2, false))
            }
        } else {
            val bCol = maxOf(0, exitLine - 1)
            grid.placeBlock(PuzzleBlock(1, mid, bCol, 2, true))
            if (bCol > 0) {
                grid.placeBlock(PuzzleBlock(2, maxOf(0, mid - 1), bCol - 1, 2, false))
            }
            val bCol2 = minOf(size - 2, exitLine + 1)
            if (bCol2 != bCol) {
                grid.placeBlock(PuzzleBlock(3, if (exitPositive) size - 2 else 1, bCol2, 2, true))
            }
        }

        grid.resetMoveTracking()
        return grid
    }
}
