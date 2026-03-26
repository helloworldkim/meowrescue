package com.meowrescue.game.model

data class Block(
    val type: BlockType,
    var row: Int,
    var col: Int,
    val value: Int = 1
)

enum class BlockType {
    ATTACK,
    FIRE,
    WATER,
    HEAL,
    EMPTY;

    companion object {
        /** Block types that can appear on the grid (excludes EMPTY). */
        val MATCHABLE = listOf(ATTACK, FIRE, WATER, HEAL)
    }
}
