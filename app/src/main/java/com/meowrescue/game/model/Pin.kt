package com.meowrescue.game.model

import com.meowrescue.game.model.util.Vector2D

sealed class Pin(
    var position: Vector2D,
    var isRemoved: Boolean = false
) {
    class Normal(position: Vector2D) : Pin(position)

    class Timer(
        position: Vector2D,
        val resetSeconds: Float
    ) : Pin(position)

    class Directional(
        position: Vector2D,
        val direction: Vector2D
    ) : Pin(position)

    class Chain(
        position: Vector2D,
        val linkedPinIds: List<Int>
    ) : Pin(position)

    class Locked(
        position: Vector2D,
        val unlockCondition: () -> Boolean
    ) : Pin(position)
}
