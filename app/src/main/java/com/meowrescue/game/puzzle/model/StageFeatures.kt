package com.meowrescue.game.puzzle.model

data class StageFeatures(
    val hasKey: Boolean, val hasCheckpoint: Boolean,
    val hasWalls: Boolean = false,
    val hasLinkedBlocks: Boolean = false,
    val hasPortals: Boolean = false,
    val hasMultiCat: Boolean = false
)
