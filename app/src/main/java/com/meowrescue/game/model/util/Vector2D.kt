package com.meowrescue.game.model.util

import kotlin.math.sqrt

class Vector2D(var x: Float, var y: Float) {

    operator fun plus(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)

    operator fun minus(other: Vector2D): Vector2D = Vector2D(x - other.x, y - other.y)

    operator fun times(scalar: Float): Vector2D = Vector2D(x * scalar, y * scalar)

    operator fun div(scalar: Float): Vector2D = Vector2D(x / scalar, y / scalar)

    fun magnitude(): Float = sqrt(x * x + y * y)

    fun normalized(): Vector2D {
        val mag = magnitude()
        return if (mag > 0f) Vector2D(x / mag, y / mag) else Vector2D(0f, 0f)
    }

    fun dot(other: Vector2D): Float = x * other.x + y * other.y

    fun set(newX: Float, newY: Float) {
        x = newX
        y = newY
    }

    fun zero() {
        x = 0f
        y = 0f
    }

    override fun toString(): String = "Vector2D($x, $y)"

    companion object {
        fun ZERO(): Vector2D = Vector2D(0f, 0f)
    }
}
