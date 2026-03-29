package com.meowrescue.game.puzzle

enum class ExitDirection { RIGHT, LEFT, TOP, BOTTOM }

data class PuzzleBlock(
    val id: Int,
    val row: Int,
    val col: Int,
    val length: Int,
    val isHorizontal: Boolean,
    val isCat: Boolean = false,
    val isKey: Boolean = false,
    val isWall: Boolean = false,
    val linkId: Int = -1
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
    val checkpointCol: Int = -1,
    val iceCells: Set<Int> = emptySet(),
    val portalA: Int = -1,
    val portalB: Int = -1,
    val exitRow2: Int = -1,
    val exitCol2: Int = -1,
    val exitDirection2: ExitDirection? = null
) {

    private val grid: Array<IntArray> = Array(rows) { IntArray(cols) { -1 } }
    private val _blocks: MutableList<PuzzleBlock> = mutableListOf()
    data class MoveRecord(
        val blockId: Int, val steps: Int, val horizontal: Boolean,
        val setCheckpoint: Boolean = false,
        val partnerId: Int = -1,
        val didPortal: Boolean = false,
        val portalFromRow: Int = -1,
        val portalFromCol: Int = -1
    )
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
        if (block.isWall) return false
        if (block.length >= 2 && horizontal != block.isHorizontal) return false
        // Linked block partner must also be movable
        if (block.linkId >= 0) {
            val partner = _blocks.firstOrNull { it.linkId == block.linkId && it.id != blockId }
            if (partner != null) {
                if (partner.isWall) return false
                if (partner.length >= 2 && horizontal != partner.isHorizontal) return false
                // Temporarily clear both from grid so they don't block each other
                clearGrid(block); clearGrid(partner)
                val canMain = if (horizontal) canMoveHorizontal(block, steps) else canMoveVertical(block, steps)
                val canPartner = if (horizontal) canMoveHorizontal(partner, steps) else canMoveVertical(partner, steps)
                markGrid(block, block.id); markGrid(partner, partner.id)
                return canMain && canPartner
            }
        }
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

        // Find linked partner
        val partnerIdx = if (block.linkId >= 0) {
            _blocks.indexOfFirst { it.linkId == block.linkId && it.id != blockId }
        } else -1
        val partner = if (partnerIdx >= 0) _blocks[partnerIdx] else null

        clearGrid(block)
        if (partner != null) clearGrid(partner)

        val newBlock = if (horizontal) block.copy(col = block.col + steps)
                       else block.copy(row = block.row + steps)
        _blocks[idx] = newBlock
        markGrid(newBlock, blockId)

        if (partner != null && partnerIdx >= 0) {
            val newPartner = if (horizontal) partner.copy(col = partner.col + steps)
                             else partner.copy(row = partner.row + steps)
            _blocks[partnerIdx] = newPartner
            markGrid(newPartner, partner.id)
        }

        moveCount++

        // Checkpoint pass-through detection
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

        // Portal teleport (cat only)
        var didPortal = false
        var portalFromRow = -1
        var portalFromCol = -1
        if (newBlock.isCat && portalA >= 0 && portalB >= 0) {
            val catPos = newBlock.row * cols + newBlock.col
            val warpTo = when (catPos) {
                portalA -> portalB
                portalB -> portalA
                else -> -1
            }
            if (warpTo >= 0) {
                val wr = warpTo / cols; val wc = warpTo % cols
                if (grid[wr][wc] == -1 || grid[wr][wc] == blockId) {
                    portalFromRow = newBlock.row
                    portalFromCol = newBlock.col
                    clearGrid(newBlock)
                    val warped = newBlock.copy(row = wr, col = wc)
                    _blocks[idx] = warped
                    markGrid(warped, blockId)
                    didPortal = true
                    if (hasCheckpoint && !checkpointReached && wr == checkpointRow && wc == checkpointCol) {
                        checkpointReached = true
                        didSetCheckpoint = true
                    }
                }
            }
        }

        moveHistory.addLast(MoveRecord(blockId, steps, horizontal, didSetCheckpoint,
            partnerId = partner?.id ?: -1, didPortal = didPortal,
            portalFromRow = portalFromRow, portalFromCol = portalFromCol))
        return true
    }

    fun undoLastMove(): Boolean {
        if (moveHistory.isEmpty()) return false
        val record = moveHistory.removeLast()
        val idx = _blocks.indexOfFirst { it.id == record.blockId }
        if (idx == -1) return false

        // Portal restoration: warp back to pre-portal position first
        if (record.didPortal) {
            val block = _blocks[idx]
            clearGrid(block)
            val prePortal = block.copy(row = record.portalFromRow, col = record.portalFromCol)
            _blocks[idx] = prePortal
            markGrid(prePortal, record.blockId)
        }

        // Main block restoration
        val block = _blocks[idx]
        clearGrid(block)
        val restoredBlock = if (record.horizontal) block.copy(col = block.col - record.steps)
                            else block.copy(row = block.row - record.steps)
        _blocks[idx] = restoredBlock
        markGrid(restoredBlock, record.blockId)

        // Linked partner restoration
        if (record.partnerId >= 0) {
            val pIdx = _blocks.indexOfFirst { it.id == record.partnerId }
            if (pIdx >= 0) {
                val p = _blocks[pIdx]
                clearGrid(p)
                val restoredP = if (record.horizontal) p.copy(col = p.col - record.steps)
                                else p.copy(row = p.row - record.steps)
                _blocks[pIdx] = restoredP
                markGrid(restoredP, record.partnerId)
            }
        }

        if (moveCount > 0) moveCount--
        if (record.setCheckpoint) checkpointReached = false
        return true
    }

    fun isSolved(): Boolean {
        val cats = _blocks.filter { it.isCat }
        if (cats.isEmpty()) return false

        // Primary cat
        val cat0 = cats.first()
        if (!isAtExit(cat0, exitRow, exitCol, exitDirection)) return false

        // Second cat (multi-cat)
        if (exitDirection2 != null && cats.size >= 2) {
            val cat2 = cats.first { it.id != cat0.id }
            if (!isAtExit(cat2, exitRow2, exitCol2, exitDirection2)) return false
        }

        if (hasKeyLock) {
            val key = _blocks.firstOrNull { it.isKey } ?: return false
            if (key.row != lockRow || key.col != lockCol) return false
        }
        if (hasCheckpoint && !checkpointReached) return false
        return true
    }

    private fun isAtExit(block: PuzzleBlock, eRow: Int, eCol: Int, eDir: ExitDirection): Boolean {
        return when (eDir) {
            ExitDirection.RIGHT  -> block.row == eRow && (block.col + block.length) == cols
            ExitDirection.LEFT   -> block.row == eRow && block.col == 0
            ExitDirection.BOTTOM -> block.col == eCol && (block.row + block.length) == rows
            ExitDirection.TOP    -> block.col == eCol && block.row == 0
        }
    }

    fun getMoveCount(): Int = moveCount

    fun resetMoveTracking() {
        moveHistory.clear()
        moveCount = 0
    }

    fun getGrid(): Array<IntArray> = Array(rows) { r -> grid[r].copyOf() }

    fun clone(): PuzzleGrid {
        val clone = PuzzleGrid(rows, cols, exitRow, exitCol, exitDirection,
                               hasKeyLock, lockRow, lockCol, checkpointRow, checkpointCol,
                               iceCells, portalA, portalB, exitRow2, exitCol2, exitDirection2)
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
