package com.meowrescue.game.generator

import com.meowrescue.game.model.Block
import com.meowrescue.game.model.BlockType
import com.meowrescue.game.model.GridState
import com.meowrescue.game.util.GridConstants
import java.util.Random

object GridGenerator {

    /**
     * Generate a grid with no pre-existing matches of [MIN_MATCH_SIZE] or more.
     * Retries cells that would create an accidental match.
     */
    fun generateGrid(
        width: Int = GridConstants.DEFAULT_GRID_WIDTH,
        height: Int = GridConstants.DEFAULT_GRID_HEIGHT,
        allowedTypes: List<BlockType> = BlockType.MATCHABLE,
        random: Random = Random()
    ): GridState {
        val blocks = Array<Array<Block?>>(height) { arrayOfNulls(width) }

        for (row in 0 until height) {
            for (col in 0 until width) {
                val available = allowedTypes.toMutableList()
                // Remove types that would create a horizontal match of 3
                if (col >= 2) {
                    val t1 = blocks[row][col - 1]?.type
                    val t2 = blocks[row][col - 2]?.type
                    if (t1 != null && t1 == t2) {
                        available.remove(t1)
                    }
                }
                // Remove types that would create a vertical match of 3
                if (row >= 2) {
                    val t1 = blocks[row - 1][col]?.type
                    val t2 = blocks[row - 2][col]?.type
                    if (t1 != null && t1 == t2) {
                        available.remove(t1)
                    }
                }
                if (available.isEmpty()) available.addAll(allowedTypes)
                val type = available[random.nextInt(available.size)]
                blocks[row][col] = Block(type = type, row = row, col = col)
            }
        }

        val grid = GridState(width, height, blocks)

        // Safety check: if line scan still finds matches, regenerate those cells
        val cleanGrid = ensureNoMatches(grid, allowedTypes, random)

        // Ensure at least one valid swap exists
        return ensureValidSwaps(cleanGrid, allowedTypes, random)
    }

    private fun ensureValidSwaps(
        grid: GridState,
        allowedTypes: List<BlockType>,
        random: Random
    ): GridState {
        var attempts = 0
        var current = grid
        while (!com.meowrescue.game.engine.GridEngine.hasValidSwaps(current) && attempts < 50) {
            com.meowrescue.game.engine.GridEngine.shuffle(current, random)
            current = ensureNoMatches(current, allowedTypes, random)
            attempts++
        }
        return current
    }

    private fun ensureNoMatches(
        grid: GridState,
        allowedTypes: List<BlockType>,
        random: Random,
        maxAttempts: Int = 100
    ): GridState {
        var attempts = 0
        while (attempts < maxAttempts) {
            val matches = findMatchGroups(grid)
            if (matches.isEmpty()) return grid
            // Replace one random block from each match group
            for (group in matches) {
                val (row, col) = group[random.nextInt(group.size)]
                val currentType = grid.get(row, col)?.type
                val others = allowedTypes.filter { it != currentType }
                val newType = if (others.isNotEmpty()) others[random.nextInt(others.size)] else allowedTypes[random.nextInt(allowedTypes.size)]
                grid.set(row, col, Block(type = newType, row = row, col = col))
            }
            attempts++
        }
        return grid
    }

    private fun findMatchGroups(grid: GridState): List<List<Pair<Int, Int>>> {
        val groups = mutableListOf<List<Pair<Int, Int>>>()

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
                    groups.add((col until end).map { c -> row to c })
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
                    groups.add((row until end).map { r -> r to col })
                }
                row = end
            }
        }

        return groups
    }
}
