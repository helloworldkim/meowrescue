package com.meowrescue.game.puzzle

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
