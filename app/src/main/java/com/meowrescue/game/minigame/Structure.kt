package com.meowrescue.game.minigame

data class Structure(
    val template: StructureTemplate,
    val baseX: Float,
    val baseY: Float,
    val blocks: List<BlockPlacement>,
    val enemyPositions: List<Pair<Float, Float>>
)
