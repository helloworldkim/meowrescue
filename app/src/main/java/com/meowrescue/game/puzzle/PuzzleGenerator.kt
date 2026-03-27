package com.meowrescue.game.puzzle

import java.util.LinkedList

class PuzzleGenerator {

    data class DifficultyParams(
        val gridSize: Int,
        val blockCountMin: Int,
        val blockCountMax: Int,
        val minMoves: Int
    )

    data class GenerateResult(val grid: PuzzleGrid, val optimalMoves: Int)

    private fun difficultyFor(stage: Int): DifficultyParams = when {
        stage <= 10  -> DifficultyParams(5, 3, 5,  minOf(stage / 3 + 2, 5))
        stage <= 30  -> DifficultyParams(5, 4, 7,  minOf(stage / 6 + 3, 7))
        stage <= 60  -> DifficultyParams(6, 5, 9,  minOf(stage / 8 + 3, 10))
        stage <= 100 -> DifficultyParams(6, 6, 10, minOf(stage / 8 + 4, 16))
        stage <= 150 -> DifficultyParams(7, 7, 12, minOf(stage / 10 + 5, 20))
        else         -> DifficultyParams(7, 8, 14, minOf(stage / 12 + 8, 22))
    }

    fun generateWithResult(stage: Int): GenerateResult {
        val params = difficultyFor(stage)
        val size = params.gridSize
        val exitRow = size / 2

        var bestGrid: PuzzleGrid? = null
        var bestMoves = 0
        val acceptThreshold = maxOf(2, (params.minMoves * 0.7).toInt())

        repeat(30) { attempt ->
            val rng = java.util.Random(stage.toLong() * 1000 + attempt)
            val grid = tryGenerate(size, exitRow, params, rng) ?: return@repeat

            // Quick pre-filter: skip BFS if cat has clear path to exit
            if (hasClearPath(grid, exitRow)) return@repeat

            val minMoves = solveFast(grid)
            if (minMoves < 0) return@repeat

            if (minMoves > bestMoves) {
                bestMoves = minMoves
                bestGrid = grid
            }
            // Early accept: good enough puzzle
            if (minMoves >= acceptThreshold) {
                return GenerateResult(grid, minMoves)
            }
        }

        if (bestGrid != null && bestMoves >= 2) {
            return GenerateResult(bestGrid!!, bestMoves)
        }

        val fallback = buildFallback(size, exitRow)
        return GenerateResult(fallback, solveFast(fallback).coerceAtLeast(1))
    }

    fun generate(stage: Int): PuzzleGrid = generateWithResult(stage).grid

    /** Quick O(n) check: does the cat have a clear path to exit? If so, puzzle is trivial. */
    private fun hasClearPath(grid: PuzzleGrid, exitRow: Int): Boolean {
        val gridArr = grid.getGrid()
        val cat = grid.blocks.firstOrNull { it.isCat } ?: return true
        val startCol = cat.col + cat.length
        for (c in startCol until grid.cols) {
            if (gridArr[exitRow][c] != -1) return false
        }
        return true // no obstacle between cat and exit
    }

    // ── Fast BFS solver using compact state representation ──────────────

    /** Block descriptor: static properties that don't change during solve */
    private class BlockInfo(val length: Int, val isHorizontal: Boolean, val isCat: Boolean)

    /**
     * Lightweight BFS solver. Instead of cloning PuzzleGrid per state,
     * represents state as an IntArray of block positions (row*cols+col).
     * Grid is reconstructed only when needed for move validation.
     */
    fun solveFast(grid: PuzzleGrid): Int {
        val blocks = grid.blocks.sortedBy { it.id }
        val n = blocks.size
        val rows = grid.rows
        val cols = grid.cols
        val exitRow = grid.exitRow

        val infos = Array(n) { BlockInfo(blocks[it].length, blocks[it].isHorizontal, blocks[it].isCat) }
        val catIdx = infos.indexOfFirst { it.isCat }
        if (catIdx < 0) return -1

        // Encode initial state: position of each block as row*cols+col
        val initState = IntArray(n) { blocks[it].row * cols + blocks[it].col }

        // Check if already solved
        if (isSolvedState(initState, infos, catIdx, cols)) return 0

        val visited = HashSet<Long>(4096)
        visited.add(hashState(initState))

        val queue = LinkedList<Pair<IntArray, Int>>()
        queue.add(initState to 0)

        val maxDepth = 25
        val maxStates = 30_000

        while (queue.isNotEmpty() && visited.size < maxStates) {
            val (state, depth) = queue.poll() ?: break
            if (depth >= maxDepth) continue

            // Build grid from state for move validation
            val g = buildGrid(state, infos, rows, cols)

            for (i in 0 until n) {
                val info = infos[i]
                val pos = state[i]
                val bRow = pos / cols
                val bCol = pos % cols

                if (info.isHorizontal) {
                    // Try move right
                    for (d in 1..cols) {
                        val nc = bCol + d
                        if (nc + info.length > cols) break
                        if (g[bRow][nc + info.length - 1] != -1 && g[bRow][nc + info.length - 1] != i) break
                        // Check all intermediate cells
                        val endCheck = bCol + info.length - 1 + d
                        if (endCheck >= cols) break
                        if (g[bRow][endCheck] != -1 && g[bRow][endCheck] != i) break

                        val ns = state.copyOf()
                        ns[i] = bRow * cols + nc
                        if (isSolvedState(ns, infos, catIdx, cols)) return depth + 1
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
                        if (isSolvedState(ns, infos, catIdx, cols)) return depth + 1
                        val h = hashState(ns)
                        if (h !in visited) {
                            visited.add(h)
                            queue.add(ns to depth + 1)
                        }
                    }
                } else {
                    // Try move down
                    for (d in 1..rows) {
                        val nr = bRow + d
                        if (nr + info.length > rows) break
                        val endCheck = bRow + info.length - 1 + d
                        if (endCheck >= rows) break
                        if (g[endCheck][bCol] != -1 && g[endCheck][bCol] != i) break

                        val ns = state.copyOf()
                        ns[i] = nr * cols + bCol
                        if (isSolvedState(ns, infos, catIdx, cols)) return depth + 1
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
                        if (isSolvedState(ns, infos, catIdx, cols)) return depth + 1
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

    private fun isSolvedState(state: IntArray, infos: Array<BlockInfo>, catIdx: Int, cols: Int): Boolean {
        val catPos = state[catIdx]
        val catCol = catPos % cols
        return catCol + infos[catIdx].length == cols
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

    // Keep old solve() as public API for PuzzleView optimal moves display
    fun solve(grid: PuzzleGrid): Int = solveFast(grid)

    // ── Puzzle generation ───────────────────────────────────────────────

    private fun tryGenerate(
        size: Int, exitRow: Int, params: DifficultyParams, rng: java.util.Random
    ): PuzzleGrid? {
        val grid = PuzzleGrid(size, size, exitRow)

        val catCol = rng.nextInt(maxOf(1, size / 2 - 1))
        if (!grid.placeBlock(PuzzleBlock(0, exitRow, catCol, 2, true, true))) return null

        var nextId = 1
        val pathCols = (catCol + 2 until size).toMutableList()
        pathCols.shuffle(rng)
        val chainDepth = 1 + rng.nextInt(minOf(3, pathCols.size))

        for (i in 0 until minOf(chainDepth, pathCols.size)) {
            val col = pathCols[i]
            val vLen = if (rng.nextInt(3) == 0) 3 else 2
            val vRow = maxOf(0, exitRow - rng.nextInt(vLen))
            if (grid.placeBlock(PuzzleBlock(nextId, vRow, col, vLen, false))) {
                nextId++
                if (vRow > 0) {
                    val hLen = if (rng.nextInt(3) == 0) 3 else 2
                    val hCol = maxOf(0, col - rng.nextInt(hLen))
                    if (grid.placeBlock(PuzzleBlock(nextId, vRow - 1, hCol, hLen, true))) {
                        nextId++
                        if (rng.nextInt(2) == 0) {
                            val v2Col = hCol + hLen
                            if (v2Col < size) {
                                if (grid.placeBlock(PuzzleBlock(nextId, maxOf(0, vRow - 1 - rng.nextInt(2)), v2Col, 2, false)))
                                    nextId++
                            }
                        }
                    }
                }
                val bottomRow = vRow + vLen
                if (bottomRow < size) {
                    val hLen = if (rng.nextInt(3) == 0) 3 else 2
                    val hCol = maxOf(0, col - rng.nextInt(hLen))
                    if (grid.placeBlock(PuzzleBlock(nextId, bottomRow, hCol, hLen, true)))
                        nextId++
                }
            }
        }

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
            val len = if (rng.nextInt(4) == 0) 3 else 2
            if (grid.placeBlock(PuzzleBlock(id, rng.nextInt(size), rng.nextInt(size), len, h)))
                return true
        }
        return false
    }

    private fun buildFallback(size: Int, exitRow: Int): PuzzleGrid {
        val grid = PuzzleGrid(size, size, exitRow)
        grid.placeBlock(PuzzleBlock(0, exitRow, 0, 2, true, true))
        grid.placeBlock(PuzzleBlock(1, maxOf(0, exitRow - 1), size / 2, 2, false))
        grid.placeBlock(PuzzleBlock(2, maxOf(0, exitRow - 2), maxOf(0, size / 2 - 1), 2, true))
        grid.placeBlock(PuzzleBlock(3, maxOf(0, exitRow - 1), size - 1, 2, false))
        grid.placeBlock(PuzzleBlock(4, maxOf(0, exitRow - 2), size - 2, 2, true))
        grid.resetMoveTracking()
        return grid
    }
}
