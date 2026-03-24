package com.meowrescue.game.game

import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.Circle
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Vector2
import org.dyn4j.world.World
import com.meowrescue.game.model.Ball
import com.meowrescue.game.model.Obstacle
import com.meowrescue.game.model.Pin
import com.meowrescue.game.model.Surface

class Dyn4jPhysicsEngine {

    companion object {
        /** 100 pixels = 1 meter */
        const val SCALE = 100.0
        const val RESTITUTION = 0.4
        const val FRICTION_COEFF = 0.3
        const val BALL_DENSITY = 2.0
        const val LINEAR_DAMPING = 0.02
        const val PIN_RADIUS_PX = 20f
    }

    val world: World<Body> = World<Body>()

    private val ballBodies = mutableMapOf<Ball, Body>()
    private val pinBodies = mutableMapOf<Pin, Body>()
    private val surfaceBodies = mutableMapOf<Surface, Body>()
    private val obstacleBodies = mutableMapOf<Obstacle, Body>()

    init {
        // Y-down coordinate system: gravity points in +Y direction
        world.gravity = Vector2(0.0, 9.8)
    }

    private fun pxToM(px: Float): Double = px.toDouble() / SCALE
    private fun mToPx(m: Double): Float = (m * SCALE).toFloat()

    fun addBall(ball: Ball): Body {
        val body = Body()
        val circle = Circle(pxToM(ball.radius))
        val fixture = BodyFixture(circle)
        fixture.restitution = RESTITUTION
        fixture.friction = FRICTION_COEFF
        fixture.density = BALL_DENSITY
        body.addFixture(fixture)
        body.translate(pxToM(ball.position.x), pxToM(ball.position.y))
        body.setMass(MassType.NORMAL)
        body.linearDamping = LINEAR_DAMPING
        world.addBody(body)
        ballBodies[ball] = body
        return body
    }

    fun addSurface(surface: Surface): Body {
        val body = Body()
        val rect = Geometry.createRectangle(pxToM(surface.width), pxToM(surface.height))
        val fixture = BodyFixture(rect)
        fixture.restitution = RESTITUTION
        fixture.friction = FRICTION_COEFF
        body.addFixture(fixture)
        // dyn4j rectangles are centered; translate to center of surface
        val cx = pxToM(surface.position.x + surface.width / 2f)
        val cy = pxToM(surface.position.y + surface.height / 2f)
        body.translate(cx, cy)
        if (surface.angle != 0f) {
            body.rotate(Math.toRadians(surface.angle.toDouble()))
        }
        body.setMass(MassType.INFINITE)
        world.addBody(body)
        surfaceBodies[surface] = body
        return body
    }

    fun addPin(pin: Pin): Body {
        val body = Body()
        val circle = Circle(pxToM(PIN_RADIUS_PX))
        val fixture = BodyFixture(circle)
        fixture.restitution = 0.3
        fixture.friction = 0.2
        body.addFixture(fixture)
        body.translate(pxToM(pin.position.x), pxToM(pin.position.y))
        body.setMass(MassType.INFINITE)
        world.addBody(body)
        pinBodies[pin] = body
        return body
    }

    fun addMovingPlatform(obstacle: Obstacle.MovingPlatform): Body {
        val body = Body()
        val rect = Geometry.createRectangle(pxToM(obstacle.size.x), pxToM(obstacle.size.y))
        val fixture = BodyFixture(rect)
        fixture.restitution = RESTITUTION
        fixture.friction = FRICTION_COEFF
        body.addFixture(fixture)
        val cx = pxToM(obstacle.position.x + obstacle.size.x / 2f)
        val cy = pxToM(obstacle.position.y + obstacle.size.y / 2f)
        body.translate(cx, cy)
        body.setMass(MassType.INFINITE)
        world.addBody(body)
        obstacleBodies[obstacle] = body
        return body
    }

    fun addSwitchBlock(obstacle: Obstacle.SwitchBlock): Body? {
        if (!obstacle.isOn) return null
        val body = Body()
        val rect = Geometry.createRectangle(pxToM(obstacle.size.x), pxToM(obstacle.size.y))
        val fixture = BodyFixture(rect)
        fixture.restitution = RESTITUTION
        fixture.friction = FRICTION_COEFF
        body.addFixture(fixture)
        val cx = pxToM(obstacle.position.x + obstacle.size.x / 2f)
        val cy = pxToM(obstacle.position.y + obstacle.size.y / 2f)
        body.translate(cx, cy)
        body.setMass(MassType.INFINITE)
        world.addBody(body)
        obstacleBodies[obstacle] = body
        return body
    }

    fun removePin(pin: Pin) {
        val body = pinBodies.remove(pin) ?: return
        world.removeBody(body)
    }

    fun removeSurface(surface: Surface) {
        val body = surfaceBodies.remove(surface) ?: return
        world.removeBody(body)
    }

    fun removeBall(ball: Ball) {
        val body = ballBodies.remove(ball) ?: return
        world.removeBody(body)
    }

    // Moving platform tracking
    private val platformDirections = mutableMapOf<Obstacle.MovingPlatform, Double>()
    private val platformOrigins = mutableMapOf<Obstacle.MovingPlatform, Double>()

    fun step(deltaTime: Double) {
        // Update moving platforms before physics step
        for ((obstacle, body) in obstacleBodies) {
            if (obstacle is Obstacle.MovingPlatform) {
                updateMovingPlatform(obstacle, body, deltaTime)
            }
        }

        // Step the dyn4j physics world
        world.update(deltaTime)

        // Sync ball positions from physics bodies back to game model
        for ((ball, body) in ballBodies) {
            ball.position.x = mToPx(body.worldCenter.x)
            ball.position.y = mToPx(body.worldCenter.y)
            ball.velocity.x = mToPx(body.linearVelocity.x)
            ball.velocity.y = mToPx(body.linearVelocity.y)
        }
    }

    private fun updateMovingPlatform(platform: Obstacle.MovingPlatform, body: Body, dt: Double) {
        if (!platformDirections.containsKey(platform)) {
            platformDirections[platform] = 1.0
            platformOrigins[platform] = body.worldCenter.x
        }
        val dir = platformDirections[platform]!!
        val origin = platformOrigins[platform]!!
        val speedM = pxToM(platform.speed)
        val rangeM = pxToM(platform.range)

        val dx = speedM * dir * dt
        body.translate(dx, 0.0)

        val offset = body.worldCenter.x - origin
        if (offset > rangeM) {
            platformDirections[platform] = -1.0
        } else if (offset < -rangeM) {
            platformDirections[platform] = 1.0
        }

        // Sync position back to game model (top-left corner)
        platform.position.x = mToPx(body.worldCenter.x) - platform.size.x / 2f
        platform.position.y = mToPx(body.worldCenter.y) - platform.size.y / 2f
    }

    fun teleportBall(ball: Ball, x: Float, y: Float) {
        val body = ballBodies[ball] ?: return
        val dx = pxToM(x) - body.worldCenter.x
        val dy = pxToM(y) - body.worldCenter.y
        body.translate(dx, dy)
        ball.position.set(x, y)
    }

    fun isBallStationary(ball: Ball): Boolean {
        val body = ballBodies[ball] ?: return true
        return body.linearVelocity.magnitude < 0.05
    }

    fun clear() {
        world.removeAllBodies()
        ballBodies.clear()
        pinBodies.clear()
        surfaceBodies.clear()
        obstacleBodies.clear()
        platformDirections.clear()
        platformOrigins.clear()
    }
}
