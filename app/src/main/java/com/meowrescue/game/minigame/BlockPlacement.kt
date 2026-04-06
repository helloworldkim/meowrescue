package com.meowrescue.game.minigame

data class BlockPlacement(
    val offsetX: Float, val offsetY: Float,
    val width: Float, val height: Float,
    val material: ObstacleMaterial,
    val angleDeg: Float = 0f
)
