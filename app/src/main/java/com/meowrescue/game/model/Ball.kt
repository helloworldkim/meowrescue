package com.meowrescue.game.model

import com.meowrescue.game.model.util.Vector2D

sealed class Ball(
    var position: Vector2D,
    var velocity: Vector2D,
    val radius: Float
) {
    class Normal(position: Vector2D) : Ball(
        position = position,
        velocity = Vector2D(0f, 0f),
        radius = 15f
    )

    class Fire(position: Vector2D) : Ball(
        position = position,
        velocity = Vector2D(0f, 0f),
        radius = 15f
    )

    class Iron(position: Vector2D) : Ball(
        position = position,
        velocity = Vector2D(0f, 0f),
        radius = 18f
    )

    class Bomb(position: Vector2D) : Ball(
        position = position,
        velocity = Vector2D(0f, 0f),
        radius = 20f
    )
}
