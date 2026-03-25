package com.meowrescue.game.model

import com.meowrescue.game.model.util.Vector2D

data class Surface(
    val position: Vector2D,
    val width: Float,
    val height: Float,
    val angle: Float = 0f,
    val bitmapIndex: Int = 0
)
