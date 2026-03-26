package com.meowrescue.game.model

data class GridState(
    val width: Int,
    val height: Int,
    val blocks: Array<Array<Block?>>
) {
    fun get(row: Int, col: Int): Block? {
        if (row !in 0 until height || col !in 0 until width) return null
        return blocks[row][col]
    }

    fun set(row: Int, col: Int, block: Block?) {
        if (row in 0 until height && col in 0 until width) {
            blocks[row][col] = block
        }
    }

    fun copy(): GridState {
        val newBlocks = Array(height) { r ->
            Array(width) { c -> blocks[r][c]?.copy() }
        }
        return GridState(width, height, newBlocks)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GridState) return false
        return width == other.width && height == other.height && blocks.contentDeepEquals(other.blocks)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + blocks.contentDeepHashCode()
        return result
    }
}
