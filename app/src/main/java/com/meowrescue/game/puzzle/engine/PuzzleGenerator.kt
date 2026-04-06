package com.meowrescue.game.puzzle.engine

import com.meowrescue.game.puzzle.model.ExitDirection
import com.meowrescue.game.puzzle.model.GenerateResult
import com.meowrescue.game.puzzle.model.PuzzleBlock
import com.meowrescue.game.puzzle.model.StageFeatures
import kotlin.math.abs
import kotlin.math.sqrt

class PuzzleGenerator {

    private val solver = PuzzleSolver()


    data class DifficultyParams(
        val gridSize: Int,
        val blockCountMin: Int,
        val blockCountMax: Int,
        val minMoves: Int
    )

    // Cache seed offsets for deterministic re-entry
    private val seedOffsetCache = mutableMapOf<Int, Int>()

    private fun difficultyFor(stage: Int): DifficultyParams {
        // 1x1 cat is much more mobile than 2-cell → realistic move targets are lower
        val minMoves = if (stage <= 5) {
            stage + 1
        } else {
            (sqrt(stage.toDouble()) * 1.5 + 2).toInt().coerceAtMost(16)
        }
        return when {
            stage <= 5   -> DifficultyParams(5, 3, 5, minMoves)
            stage <= 15  -> DifficultyParams(5, 5, 8, minMoves)
            stage <= 30  -> DifficultyParams(6, 6, 10, minMoves)
            stage <= 50  -> DifficultyParams(6, 8, 12, minMoves)
            stage <= 75  -> DifficultyParams(7, 9, 14, minMoves)
            stage <= 100 -> DifficultyParams(7, 10, 15, minMoves)
            else         -> DifficultyParams(7, 11, 16, minMoves)
        }
    }

    fun featuresForStage(stage: Int): StageFeatures {
        if (stage < 16) return StageFeatures(false, false)
        val rng = java.util.Random(stage.toLong() * SEED_PRIME + 42)
        if (stage <= 30) return StageFeatures(rng.nextBoolean(), false)
        if (stage <= 50) { val k = rng.nextBoolean(); return StageFeatures(k, !k) }
        val hasKey = rng.nextBoolean()
        val hasCp = rng.nextBoolean()
        return StageFeatures(
            hasKey = hasKey, hasCheckpoint = hasCp,
            hasWalls = stage >= 51,
            hasLinkedBlocks = stage >= 91 && rng.nextBoolean(),
            hasPortals = stage >= 111 && rng.nextInt(5) < 2,
            hasMultiCat = stage >= 131 && rng.nextInt(10) < 3
        )
    }

    fun generateWithResult(stage: Int): GenerateResult {
        val cachedOffset = seedOffsetCache[stage]
        if (cachedOffset != null) {
            val cached = generateCore(stage, cachedOffset)
            if (cached.optimalMoves >= 2) return cached
            // Cache produced bad result; regenerate below
            seedOffsetCache.remove(stage)
        }

        val deadline = System.currentTimeMillis() + if (stage > 100) GENERATION_DEADLINE_ADVANCED_MS else GENERATION_DEADLINE_MS
        var bestResult: GenerateResult? = null
        var bestMoves = 0
        var bestOffset = 0
        for (offset in 0 until MAX_OUTER_ATTEMPTS) {
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

        seedOffsetCache[stage] = bestOffset
        val result = bestResult ?: generateCore(stage, 0)
        // Safety net: optimalMoves must be at least 2 for meaningful star thresholds
        if (result.optimalMoves < 2) {
            val minFloor = maxOf(2, difficultyFor(stage).minMoves)
            return result.copy(optimalMoves = minFloor)
        }
        return result
    }

    fun generate(stage: Int): PuzzleGrid = generateWithResult(stage).grid

    // ── Core generation ──────────────────────────────────────────────────

    private fun generateCore(stage: Int, seedOffset: Int, deadline: Long = Long.MAX_VALUE): GenerateResult {
        val params = difficultyFor(stage)
        val size = params.gridSize
        val baseSeed = stage.toLong() + seedOffset.toLong() * 10000
        val features = featuresForStage(stage)

        val dirRng = java.util.Random(stage.toLong() * SEED_PRIME)
        val exitDir = ExitDirection.entries[dirRng.nextInt(4)]
        val exitLine = (1 + dirRng.nextInt(maxOf(1, size - 2))).coerceIn(1, size - 2)

        var bestGrid: PuzzleGrid? = null
        var bestMoves = 0
        // 1x1 cat is much more mobile → lower threshold to avoid constant fallbacks
        val acceptThreshold = maxOf(2, (params.minMoves * ACCEPT_THRESHOLD).toInt())

        repeat(MAX_INNER_ATTEMPTS) { attempt ->
            if (System.currentTimeMillis() > deadline) return@repeat
            val rng = java.util.Random(baseSeed * 1000 + attempt)
            val grid = tryGenerate(size, params, rng, exitDir, exitLine, features) ?: return@repeat

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

        // Try fallback with current features
        val fallback = buildFallback(size, exitDir, exitLine, features)
        val fallbackMoves = solveFast(fallback)
        if (fallbackMoves >= 2) {
            return GenerateResult(fallback, fallbackMoves)
        }
        // Complex features may cause unsolvable/trivial fallback; try simple fallback
        val simpleFallback = buildFallback(size, exitDir, exitLine)
        val simpleMoves = solveFast(simpleFallback)
        if (simpleMoves >= 2) {
            return GenerateResult(simpleFallback, simpleMoves)
        }
        // Last resort: use best found grid (even if only 1 move) or fallback with difficulty estimate
        val estimatedMoves = maxOf(2, params.minMoves)
        return GenerateResult(bestGrid ?: simpleFallback, estimatedMoves)
    }

    // ── Quality filter ──────────────────────────────────────────────────

    private fun isQualityPuzzle(grid: PuzzleGrid, stage: Int, optimalMoves: Int): Boolean =
        solver.isQualityPuzzle(grid, stage, optimalMoves)

    private fun hasClearPath(grid: PuzzleGrid): Boolean = solver.hasClearPath(grid)

    // ── Solver delegation ────────────────────────────────────────────────

    fun solveFast(grid: PuzzleGrid): Int = solver.solveFast(grid)

    fun solve(grid: PuzzleGrid): Int = solver.solve(grid)

    /**
     * Returns the optimal solution path as a list of [MoveStep], or null if unsolvable.
     * Each step describes moving a block by (dRow, dCol) grid cells.
     */
    fun solveSteps(grid: PuzzleGrid): List<PuzzleSolver.MoveStep>? = solver.solveSteps(grid)

    // ── Puzzle generation ───────────────────────────────────────────────

    private fun randomLength(rng: java.util.Random): Int {
        val r = rng.nextInt(10)
        return when {
            r < 2 -> 1
            r < 7 -> 2
            else  -> 3
        }
    }

    private fun randomBlockerLength(rng: java.util.Random): Int {
        return if (rng.nextInt(3) == 0) 3 else 2
    }

    private fun lockPosition(exitDir: ExitDirection, exitRow: Int, exitCol: Int, size: Int): Pair<Int, Int> {
        return when (exitDir) {
            ExitDirection.RIGHT  -> (if (exitRow > 0) exitRow - 1 else exitRow + 1) to (size - 1)
            ExitDirection.LEFT   -> (if (exitRow > 0) exitRow - 1 else exitRow + 1) to 0
            ExitDirection.BOTTOM -> (size - 1) to (if (exitCol > 0) exitCol - 1 else exitCol + 1)
            ExitDirection.TOP    -> 0 to (if (exitCol > 0) exitCol - 1 else exitCol + 1)
        }
    }

    private fun tryGenerate(
        size: Int, params: DifficultyParams, rng: java.util.Random,
        exitDir: ExitDirection, exitLine: Int, features: StageFeatures
    ): PuzzleGrid? {
        val catHoriz = (exitDir == ExitDirection.RIGHT || exitDir == ExitDirection.LEFT)
        val exitPositive = (exitDir == ExitDirection.RIGHT || exitDir == ExitDirection.BOTTOM)

        // Cat start position — 1x1 cat
        val catStart = if (exitPositive) {
            rng.nextInt(maxOf(1, size / 2))
        } else {
            val lo = size / 2
            val hi = size - 1
            lo + rng.nextInt(maxOf(1, hi - lo + 1))
        }

        // Determine lock position
        val gridExitRow = if (catHoriz) exitLine else -1
        val gridExitCol = if (!catHoriz) exitLine else -1
        var lockR = -1; var lockC = -1
        if (features.hasKey) {
            val (lr, lc) = lockPosition(exitDir, gridExitRow, gridExitCol, size)
            lockR = lr; lockC = lc
        }

        // Determine checkpoint position (off the main cat-exit axis)
        var cpRow = -1; var cpCol = -1
        if (features.hasCheckpoint) {
            if (catHoriz) {
                val offset = if (rng.nextBoolean()) 1 else -1
                cpRow = (exitLine + offset).coerceIn(0, size - 1)
                if (cpRow == exitLine) cpRow = (exitLine + 1).coerceIn(0, size - 1)
                cpCol = if (exitPositive) {
                    val range = size - catStart - 2
                    catStart + 1 + rng.nextInt(maxOf(1, range))
                } else {
                    rng.nextInt(maxOf(1, catStart))
                }
            } else {
                val offset = if (rng.nextBoolean()) 1 else -1
                cpCol = (exitLine + offset).coerceIn(0, size - 1)
                if (cpCol == exitLine) cpCol = (exitLine + 1).coerceIn(0, size - 1)
                cpRow = if (exitPositive) {
                    val range = size - catStart - 2
                    catStart + 1 + rng.nextInt(maxOf(1, range))
                } else {
                    rng.nextInt(maxOf(1, catStart))
                }
            }
            cpRow = cpRow.coerceIn(0, size - 1)
            cpCol = cpCol.coerceIn(0, size - 1)
        }

        val catRow = if (catHoriz) exitLine else catStart
        val catCol = if (catHoriz) catStart else exitLine

        // Portal pair (ensure A != B)
        val pA: Int; val pB: Int
        if (features.hasPortals) {
            var a = -1; var b = -1
            repeat(60) {
                if (a >= 0 && b >= 0) return@repeat
                val r1 = rng.nextInt(size); val c1 = rng.nextInt(size)
                val r2 = rng.nextInt(size); val c2 = rng.nextInt(size)
                val pos1 = r1 * size + c1; val pos2 = r2 * size + c2
                if (pos1 != pos2
                    && abs(r1 - r2) + abs(c1 - c2) >= size / 2) {
                    a = pos1; b = pos2
                }
            }
            pA = a; pB = b
        } else { pA = -1; pB = -1 }

        // Multi-cat second exit
        var eRow2 = -1; var eCol2 = -1; var eDir2: ExitDirection? = null
        var cat2Row = -1; var cat2Col = -1; var cat2Horiz = false
        if (features.hasMultiCat) {
            val availDirs = ExitDirection.entries.filter { it != exitDir }
            eDir2 = availDirs[rng.nextInt(availDirs.size)]
            val exitLine2 = 1 + rng.nextInt(maxOf(1, size - 2))
            eRow2 = if (eDir2 == ExitDirection.RIGHT || eDir2 == ExitDirection.LEFT) exitLine2 else -1
            eCol2 = if (eDir2 == ExitDirection.TOP || eDir2 == ExitDirection.BOTTOM) exitLine2 else -1
            cat2Horiz = (eDir2 == ExitDirection.RIGHT || eDir2 == ExitDirection.LEFT)
            val cat2Start = rng.nextInt(size)
            cat2Row = if (cat2Horiz) exitLine2 else cat2Start
            cat2Col = if (cat2Horiz) cat2Start else exitLine2
        }

        val grid = PuzzleGrid(
            size, size,
            exitRow = gridExitRow,
            exitCol = gridExitCol,
            exitDirection = exitDir,
            hasKeyLock = features.hasKey,
            lockRow = lockR,
            lockCol = lockC,
            checkpointRow = cpRow,
            checkpointCol = cpCol,
            portalA = pA,
            portalB = pB,
            exitRow2 = eRow2,
            exitCol2 = eCol2,
            exitDirection2 = eDir2
        )

        if (!grid.placeBlock(PuzzleBlock(0, catRow, catCol, 1, catHoriz, true))) return null

        var nextId = 1

        // Place second cat for multi-cat
        if (features.hasMultiCat && cat2Row >= 0 && cat2Col >= 0) {
            if (grid.placeBlock(PuzzleBlock(nextId, cat2Row, cat2Col, 1, cat2Horiz, isCat = true)))
                nextId++
        }

        // Path cells between cat front and exit edge (1x1 cat: +1 instead of +2)
        val pathCells = if (exitPositive) {
            (catStart + 1 until size).toMutableList()
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
                val vRow = maxOf(0, exitLine - rng.nextInt(blockerLen))
                if (grid.placeBlock(PuzzleBlock(nextId, vRow, pathPos, blockerLen, false))) {
                    nextId++
                    if (vRow > 0) {
                        val hLen = randomLength(rng)
                        val hCol = maxOf(0, pathPos - rng.nextInt(maxOf(1, hLen)))
                        if (grid.placeBlock(PuzzleBlock(nextId, vRow - 1, hCol, hLen, true))) {
                            nextId++
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
                    val bottomRow = vRow + blockerLen
                    if (bottomRow < size) {
                        val hLen = randomLength(rng)
                        val hCol = maxOf(0, pathPos - rng.nextInt(maxOf(1, hLen)))
                        if (grid.placeBlock(PuzzleBlock(nextId, bottomRow, hCol, hLen, true)))
                            nextId++
                    }
                }
            } else {
                val hCol = maxOf(0, exitLine - rng.nextInt(blockerLen))
                if (grid.placeBlock(PuzzleBlock(nextId, pathPos, hCol, blockerLen, true))) {
                    nextId++
                    if (hCol > 0) {
                        val vLen = randomLength(rng)
                        val vRow = maxOf(0, pathPos - rng.nextInt(maxOf(1, vLen)))
                        if (grid.placeBlock(PuzzleBlock(nextId, vRow, hCol - 1, vLen, false))) {
                            nextId++
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

        // Place key block if needed (1x1, away from lock)
        if (features.hasKey && lockR >= 0 && lockC >= 0) {
            if (placeKeyBlock(grid, nextId, lockR, lockC, size, rng)) nextId++
        }

        // Add cross-axis blockers near the cat's alternative escape routes (1x1 cat bypass prevention)
        val crossAxisCount = 1 + rng.nextInt(3)
        for (ci in 0 until crossAxisCount) {
            val len = randomBlockerLength(rng)
            if (catHoriz) {
                // Place horizontal blocks on rows near exitLine to block vertical cat movement
                val row = (exitLine + (if (rng.nextBoolean()) -1 - ci else 1 + ci)).coerceIn(0, size - len)
                val col = rng.nextInt(maxOf(1, size - len))
                if (grid.placeBlock(PuzzleBlock(nextId, row, col, len, true))) nextId++
            } else {
                val col = (exitLine + (if (rng.nextBoolean()) -1 - ci else 1 + ci)).coerceIn(0, size - len)
                val row = rng.nextInt(maxOf(1, size - len))
                if (grid.placeBlock(PuzzleBlock(nextId, row, col, len, false))) nextId++
            }
        }

        // Wall blocks (1-3)
        if (features.hasWalls) {
            val wallCount = 1 + rng.nextInt(3)
            repeat(wallCount) {
                repeat(20) inner@{
                    val wr = rng.nextInt(size); val wc = rng.nextInt(size)
                    if (grid.placeBlock(PuzzleBlock(nextId, wr, wc, 1, true, isWall = true))) {
                        nextId++; return@inner
                    }
                }
            }
        }

        // Linked block pair
        if (features.hasLinkedBlocks) {
            val linkGroup = 1
            val linkH = rng.nextBoolean()
            val len = randomBlockerLength(rng)
            var linked = false
            repeat(20) {
                if (linked) return@repeat
                val r1 = rng.nextInt(size); val c1 = rng.nextInt(size)
                val block1 = PuzzleBlock(nextId, r1, c1, len, linkH, linkId = linkGroup)
                if (grid.placeBlock(block1)) {
                    val firstId = nextId; nextId++
                    var placedPartner = false
                    repeat(20) inner@{
                        if (placedPartner) return@inner
                        val r2 = rng.nextInt(size); val c2 = rng.nextInt(size)
                        if (grid.placeBlock(PuzzleBlock(nextId, r2, c2, len, linkH, linkId = linkGroup))) {
                            nextId++; placedPartner = true; linked = true
                        }
                    }
                    // Partner failed — remove the orphan first block
                    if (!placedPartner) {
                        grid.removeBlock(firstId)
                        nextId--
                    }
                }
            }
        }

        // Random fill to reach target block count
        val target = params.blockCountMin + rng.nextInt(maxOf(1, params.blockCountMax - params.blockCountMin + 1))
        while (grid.blockCount < target) {
            if (!tryPlaceRandom(grid, nextId, size, rng)) break
            nextId++
        }

        if (grid.blockCount < 3 || grid.isSolved()) return null
        return grid
    }

    private fun placeKeyBlock(grid: PuzzleGrid, id: Int, lockRow: Int, lockCol: Int, size: Int, rng: java.util.Random): Boolean {
        repeat(40) {
            val r = rng.nextInt(size)
            val c = rng.nextInt(size)
            if (r == lockRow && c == lockCol) return@repeat
            if (abs(r - lockRow) + abs(c - lockCol) < 2) return@repeat
            if (grid.placeBlock(PuzzleBlock(id, r, c, 1, true, isKey = true))) return true
        }
        return false
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

    private fun buildFallback(size: Int, exitDir: ExitDirection, exitLine: Int,
                              features: StageFeatures = StageFeatures(false, false)): PuzzleGrid {
        val catHoriz = (exitDir == ExitDirection.RIGHT || exitDir == ExitDirection.LEFT)
        val exitPositive = (exitDir == ExitDirection.RIGHT || exitDir == ExitDirection.BOTTOM)

        val gridExitRow = if (catHoriz) exitLine else -1
        val gridExitCol = if (!catHoriz) exitLine else -1

        // Compute lock/checkpoint for fallback too
        var lockR = -1; var lockC = -1
        if (features.hasKey) {
            val (lr, lc) = lockPosition(exitDir, gridExitRow, gridExitCol, size)
            lockR = lr; lockC = lc
        }
        var cpRow = -1; var cpCol = -1
        if (features.hasCheckpoint) {
            val rng = java.util.Random(exitLine.toLong() * 31 + size)
            if (catHoriz) {
                cpRow = (exitLine + (if (rng.nextBoolean()) 1 else -1)).coerceIn(0, size - 1)
                if (cpRow == exitLine) cpRow = (exitLine + 1).coerceIn(0, size - 1)
                cpCol = size / 2
            } else {
                cpCol = (exitLine + (if (rng.nextBoolean()) 1 else -1)).coerceIn(0, size - 1)
                if (cpCol == exitLine) cpCol = (exitLine + 1).coerceIn(0, size - 1)
                cpRow = size / 2
            }
        }

        val grid = PuzzleGrid(
            size, size,
            exitRow = gridExitRow,
            exitCol = gridExitCol,
            exitDirection = exitDir,
            hasKeyLock = features.hasKey,
            lockRow = lockR, lockCol = lockC,
            checkpointRow = cpRow, checkpointCol = cpCol
        )

        val catStart = if (exitPositive) 0 else size - 1
        val catRow = if (catHoriz) exitLine else catStart
        val catCol = if (catHoriz) catStart else exitLine
        grid.placeBlock(PuzzleBlock(0, catRow, catCol, 1, catHoriz, true))

        var nextId = 1
        val mid = size / 2
        if (catHoriz) {
            // Vertical blocker on cat path
            val bRow = maxOf(0, exitLine - 1)
            if (grid.placeBlock(PuzzleBlock(nextId, bRow, mid, 2, false))) nextId++
            // Horizontal blocker above
            if (bRow > 0) {
                if (grid.placeBlock(PuzzleBlock(nextId, bRow - 1, maxOf(0, mid - 1), 2, true))) nextId++
            }
            // Second vertical blocker
            val bRow2 = minOf(size - 2, exitLine + 1)
            if (bRow2 != bRow) {
                if (grid.placeBlock(PuzzleBlock(nextId, bRow2, if (exitPositive) size - 2 else 1, 2, false))) nextId++
            }
            // Extra cross-axis blockers to make 1x1 cat work harder
            val farCol = if (exitPositive) size - 3 else 2
            if (grid.placeBlock(PuzzleBlock(nextId, maxOf(0, exitLine - 2), farCol, 3, false))) nextId++
            if (grid.placeBlock(PuzzleBlock(nextId, minOf(size - 1, exitLine + 2), maxOf(0, mid - 2), 2, true))) nextId++
        } else {
            val bCol = maxOf(0, exitLine - 1)
            if (grid.placeBlock(PuzzleBlock(nextId, mid, bCol, 2, true))) nextId++
            if (bCol > 0) {
                if (grid.placeBlock(PuzzleBlock(nextId, maxOf(0, mid - 1), bCol - 1, 2, false))) nextId++
            }
            val bCol2 = minOf(size - 2, exitLine + 1)
            if (bCol2 != bCol) {
                if (grid.placeBlock(PuzzleBlock(nextId, if (exitPositive) size - 2 else 1, bCol2, 2, true))) nextId++
            }
            // Extra cross-axis blockers
            val farRow = if (exitPositive) size - 3 else 2
            if (grid.placeBlock(PuzzleBlock(nextId, farRow, maxOf(0, exitLine - 2), 3, true))) nextId++
            if (grid.placeBlock(PuzzleBlock(nextId, maxOf(0, mid - 2), minOf(size - 1, exitLine + 2), 2, false))) nextId++
        }

        // Place key block if features require it
        if (features.hasKey && lockR >= 0 && lockC >= 0) {
            val fbRng = java.util.Random(exitLine.toLong() * 17 + size)
            placeKeyBlock(grid, nextId, lockR, lockC, size, fbRng)
        }

        grid.resetMoveTracking()
        return grid
    }

    companion object {
        private const val SEED_PRIME = 7919L
        private const val GENERATION_DEADLINE_MS = 3000L
        private const val GENERATION_DEADLINE_ADVANCED_MS = 5000L
        private const val MAX_OUTER_ATTEMPTS = 80
        private const val MAX_INNER_ATTEMPTS = 60
        private const val ACCEPT_THRESHOLD = 0.50
    }
}
