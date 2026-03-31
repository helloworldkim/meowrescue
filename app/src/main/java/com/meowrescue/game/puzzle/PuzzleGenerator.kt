package com.meowrescue.game.puzzle

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

class PuzzleGenerator {

    data class DifficultyParams(
        val gridSize: Int,
        val blockCountMin: Int,
        val blockCountMax: Int,
        val minMoves: Int
    )

    data class GenerateResult(val grid: PuzzleGrid, val optimalMoves: Int)

    data class StageFeatures(
        val hasKey: Boolean, val hasCheckpoint: Boolean,
        val hasWalls: Boolean = false,
        val hasLinkedBlocks: Boolean = false,
        val hasPortals: Boolean = false,
        val hasMultiCat: Boolean = false
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

    private fun isQualityPuzzle(grid: PuzzleGrid, stage: Int, optimalMoves: Int): Boolean {
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

    private class BlockInfo(val length: Int, val isHorizontal: Boolean, val isCat: Boolean,
                                val isKey: Boolean = false, val isWall: Boolean = false, val linkId: Int = -1)

    fun solveFast(grid: PuzzleGrid): Int = bfsCore(grid, false).depth

    private data class BfsResult(val depth: Int, val path: List<MoveStep>?)

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

    private fun bfsCore(grid: PuzzleGrid, trackPath: Boolean): BfsResult {
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

        val maxStates = when {
            hasCheckpoint && exitDir2 != null -> MAX_STATES_MULTI
            exitDir2 != null -> MAX_STATES_CHECKPOINT
            hasCheckpoint -> MAX_STATES_CHECKPOINT
            else -> MAX_STATES_DEFAULT
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

    fun solve(grid: PuzzleGrid): Int = solveFast(grid)

    // ── Step-returning BFS for hint system ──────────────────────────────

    data class MoveStep(val blockId: Int, val dRow: Int, val dCol: Int)

    /**
     * Returns the optimal solution path as a list of [MoveStep], or null if unsolvable.
     * Each step describes moving a block by (dRow, dCol) grid cells.
     */
    fun solveSteps(grid: PuzzleGrid): List<MoveStep>? = bfsCore(grid, true).path

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
        private const val MAX_BFS_DEPTH = 45
        private const val MAX_STATES_DEFAULT = 150_000
        private const val MAX_STATES_CHECKPOINT = 250_000
        private const val MAX_STATES_MULTI = 300_000
        private const val GENERATION_DEADLINE_MS = 3000L
        private const val GENERATION_DEADLINE_ADVANCED_MS = 5000L
        private const val MAX_OUTER_ATTEMPTS = 80
        private const val MAX_INNER_ATTEMPTS = 60
        private const val ACCEPT_THRESHOLD = 0.50
        private val DIRECTIONS = arrayOf(intArrayOf(0, 1), intArrayOf(0, -1), intArrayOf(1, 0), intArrayOf(-1, 0))
    }
}
