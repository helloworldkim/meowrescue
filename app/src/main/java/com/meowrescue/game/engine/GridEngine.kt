package com.meowrescue.game.engine

import com.meowrescue.game.model.Block
import com.meowrescue.game.model.BlockType
import com.meowrescue.game.model.GridState
import com.meowrescue.game.model.MatchResult
import java.util.Random
import com.meowrescue.game.util.GridConstants

object GridEngine {

    private val DIRECTIONS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    fun findMatches(grid: GridState): List<MatchResult> {
        val visited = Array(grid.height) { BooleanArray(grid.width) }
        val matches = mutableListOf<MatchResult>()

        for (row in 0 until grid.height) {
            for (col in 0 until grid.width) {
                if (visited[row][col]) continue
                val block = grid.get(row, col) ?: continue
                if (block.type == BlockType.EMPTY) continue

                val group = bfsGroup(grid, row, col, block.type, visited)
                if (group.size >= GridConstants.MIN_MATCH_SIZE) {
                    matches.add(MatchResult(type = block.type, positions = group))
                }
            }
        }

        return matches
    }

    private fun bfsGroup(
        grid: GridState,
        startRow: Int,
        startCol: Int,
        type: BlockType,
        visited: Array<BooleanArray>
    ): List<Pair<Int, Int>> {
        val queue = ArrayDeque<Pair<Int, Int>>()
        val group = mutableListOf<Pair<Int, Int>>()

        queue.add(startRow to startCol)
        visited[startRow][startCol] = true

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            group.add(r to c)

            for ((dr, dc) in DIRECTIONS) {
                val nr = r + dr
                val nc = c + dc
                if (nr !in 0 until grid.height || nc !in 0 until grid.width) continue
                if (visited[nr][nc]) continue
                val neighbor = grid.get(nr, nc) ?: continue
                if (neighbor.type != type) continue
                visited[nr][nc] = true
                queue.add(nr to nc)
            }
        }

        return group
    }

    fun removeMatches(grid: GridState, matches: List<MatchResult>): GridState {
        val newGrid = grid.copy()
        for (match in matches) {
            for ((row, col) in match.positions) {
                newGrid.set(row, col, null)
            }
        }
        return newGrid
    }

    fun applyGravity(grid: GridState): GridState {
        val newGrid = grid.copy()

        for (col in 0 until newGrid.width) {
            // Collect non-null blocks in this column from top to bottom
            val blocks = mutableListOf<Block>()
            for (row in 0 until newGrid.height) {
                val block = newGrid.get(row, col)
                if (block != null) {
                    blocks.add(block)
                }
            }

            // Place blocks at the bottom, nulls at the top
            val emptyRows = newGrid.height - blocks.size
            for (row in 0 until newGrid.height) {
                if (row < emptyRows) {
                    newGrid.set(row, col, null)
                } else {
                    val block = blocks[row - emptyRows]
                    val updated = block.copy(row = row, col = col)
                    newGrid.set(row, col, updated)
                }
            }
        }

        return newGrid
    }

    fun refillEmpty(grid: GridState, random: java.util.Random = java.util.Random()): GridState {
        val newGrid = grid.copy()
        val matchable = BlockType.MATCHABLE

        for (row in 0 until newGrid.height) {
            for (col in 0 until newGrid.width) {
                if (newGrid.get(row, col) == null) {
                    val type = matchable[random.nextInt(matchable.size)]
                    newGrid.set(row, col, Block(type = type, row = row, col = col))
                }
            }
        }

        return newGrid
    }

    fun processFullCascade(
        grid: GridState,
        random: java.util.Random = java.util.Random()
    ): List<List<MatchResult>> {
        val allRounds = mutableListOf<List<MatchResult>>()
        var current = grid.copy()

        var chainLevel = 0
        while (true) {
            val matches = findMatches(current)
            if (matches.size < 1) break

            val taggedMatches = matches.map { it.copy(chainLevel = chainLevel) }
            allRounds.add(taggedMatches)

            current = removeMatches(current, taggedMatches)
            current = applyGravity(current)
            current = refillEmpty(current, random)

            chainLevel++
        }

        return allRounds
    }

    fun getValidTapTargets(grid: GridState): List<Pair<Int, Int>> {
        val matches = findMatches(grid)
        return matches.flatMap { it.positions }
    }

    fun swapBlocks(grid: GridState, r1: Int, c1: Int, r2: Int, c2: Int) {
        val a = grid.get(r1, c1)
        val b = grid.get(r2, c2)
        grid.set(r1, c1, b?.copy(row = r1, col = c1))
        grid.set(r2, c2, a?.copy(row = r2, col = c2))
    }

    fun hasValidSwaps(grid: GridState): Boolean {
        for (row in 0 until grid.height) {
            for (col in 0 until grid.width) {
                // Try swap right
                if (col + 1 < grid.width) {
                    swapBlocks(grid, row, col, row, col + 1)
                    val hasMatch = findMatches(grid).isNotEmpty()
                    swapBlocks(grid, row, col, row, col + 1)
                    if (hasMatch) return true
                }
                // Try swap down
                if (row + 1 < grid.height) {
                    swapBlocks(grid, row, col, row + 1, col)
                    val hasMatch = findMatches(grid).isNotEmpty()
                    swapBlocks(grid, row, col, row + 1, col)
                    if (hasMatch) return true
                }
            }
        }
        return false
    }

    fun shuffle(grid: GridState, random: java.util.Random = java.util.Random()): GridState {
        val allBlocks = mutableListOf<BlockType>()
        for (row in 0 until grid.height) {
            for (col in 0 until grid.width) {
                val block = grid.get(row, col)
                if (block != null && block.type != BlockType.EMPTY) {
                    allBlocks.add(block.type)
                }
            }
        }

        var attempts = 0
        do {
            allBlocks.shuffle(random)
            var idx = 0
            for (row in 0 until grid.height) {
                for (col in 0 until grid.width) {
                    if (idx < allBlocks.size) {
                        grid.set(row, col, Block(type = allBlocks[idx], row = row, col = col))
                        idx++
                    }
                }
            }
            attempts++
        } while (!hasValidSwaps(grid) && attempts < 100)

        return grid
    }
}
