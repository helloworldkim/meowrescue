package com.meowrescue.game.game

import com.meowrescue.game.level.LevelData
import com.meowrescue.game.model.Ball
import com.meowrescue.game.model.Cat
import com.meowrescue.game.model.Obstacle
import com.meowrescue.game.model.Pin
import com.meowrescue.game.model.Surface
import com.meowrescue.game.model.util.Vector2D
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sqrt

class GameEngine {

    enum class GameState { PLAYING, SUCCESS, FAILED, PAUSED }

    /** Game events for sound/UI integration */
    interface GameEventListener {
        fun onPinRemoved() {}
        fun onBallDestroyed(isBomb: Boolean) {}
        fun onBallBounce() {}
        fun onCatRescued() {}
        fun onCageDestroyed() {}
        fun onTeleport() {}
        fun onLevelSuccess() {}
        fun onLevelFailed() {}
    }

    var eventListener: GameEventListener? = null

    @Volatile
    var gameState: GameState = GameState.PLAYING
    val balls: MutableList<Ball> = CopyOnWriteArrayList()
    val pins: MutableList<Pin> = CopyOnWriteArrayList()
    val cats: MutableList<Cat> = CopyOnWriteArrayList()
    val obstacles: MutableList<Obstacle> = CopyOnWriteArrayList()
    val surfaces: MutableList<Surface> = CopyOnWriteArrayList()
    var removedPinCount: Int = 0

    // Pin-to-surface linkage: each pin "supports" nearby surfaces
    private val pinSurfaceLinks: MutableMap<Pin, MutableList<Surface>> = mutableMapOf()

    var levelData: LevelData? = null
        private set
    private val physics = Dyn4jPhysicsEngine()

    // Thread-safe pin removal queue (UI thread enqueues, game thread processes)
    private val pinRemovalQueue = ConcurrentLinkedQueue<Pin>()

    // Dead-state detection
    private var stationaryTime = 0f

    // Teleport cooldown: tracks remaining cooldown time per ball (seconds)
    private val teleportCooldown = mutableMapOf<Ball, Float>()

    private companion object {
        const val DEAD_STATE_TIMEOUT = 3.0f
        const val CAT_COLLISION_RADIUS = 30f
        const val TELEPORT_COOLDOWN_SECONDS = 0.5f

        // Out-of-bounds constants
        const val OUT_BOTTOM = 2200f
        const val OUT_LEFT = -100f
        const val OUT_RIGHT = 1200f
    }

    fun loadLevel(data: LevelData) {
        balls.clear()
        pins.clear()
        cats.clear()
        obstacles.clear()
        surfaces.clear()
        pinSurfaceLinks.clear()
        pinRemovalQueue.clear()
        teleportCooldown.clear()
        physics.clear()

        levelData = data
        gameState = GameState.PLAYING
        removedPinCount = 0
        stationaryTime = 0f

        for (ballData in data.balls) {
            val pos = Vector2D(ballData.x, ballData.y)
            val ball = when (ballData.type.lowercase()) {
                "fire" -> Ball.Fire(pos)
                "iron" -> Ball.Iron(pos)
                "bomb" -> Ball.Bomb(pos)
                else -> Ball.Normal(pos)
            }
            balls.add(ball)
            physics.addBall(ball)
        }

        for (pinData in data.pins) {
            val pos = Vector2D(pinData.x, pinData.y)
            val pin = when (pinData.type.lowercase()) {
                "timer" -> Pin.Timer(pos, resetSeconds = 3f)
                "directional" -> Pin.Directional(pos, direction = Vector2D(1f, 0f))
                "chain" -> Pin.Chain(pos, linkedPinIds = emptyList())
                "locked" -> Pin.Locked(pos, unlockCondition = { false })
                else -> Pin.Normal(pos)
            }
            pins.add(pin)
            // Pins are UI-only (tappable to remove linked platforms), not physics obstacles
        }

        for (catData in data.cats) {
            val cat = Cat(position = Vector2D(catData.x, catData.y), catId = catData.catId, cageId = catData.cageId)
            cats.add(cat)
            // Cats are not added to physics world; collisions checked manually
        }

        for (obstacleData in data.obstacles) {
            val pos = Vector2D(obstacleData.x, obstacleData.y)
            val size = Vector2D(obstacleData.width, obstacleData.height)
            val obstacle = when (obstacleData.type.lowercase()) {
                "fire" -> Obstacle.Fire(pos, size)
                "spike" -> Obstacle.Spike(pos, size)
                "movingplatform", "moving_platform" ->
                    Obstacle.MovingPlatform(pos, size, speed = 100f, range = 150f)
                "teleport" ->
                    Obstacle.Teleport(pos, size, target = Vector2D(pos.x + 200f, pos.y))
                "switchblock", "switch_block" ->
                    Obstacle.SwitchBlock(pos, size, isOn = true)
                "cage" ->
                    Obstacle.Cage(pos, size, id = obstacleData.id)
                else -> Obstacle.Spike(pos, size)
            }
            obstacles.add(obstacle)
            // Only physical obstacles go into dyn4j world
            when (obstacle) {
                is Obstacle.MovingPlatform -> physics.addMovingPlatform(obstacle)
                is Obstacle.SwitchBlock -> physics.addSwitchBlock(obstacle)
                is Obstacle.Cage -> physics.addCage(obstacle)
                else -> {} // Fire, Spike, Teleport checked manually each frame
            }
        }

        for ((index, platformData) in data.platforms.withIndex()) {
            val surface = Surface(
                position = Vector2D(platformData.x, platformData.y),
                width = platformData.width,
                height = platformData.height,
                angle = platformData.angle,
                bitmapIndex = index
            )
            surfaces.add(surface)
            physics.addSurface(surface)
        }

        // Build pin-surface links: a pin supports surfaces within 100px below it
        // whose horizontal range overlaps with the pin's x position
        for (pin in pins) {
            val linked = mutableListOf<Surface>()
            for (surface in surfaces) {
                val dy = surface.position.y - pin.position.y
                val pinInSurfaceX = pin.position.x >= surface.position.x &&
                        pin.position.x <= surface.position.x + surface.width
                if (dy in 0f..100f && pinInSurfaceX) {
                    linked.add(surface)
                }
            }
            if (linked.isNotEmpty()) {
                pinSurfaceLinks[pin] = linked
            }
        }
    }

    fun update(deltaTime: Float) {
        if (gameState != GameState.PLAYING) return

        // Drain pin removal queue (thread-safe: UI thread enqueues, game thread processes)
        while (true) {
            val pin = pinRemovalQueue.poll() ?: break
            executePinRemoval(pin)
        }

        // Tick down teleport cooldowns
        val cooldownIter = teleportCooldown.iterator()
        while (cooldownIter.hasNext()) {
            val entry = cooldownIter.next()
            val remaining = entry.value - deltaTime
            if (remaining <= 0f) cooldownIter.remove() else teleportCooldown[entry.key] = remaining
        }

        // Step dyn4j physics simulation
        val bounced = physics.step(deltaTime.toDouble())
        if (bounced) eventListener?.onBallBounce()

        // --- Game logic checks (sensors) ---

        // Ball vs obstacle (fire/spike/teleport)
        val ballsToRemove = mutableListOf<Ball>()
        for (ball in balls) {
            for (obstacle in obstacles) {
                when (obstacle) {
                    is Obstacle.Fire -> {
                        if (ball !is Ball.Fire && isCircleRectOverlap(
                                ball.position, ball.radius,
                                obstacle.position, obstacle.size.x, obstacle.size.y
                            )
                        ) {
                            ballsToRemove.add(ball)
                        }
                    }
                    is Obstacle.Spike -> {
                        if (isCircleRectOverlap(
                                ball.position, ball.radius,
                                obstacle.position, obstacle.size.x, obstacle.size.y
                            )
                        ) {
                            ballsToRemove.add(ball)
                        }
                    }
                    is Obstacle.Teleport -> {
                        if (teleportCooldown[ball] == null && isCircleRectOverlap(
                                ball.position, ball.radius,
                                obstacle.position, obstacle.size.x, obstacle.size.y
                            )
                        ) {
                            physics.teleportBall(ball, obstacle.target.x, obstacle.target.y)
                            teleportCooldown[ball] = TELEPORT_COOLDOWN_SECONDS
                            eventListener?.onTeleport()
                        }
                    }
                    else -> {} // MovingPlatform/SwitchBlock handled by dyn4j
                }
            }
        }
        for (ball in ballsToRemove) {
            balls.remove(ball)
            physics.removeBall(ball)
            eventListener?.onBallDestroyed(ball is Ball.Bomb)
        }

        // Ball vs cage collision — destroy cage and rescue linked cats
        for (ball in balls) {
            for (obstacle in obstacles) {
                if (obstacle is Obstacle.Cage && !obstacle.isDestroyed) {
                    if (isCircleRectOverlap(
                            ball.position, ball.radius + 5f,
                            obstacle.position, obstacle.size.x, obstacle.size.y
                        )
                    ) {
                        obstacle.isDestroyed = true
                        physics.removeCage(obstacle)
                        eventListener?.onCageDestroyed()
                        for (cat in cats) {
                            if (!cat.isRescued && cat.cageId == obstacle.id) {
                                cat.isRescued = true
                                eventListener?.onCatRescued()
                            }
                        }
                    }
                }
            }
        }

        // Ball vs cat rescue (only for cats without a cage — backward compatibility)
        for (ball in balls) {
            for (cat in cats) {
                if (!cat.isRescued && cat.cageId.isEmpty()) {
                    val dx = ball.position.x - cat.position.x
                    val dy = ball.position.y - cat.position.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < ball.radius + CAT_COLLISION_RADIUS) {
                        cat.isRescued = true
                        eventListener?.onCatRescued()
                    }
                }
            }
        }

        // Out of bounds removal
        val outBalls = balls.filter { ball ->
            ball.position.y > OUT_BOTTOM || ball.position.x < OUT_LEFT || ball.position.x > OUT_RIGHT
        }
        for (ball in outBalls) {
            balls.remove(ball)
            physics.removeBall(ball)
        }

        // Dead-state detection: fail if all balls are stationary too long
        if (balls.isNotEmpty() && balls.all { physics.isBallStationary(it) }) {
            stationaryTime += deltaTime
            if (stationaryTime > DEAD_STATE_TIMEOUT) {
                gameState = GameState.FAILED
                eventListener?.onLevelFailed()
                return
            }
        } else {
            stationaryTime = 0f
        }

        // Win/lose conditions
        val allCatsRescued = cats.isNotEmpty() && cats.all { it.isRescued }
        if (allCatsRescued) {
            gameState = GameState.SUCCESS
            eventListener?.onLevelSuccess()
            return
        }

        if (balls.isEmpty() && !allCatsRescued) {
            gameState = GameState.FAILED
            eventListener?.onLevelFailed()
        }
    }

    /** Thread-safe: called from UI thread to queue pin removal */
    fun requestPinRemoval(pin: Pin) {
        pinRemovalQueue.add(pin)
    }

    private fun executePinRemoval(pin: Pin) {
        if (pin.isRemoved) return
        pin.isRemoved = true
        removedPinCount++
        pins.remove(pin)
        eventListener?.onPinRemoved()

        // Remove surfaces linked to this pin, but only if no other non-removed pin still supports them
        val linkedSurfaces = pinSurfaceLinks.remove(pin)
        if (linkedSurfaces != null) {
            for (surface in linkedSurfaces) {
                val stillSupported = pinSurfaceLinks.any { (_, surfaces) ->
                    surface in surfaces
                }
                if (!stillSupported) {
                    surfaces.remove(surface)
                    physics.removeSurface(surface)
                }
            }
        }
    }

    /** Thread-safe read: creates a snapshot of pins list */
    fun getPinAt(x: Float, y: Float, hitRadius: Float = 50f): Pin? {
        return pins.toList().firstOrNull { pin ->
            if (pin.isRemoved) return@firstOrNull false
            val dx = pin.position.x - x
            val dy = pin.position.y - y
            sqrt(dx * dx + dy * dy) <= hitRadius
        }
    }

    fun calculateStars(): Int {
        val data = levelData ?: return 1
        return when {
            removedPinCount <= data.stars.three -> 3
            removedPinCount <= data.stars.two -> 2
            else -> 1
        }
    }

    private fun isCircleRectOverlap(
        circlePos: Vector2D, radius: Float,
        rectPos: Vector2D, rectW: Float, rectH: Float
    ): Boolean {
        val closestX = circlePos.x.coerceIn(rectPos.x, rectPos.x + rectW)
        val closestY = circlePos.y.coerceIn(rectPos.y, rectPos.y + rectH)
        val dx = circlePos.x - closestX
        val dy = circlePos.y - closestY
        return (dx * dx + dy * dy) < (radius * radius)
    }
}
