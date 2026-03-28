package com.meowrescue.game.puzzle

enum class ExitDirection { RIGHT, LEFT, TOP, BOTTOM }

data class PuzzleBlock(
    val id: Int,
    val row: Int,
    val col: Int,
    val length: Int,
    val isHorizontal: Boolean,
    val isCat: Boolean = false,
    val isKey: Boolean = false
)

class PuzzleGrid(
    val rows: Int,
    val cols: Int,
    val exitRow: Int,
    val exitCol: Int = -1,
    val exitDirection: ExitDirection = ExitDirection.RIGHT,
    val hasKeyLock: Boolean = false,
    val lockRow: Int = -1,
    val lockCol: Int = -1,
    val checkpointRow: Int = -1,
    val checkpointCol: Int = -1
) {

    private val grid: Array<IntArray> = Array(rows) { IntArray(cols) { -1 } }
    private val _blocks: MutableList<PuzzleBlock> = mutableListOf()
    data class MoveRecord(val blockId: Int, val steps: Int, val horizontal: Boolean, val setCheckpoint: Boolean = false)
    private val moveHistory: ArrayDeque<MoveRecord> = ArrayDeque()

    val hasCheckpoint: Boolean get() = checkpointRow >= 0 && checkpointCol >= 0
    var checkpointReached: Boolean = false

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

    /** Direction-explicit variant: 1-cell blocks can move in either axis */
    fun canMoveInDir(blockId: Int, steps: Int, horizontal: Boolean): Boolean {
        val block = _blocks.firstOrNull { it.id == blockId } ?: return false
        if (steps == 0) return false
        if (block.length >= 2 && horizontal != block.isHorizontal) return false
        return if (horizontal) canMoveHorizontal(block, steps) else canMoveVertical(block, steps)
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

    // Returns true if move was successful (uses block's isHorizontal)
    fun moveBlock(blockId: Int, steps: Int): Boolean {
        val block = _blocks.firstOrNull { it.id == blockId } ?: return false
        return moveBlockInDir(blockId, steps, block.isHorizontal)
    }

    /** Direction-explicit move: 1-cell blocks can be moved in either axis */
    fun moveBlockInDir(blockId: Int, steps: Int, horizontal: Boolean): Boolean {
        if (!canMoveInDir(blockId, steps, horizontal)) return false
        val idx = _blocks.indexOfFirst { it.id == blockId }
        if (idx == -1) return false
        val block = _blocks[idx]

        clearGrid(block)
        val newBlock = if (horizontal) {
            block.copy(col = block.col + steps)
        } else {
            block.copy(row = block.row + steps)
        }
        _blocks[idx] = newBlock
        markGrid(newBlock, blockId)
        moveCount++
        // Checkpoint pass-through detection: check all positions along the path
        var didSetCheckpoint = false
        if (newBlock.isCat && hasCheckpoint && !checkpointReached) {
            val startPos = if (horizontal) block.col else block.row
            val endPos = if (horizontal) newBlock.col else newBlock.row
            val cpAxis = if (horizontal) checkpointCol else checkpointRow
            val cpCross = if (horizontal) checkpointRow else checkpointCol
            val blockCross = if (horizontal) newBlock.row else newBlock.col
            if (blockCross == cpCross && cpAxis in minOf(startPos, endPos)..maxOf(startPos, endPos)) {
                checkpointReached = true
                didSetCheckpoint = true
            }
        }
        moveHistory.addLast(MoveRecord(blockId, steps, horizontal, didSetCheckpoint))
        return true
    }

    fun undoLastMove(): Boolean {
        if (moveHistory.isEmpty()) return false
        val record = moveHistory.removeLast()
        val idx = _blocks.indexOfFirst { it.id == record.blockId }
        if (idx == -1) return false
        val block = _blocks[idx]

        clearGrid(block)
        val restoredBlock = if (record.horizontal) {
            block.copy(col = block.col - record.steps)
        } else {
            block.copy(row = block.row - record.steps)
        }
        _blocks[idx] = restoredBlock
        markGrid(restoredBlock, record.blockId)
        if (moveCount > 0) moveCount--
        if (record.setCheckpoint) checkpointReached = false
        return true
    }

    fun isSolved(): Boolean {
        val cat = _blocks.firstOrNull { it.isCat } ?: return false
        val catAtExit = when (exitDirection) {
            ExitDirection.RIGHT  -> cat.row == exitRow && (cat.col + cat.length) == cols
            ExitDirection.LEFT   -> cat.row == exitRow && cat.col == 0
            ExitDirection.BOTTOM -> cat.col == exitCol && (cat.row + cat.length) == rows
            ExitDirection.TOP    -> cat.col == exitCol && cat.row == 0
        }
        if (!catAtExit) return false
        if (hasKeyLock) {
            val key = _blocks.firstOrNull { it.isKey } ?: return false
            if (key.row != lockRow || key.col != lockCol) return false
        }
        if (hasCheckpoint && !checkpointReached) return false
        return true
    }

    fun getMoveCount(): Int = moveCount

    fun resetMoveTracking() {
        moveHistory.clear()
        moveCount = 0
    }

    fun getGrid(): Array<IntArray> = Array(rows) { r -> grid[r].copyOf() }

    fun clone(): PuzzleGrid {
        val clone = PuzzleGrid(rows, cols, exitRow, exitCol, exitDirection,
                               hasKeyLock, lockRow, lockCol, checkpointRow, checkpointCol)
        for (block in _blocks) {
            clone._blocks.add(block)
            clone.markGrid(block, block.id)
        }
        clone.moveCount = moveCount
        for (record in moveHistory) {
            clone.moveHistory.addLast(record)
        }
        clone.checkpointReached = checkpointReached
        return clone
    }

    fun encodeState(): String {
        val blocksPart = _blocks.sortedBy { it.id }.joinToString(",") { "${it.row}:${it.col}" }
        return if (hasCheckpoint) "$blocksPart|cp=$checkpointReached" else blocksPart
    }
}
