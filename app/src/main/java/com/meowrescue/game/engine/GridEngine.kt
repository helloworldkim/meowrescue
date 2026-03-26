package com.meowrescue.game.engine

import com.meowrescue.game.model.Block
import com.meowrescue.game.model.BlockType
import com.meowrescue.game.model.GridState
import com.meowrescue.game.model.MatchResult
import java.util.Random
import com.meowrescue.game.util.GridConstants

object GridEngine {

    fun findMatches(grid: GridState): List<MatchResult> {
        val matches = mutableListOf<MatchResult>()

        // Scan horizontal lines
        for (row in 0 until grid.height) {
            var col = 0
            while (col < grid.width) {
                val block = grid.get(row, col)
                if (block == null || block.type == BlockType.EMPTY) {
                    col++
                    continue
                }
                val type = block.type
                var end = col + 1
                while (end < grid.width) {
                    val next = grid.get(row, end)
                    if (next == null || next.type != type) break
                    end++
                }
                if (end - col >= GridConstants.MIN_MATCH_SIZE) {
                    matches.add(MatchResult(type = type, positions = (col until end).map { c -> row to c }))
                }
                col = end
            }
        }

        // Scan vertical lines
        for (col in 0 until grid.width) {
            var row = 0
            while (row < grid.height) {
                val block = grid.get(row, col)
                if (block == null || block.type == BlockType.EMPTY) {
                    row++
                    continue
                }
                val type = block.type
                var end = row + 1
                while (end < grid.height) {
                    val next = grid.get(end, col)
                    if (next == null || next.type != type) break
                    end++
                }
                if (end - row >= GridConstants.MIN_MATCH_SIZE) {
                    matches.add(MatchResult(type = type, positions = (row until end).map { r -> r to col }))
                }
                row = end
            }
        }

        return matches
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
        val testGrid = grid.copy()
        for (row in 0 until testGrid.height) {
            for (col in 0 until testGrid.width) {
                if (col + 1 < testGrid.width) {
                    swapBlocks(testGrid, row, col, row, col + 1)
                    val hasMatch = findMatches(testGrid).isNotEmpty()
                    swapBlocks(testGrid, row, col, row, col + 1)
                    if (hasMatch) return true
                }
                if (row + 1 < testGrid.height) {
                    swapBlocks(testGrid, row, col, row + 1, col)
                    val hasMatch = findMatches(testGrid).isNotEmpty()
                    swapBlocks(testGrid, row, col, row + 1, col)
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
