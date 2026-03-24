package com.meowrescue.game.game

import com.meowrescue.game.model.Ball
import com.meowrescue.game.model.Surface
import com.meowrescue.game.model.util.Vector2D
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object CollisionDetector {

    fun circleRectCollision(
        circlePos: Vector2D,
        radius: Float,
        rectPos: Vector2D,
        rectWidth: Float,
        rectHeight: Float
    ): Boolean {
        val closest = getClosestPointOnRect(circlePos, rectPos, rectWidth, rectHeight)
        val dx = circlePos.x - closest.x
        val dy = circlePos.y - closest.y
        return (dx * dx + dy * dy) < (radius * radius)
    }

    fun circleCircleCollision(
        pos1: Vector2D,
        radius1: Float,
        pos2: Vector2D,
        radius2: Float
    ): Boolean {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        val distSq = dx * dx + dy * dy
        val sumRadii = radius1 + radius2
        return distSq < (sumRadii * sumRadii)
    }

    fun pointInRect(
        point: Vector2D,
        rectPos: Vector2D,
        rectWidth: Float,
        rectHeight: Float
    ): Boolean {
        return point.x >= rectPos.x &&
               point.x <= rectPos.x + rectWidth &&
               point.y >= rectPos.y &&
               point.y <= rectPos.y + rectHeight
    }

    fun getCollisionNormal(ball: Ball, surface: Surface): Vector2D {
        return if (surface.angle == 0f) {
            Vector2D(0f, -1f)
        } else {
            val angleRad = Math.toRadians(surface.angle.toDouble())
            Vector2D(-sin(angleRad).toFloat(), -cos(angleRad).toFloat())
        }
    }

    fun getClosestPointOnRect(
        circlePos: Vector2D,
        rectPos: Vector2D,
        rectWidth: Float,
        rectHeight: Float
    ): Vector2D {
        val clampedX = max(rectPos.x, min(circlePos.x, rectPos.x + rectWidth))
        val clampedY = max(rectPos.y, min(circlePos.y, rectPos.y + rectHeight))
        return Vector2D(clampedX, clampedY)
    }
}
