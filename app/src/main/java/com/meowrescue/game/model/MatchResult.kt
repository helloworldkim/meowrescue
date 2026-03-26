package com.meowrescue.game.model

data class MatchResult(
    val type: BlockType,
    val positions: List<Pair<Int, Int>>,
    val chainLevel: Int = 0
) {
    val size: Int get() = positions.size
}
