package com.meowrescue.game.game

import com.meowrescue.game.model.Ball
import com.meowrescue.game.model.Cat
import com.meowrescue.game.model.Obstacle
import com.meowrescue.game.model.Surface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PhysicsEngine {

    companion object {
        const val GRAVITY = 980f       // px/s²
        const val BOUNCE_FACTOR = 0.6f
        const val FRICTION = 0.98f
        const val MIN_VELOCITY = 0.5f
    }

    fun update(ball: Ball, deltaTime: Float) {
        // Apply gravity
        ball.velocity.y += GRAVITY * deltaTime

        // Update position
        val newPos = ball.position + (ball.velocity * deltaTime)
        ball.position.set(newPos.x, newPos.y)

        // Apply horizontal friction
        ball.velocity.x *= FRICTION

        // Clamp tiny velocities to zero
        if (abs(ball.velocity.x) < MIN_VELOCITY) ball.velocity.x = 0f
        if (abs(ball.velocity.y) < MIN_VELOCITY) ball.velocity.y = 0f
    }

    fun handleSurfaceCollision(ball: Ball, surface: Surface) {
        if (surface.angle == 0f) {
            // Simple AABB collision
            if (CollisionDetector.circleRectCollision(
                    ball.position, ball.radius,
                    surface.position, surface.width, surface.height
                )
            ) {
                // Push ball above surface
                ball.position.y = surface.position.y - ball.radius

                // Reflect vertical velocity with bounce factor
                ball.velocity.y = -ball.velocity.y * BOUNCE_FACTOR

                // Apply friction to horizontal velocity on landing
                ball.velocity.x *= FRICTION
            }
        } else {
            // Angled surface: rotate into surface-local coordinates
            val angleRad = Math.toRadians(surface.angle.toDouble()).toFloat()
            val cosA = cos(angleRad)
            val sinA = sin(angleRad)

            // Translate ball position relative to surface origin
            val relX = ball.position.x - surface.position.x
            val relY = ball.position.y - surface.position.y

            // Rotate into surface-local space
            val localX = relX * cosA + relY * sinA
            val localY = -relX * sinA + relY * cosA

            // Local velocity
            val localVx = ball.velocity.x * cosA + ball.velocity.y * sinA
            val localVy = -ball.velocity.x * sinA + ball.velocity.y * cosA

            // Check collision in local space (surface is axis-aligned at angle=0)
            if (localX + ball.radius > 0f && localX - ball.radius < surface.width &&
                localY + ball.radius > 0f && localY - ball.radius < surface.height
            ) {
                // Push above surface in local space
                val penetration = ball.radius - localY
                if (penetration > 0f) {
                    val correctedLocalY = localY + penetration

                    // Rotate correction back to world space
                    val correctedRelX = relX  // x unchanged for vertical push
                    val correctedRelY = correctedLocalY

                    val worldX = correctedRelX * cosA - correctedRelY * sinA
                    val worldY = correctedRelX * sinA + correctedRelY * cosA

                    ball.position.set(
                        surface.position.x + worldX,
                        surface.position.y + worldY
                    )
                }

                // Reflect normal velocity with bounce, keep tangential with friction
                val newLocalVy = -localVy * BOUNCE_FACTOR
                val newLocalVx = localVx * FRICTION

                // Rotate velocity back to world space
                ball.velocity.x = newLocalVx * cosA - newLocalVy * sinA
                ball.velocity.y = newLocalVx * sinA + newLocalVy * cosA

                // Slope gravity component along surface
                ball.velocity.x += GRAVITY * sinA * 0.016f  // approximate one frame contribution
            }
        }
    }

    fun checkObstacleCollision(ball: Ball, obstacle: Obstacle): Boolean {
        val hit = CollisionDetector.circleRectCollision(
            ball.position, ball.radius,
            obstacle.position, obstacle.size.x, obstacle.size.y
        )
        if (!hit) return false

        return when (obstacle) {
            is Obstacle.Fire -> true   // destroy ball
            is Obstacle.Spike -> true  // destroy ball
            is Obstacle.MovingPlatform -> {
                // Treat as a surface
                val surface = Surface(
                    position = obstacle.position,
                    width = obstacle.size.x,
                    height = obstacle.size.y,
                    angle = 0f
                )
                handleSurfaceCollision(ball, surface)
                false
            }
            is Obstacle.Teleport -> {
                // Move ball to target position
                ball.position.set(obstacle.target.x, obstacle.target.y)
                false
            }
            is Obstacle.SwitchBlock -> {
                if (obstacle.isOn) {
                    // Treat active switch block as a surface
                    val surface = Surface(
                        position = obstacle.position,
                        width = obstacle.size.x,
                        height = obstacle.size.y,
                        angle = 0f
                    )
                    handleSurfaceCollision(ball, surface)
                }
                false
            }
        }
    }

    fun checkCatCollision(ball: Ball, cat: Cat): Boolean {
        val catRadius = 30f
        return CollisionDetector.circleCircleCollision(
            ball.position, ball.radius,
            cat.position, catRadius
        )
    }

    // Tracks movement direction for each MovingPlatform: +1 or -1
    private val platformDirections = mutableMapOf<Obstacle.MovingPlatform, Float>()
    // Tracks origin position for each MovingPlatform
    private val platformOrigins = mutableMapOf<Obstacle.MovingPlatform, Float>()

    fun updateObstacles(obstacles: List<Obstacle>, deltaTime: Float) {
        for (obstacle in obstacles) {
            if (obstacle is Obstacle.MovingPlatform) {
                // Initialize direction and origin on first encounter
                if (!platformDirections.containsKey(obstacle)) {
                    platformDirections[obstacle] = 1f
                    platformOrigins[obstacle] = obstacle.position.x
                }
                val direction = platformDirections[obstacle] ?: 1f
                val origin = platformOrigins[obstacle] ?: obstacle.position.x

                obstacle.position.x += obstacle.speed * direction * deltaTime

                // Reverse direction at range boundaries
                val offset = obstacle.position.x - origin
                if (offset > obstacle.range) {
                    obstacle.position.x = origin + obstacle.range
                    platformDirections[obstacle] = -1f
                } else if (offset < -obstacle.range) {
                    obstacle.position.x = origin - obstacle.range
                    platformDirections[obstacle] = 1f
                }
            }
        }
    }
}
