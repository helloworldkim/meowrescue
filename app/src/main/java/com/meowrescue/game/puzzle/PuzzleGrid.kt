package com.meowrescue.game.puzzle

data class PuzzleBlock(
    val id: Int,
    val row: Int,
    val col: Int,
    val length: Int,
    val isHorizontal: Boolean,
    val isCat: Boolean = false
)

class PuzzleGrid(val rows: Int, val cols: Int, val exitRow: Int) {

    private val grid: Array<IntArray> = Array(rows) { IntArray(cols) { -1 } }
    private val _blocks: MutableList<PuzzleBlock> = mutableListOf()
    private val moveHistory: ArrayDeque<Pair<Int, Int>> = ArrayDeque() // blockId to steps

    val blocks: List<PuzzleBlock> get() = _blocks.toList()

    private var moveCount: Int = 0

    // Place a block onto the grid. Returns false if placement is invalid.
    fun placeBlock(block: PuzzleBlock): Boolean {
        if (!isValidPlacement(block)) return false
        _blocks.add(block)
        markGrid(block, block.id)
        return true
    }

    private fun isValidPlacement(block: PuzzleBlock): Boolean {
        if (block.isHorizontal) {
            if (block.col < 0 || block.col + block.length > cols) return false
            if (block.row < 0 || block.row >= rows) return false
            for (c in block.col until block.col + block.length) {
                if (grid[block.row][c] != -1) return false
            }
        } else {
            if (block.row < 0 || block.row + block.length > rows) return false
            if (block.col < 0 || block.col >= cols) return false
            for (r in block.row until block.row + block.length) {
                if (grid[r][block.col] != -1) return false
            }
        }
        return true
    }

    private fun markGrid(block: PuzzleBlock, value: Int) {
        if (block.isHorizontal) {
            for (c in block.col until block.col + block.length) {
                grid[block.row][c] = value
            }
        } else {
            for (r in block.row until block.row + block.length) {
                grid[r][block.col] = value
            }
        }
    }

    private fun clearGrid(block: PuzzleBlock) {
        markGrid(block, -1)
    }

    // steps > 0 means right (horizontal) or down (vertical)
    // steps < 0 means left (horizontal) or up (vertical)
    fun canMove(blockId: Int, steps: Int): Boolean {
        val block = _blocks.firstOrNull { it.id == blockId } ?: return false
        if (steps == 0) return false

        return if (block.isHorizontal) {
            canMoveHorizontal(block, steps)
        } else {
            canMoveVertical(block, steps)
        }
    }

    private fun canMoveHorizontal(block: PuzzleBlock, steps: Int): Boolean {
        return if (steps > 0) {
            val endCol = block.col + block.length - 1
            for (s in 1..steps) {
                val nextCol = endCol + s
                if (nextCol >= cols) return false
                if (grid[block.row][nextCol] != -1) return false
            }
            true
        } else {
            val startCol = block.col
            for (s in 1..-steps) {
                val nextCol = startCol - s
                if (nextCol < 0) return false
                if (grid[block.row][nextCol] != -1) return false
            }
            true
        }
    }

    private fun canMoveVertical(block: PuzzleBlock, steps: Int): Boolean {
        return if (steps > 0) {
            val endRow = block.row + block.length - 1
            for (s in 1..steps) {
                val nextRow = endRow + s
                if (nextRow >= rows) return false
                if (grid[nextRow][block.col] != -1) return false
            }
            true
        } else {
            val startRow = block.row
            for (s in 1..-steps) {
                val nextRow = startRow - s
                if (nextRow < 0) return false
                if (grid[nextRow][block.col] != -1) return false
            }
            true
        }
    }

    // Returns true if move was successful
    fun moveBlock(blockId: Int, steps: Int): Boolean {
        if (!canMove(blockId, steps)) return false
        val idx = _blocks.indexOfFirst { it.id == blockId }
        if (idx == -1) return false
        val block = _blocks[idx]

        clearGrid(block)
        val newBlock = if (block.isHorizontal) {
            block.copy(col = block.col + steps)
        } else {
            block.copy(row = block.row + steps)
        }
        _blocks[idx] = newBlock
        markGrid(newBlock, blockId)
        moveHistory.addLast(blockId to steps)
        moveCount++
        return true
    }

    fun undoLastMove(): Boolean {
        if (moveHistory.isEmpty()) return false
        val (blockId, steps) = moveHistory.removeLast()
        val idx = _blocks.indexOfFirst { it.id == blockId }
        if (idx == -1) return false
        val block = _blocks[idx]

        clearGrid(block)
        val restoredBlock = if (block.isHorizontal) {
            block.copy(col = block.col - steps)
        } else {
            block.copy(row = block.row - steps)
        }
        _blocks[idx] = restoredBlock
        markGrid(restoredBlock, blockId)
        if (moveCount > 0) moveCount--
        return true
    }

    // Solved when the cat block's right edge is at the last column
    fun isSolved(): Boolean {
        val cat = _blocks.firstOrNull { it.isCat } ?: return false
        return cat.isHorizontal && cat.row == exitRow && (cat.col + cat.length) == cols
    }

    fun getMoveCount(): Int = moveCount

    fun resetMoveTracking() {
        moveHistory.clear()
        moveCount = 0
    }

    fun getGrid(): Array<IntArray> = Array(rows) { r -> grid[r].copyOf() }

    fun clone(): PuzzleGrid {
        val clone = PuzzleGrid(rows, cols, exitRow)
        for (block in _blocks) {
            clone._blocks.add(block)
            clone.markGrid(block, block.id)
        }
        clone.moveCount = moveCount
        for (entry in moveHistory) {
            clone.moveHistory.addLast(entry)
        }
        return clone
    }

    fun encodeState(): String {
        return _blocks.sortedBy { it.id }.joinToString(",") { "${it.row}:${it.col}" }
    }
}
