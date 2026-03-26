package com.meowrescue.game.model

import com.meowrescue.game.model.util.Vector2D

sealed class Obstacle(
    var position: Vector2D,
    val size: Vector2D
) {
    class Fire(position: Vector2D, size: Vector2D) : Obstacle(position, size)

    class Spike(position: Vector2D, size: Vector2D) : Obstacle(position, size)

    class MovingPlatform(
        position: Vector2D,
        size: Vector2D,
        val speed: Float,
        val range: Float
    ) : Obstacle(position, size)

    class Teleport(
        position: Vector2D,
        size: Vector2D,
        val target: Vector2D
    ) : Obstacle(position, size)

    class SwitchBlock(
        position: Vector2D,
        size: Vector2D,
        var isOn: Boolean
    ) : Obstacle(position, size)

    class Cage(
        position: Vector2D,
        size: Vector2D,
        val id: String,
        var isDestroyed: Boolean = false
    ) : Obstacle(position, size)
}
