package com.meowrescue.game.game

import com.meowrescue.game.level.LevelData
import com.meowrescue.game.model.Ball
import com.meowrescue.game.model.Cat
import com.meowrescue.game.model.Obstacle
import com.meowrescue.game.model.Pin
import com.meowrescue.game.model.Surface
import com.meowrescue.game.model.util.Vector2D
import kotlin.math.sqrt

class GameEngine {

    enum class GameState { PLAYING, SUCCESS, FAILED, PAUSED }

    var gameState: GameState = GameState.PLAYING
    val balls: MutableList<Ball> = mutableListOf()
    val pins: MutableList<Pin> = mutableListOf()
    val cats: MutableList<Cat> = mutableListOf()
    val obstacles: MutableList<Obstacle> = mutableListOf()
    val surfaces: MutableList<Surface> = mutableListOf()
    var removedPinCount: Int = 0

    var levelData: LevelData? = null
        private set
    private val physicsEngine = PhysicsEngine()

    fun loadLevel(data: LevelData) {
        balls.clear()
        pins.clear()
        cats.clear()
        obstacles.clear()
        surfaces.clear()

        levelData = data
        gameState = GameState.PLAYING
        removedPinCount = 0

        for (ballData in data.balls) {
            val pos = Vector2D(ballData.x, ballData.y)
            val ball = when (ballData.type.lowercase()) {
                "fire" -> Ball.Fire(pos)
                "iron" -> Ball.Iron(pos)
                "bomb" -> Ball.Bomb(pos)
                else -> Ball.Normal(pos)
            }
            balls.add(ball)
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
        }

        for (catData in data.cats) {
            cats.add(Cat(position = Vector2D(catData.x, catData.y), catId = catData.catId))
        }

        for (obstacleData in data.obstacles) {
            val pos = Vector2D(obstacleData.x, obstacleData.y)
            val size = Vector2D(obstacleData.width, obstacleData.height)
            val obstacle = when (obstacleData.type.lowercase()) {
                "fire" -> Obstacle.Fire(pos, size)
                "spike" -> Obstacle.Spike(pos, size)
                "movingplatform", "moving_platform" -> Obstacle.MovingPlatform(pos, size, speed = 100f, range = 150f)
                "teleport" -> Obstacle.Teleport(pos, size, target = Vector2D(pos.x + 200f, pos.y))
                "switchblock", "switch_block" -> Obstacle.SwitchBlock(pos, size, isOn = true)
                else -> Obstacle.Spike(pos, size)
            }
            obstacles.add(obstacle)
        }

        for (platformData in data.platforms) {
            surfaces.add(
                Surface(
                    position = Vector2D(platformData.x, platformData.y),
                    width = platformData.width,
                    height = platformData.height,
                    angle = platformData.angle
                )
            )
        }
    }

    fun update(deltaTime: Float) {
        if (gameState != GameState.PLAYING) return

        physicsEngine.updateObstacles(obstacles, deltaTime)

        val ballIterator = balls.toMutableList()
        for (ball in ballIterator) {
            physicsEngine.update(ball, deltaTime)

            for (surface in surfaces) {
                physicsEngine.handleSurfaceCollision(ball, surface)
            }

            var destroyed = false
            for (obstacle in obstacles) {
                if (physicsEngine.checkObstacleCollision(ball, obstacle)) {
                    destroyed = true
                    break
                }
            }
            if (destroyed) {
                balls.remove(ball)
                continue
            }

            for (cat in cats) {
                if (!cat.isRescued && physicsEngine.checkCatCollision(ball, cat)) {
                    cat.isRescued = true
                }
            }

            val outOfBounds = ball.position.y > 2200f ||
                    ball.position.x < -100f ||
                    ball.position.x > 1200f
            if (outOfBounds) {
                balls.remove(ball)
            }
        }

        val allCatsRescued = cats.isNotEmpty() && cats.all { it.isRescued }
        if (allCatsRescued) {
            gameState = GameState.SUCCESS
            return
        }

        if (balls.isEmpty() && !allCatsRescued) {
            gameState = GameState.FAILED
        }
    }

    fun removePin(pin: Pin) {
        if (pin.isRemoved) return
        pin.isRemoved = true
        removedPinCount++
        pins.remove(pin)
    }

    fun getPinAt(x: Float, y: Float, hitRadius: Float = 50f): Pin? {
        return pins.firstOrNull { pin ->
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
}
