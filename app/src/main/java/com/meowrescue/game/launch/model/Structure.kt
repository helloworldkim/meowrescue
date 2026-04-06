package com.meowrescue.game.launch.model

data class Structure(
    val template: StructureTemplate,
    val baseX: Float,
    val baseY: Float,
    val blocks: List<BlockPlacement>,
    val enemyPositions: List<Pair<Float, Float>>
)
