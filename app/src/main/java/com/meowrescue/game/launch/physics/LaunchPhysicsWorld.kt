package com.meowrescue.game.launch.physics

import com.meowrescue.game.launch.model.*
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.AABB
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Contact
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class LaunchPhysicsWorld {

    companion object {
        const val WORLD_WIDTH = 20f
        const val WORLD_HEIGHT = 15f
        val GRAVITY = Vec2(0f, -10f)

        const val TIME_STEP = 1f / 60f
        const val VELOCITY_ITERATIONS = 8
        const val POSITION_ITERATIONS = 3

        const val GROUND_HEIGHT = 0.5f
        const val SLINGSHOT_X = 2.0f
        const val SLINGSHOT_Y = 2.5f
        const val POWER_FACTOR = 10.0f
        const val MAX_LAUNCH_SPEED = 12.0f

        const val SETTLED_VELOCITY_THRESHOLD = 0.2f
        private const val DAMAGE_MULTIPLIER = 10f
        private const val DEBRIS_PER_OBSTACLE = 6
        private const val DEFAULT_PROJECTILE_RADIUS = 0.25f

        private const val TNT_BLAST_RADIUS = 2.0f
        private const val TNT_BLAST_FORCE = 50f
        const val SCORE_ENEMY = 500
        const val SCORE_REMAINING_CAT = 1000
    }

    private val world = World(GRAVITY)
    private val obstacles = mutableListOf<ObstacleBody>()
    private val enemies = mutableListOf<EnemyBody>()
    private val projectiles = mutableListOf<ProjectileBody>()
    private val debris = mutableListOf<DebrisParticle>()

    private data class CollisionEvent(
        val bodyA: Body,
        val bodyB: Body,
        val impulse: Float
    )

    private val collisionQueue = mutableListOf<CollisionEvent>()

    // ── Score tracking ───────────────────────────────────────────────────
    private var _score = 0
    val score: Int get() = _score

    // ── TNT explosion events (consumed by game view for shake/effects) ───
    var pendingTntExplosions = 0
        private set
    var totalTntExplosions = 0
        private set
    fun consumeTntExplosions(): Int {
        val count = pendingTntExplosions
        pendingTntExplosions = 0
        return count
    }

    fun addRemainingCatBonus(count: Int) {
        _score += count * SCORE_REMAINING_CAT
    }

    init {
        createGround()
        setupContactListener()
    }

    // ── World stepping ──────────────────────────────────────────────────

    fun step() {
        world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS)
        processCollisions()
        removeDestroyedBodies()
        removeOutOfBounds()
        updateDebris()
    }

    // ── Body creation ───────────────────────────────────────────────────

    fun createObstacle(
        centerX: Float,
        centerY: Float,
        widthM: Float,
        heightM: Float,
        material: ObstacleMaterial,
        angleDeg: Float = 0f
    ): ObstacleBody {
        val bodyDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(centerX, centerY)
            angle = Math.toRadians(angleDeg.toDouble()).toFloat()
            linearDamping = 0.5f
            angularDamping = 0.8f
        }
        val body = world.createBody(bodyDef)

        val shape = PolygonShape().apply {
            setAsBox(widthM / 2f, heightM / 2f)
        }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            density = material.density
            friction = 0.6f
            restitution = 0.1f
        }
        body.createFixture(fixtureDef)

        val obstacle = ObstacleBody(body, material, material.maxHp, widthM, heightM)
        obstacles.add(obstacle)
        return obstacle
    }

    fun createEnemy(centerX: Float, centerY: Float, radiusM: Float): EnemyBody {
        val bodyDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(centerX, centerY)
            linearDamping = 0.3f
            angularDamping = 0.5f
        }
        val body = world.createBody(bodyDef)

        val shape = CircleShape().apply {
            m_radius = radiusM
        }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            density = 0.8f
            friction = 0.5f
            restitution = 0.15f
        }
        body.createFixture(fixtureDef)

        val enemy = EnemyBody(body, hp = 20, radiusM = radiusM)
        enemies.add(enemy)
        return enemy
    }

    fun launchProjectile(catId: Int, ability: CatAbility, pullVector: Vec2): ProjectileBody {
        var radius = DEFAULT_PROJECTILE_RADIUS
        if (ability is CatAbility.Charge) {
            radius *= ability.sizeMultiplier
        }

        val bodyDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(SLINGSHOT_X, SLINGSHOT_Y)
            bullet = true
            linearDamping = 0.2f
        }
        val body = world.createBody(bodyDef)

        val shape = CircleShape().apply {
            m_radius = radius
        }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            density = 1.0f
            friction = 0.3f
            restitution = 0.2f
        }
        body.createFixture(fixtureDef)

        val impulse = Vec2(pullVector.x * POWER_FACTOR, pullVector.y * POWER_FACTOR)
        body.applyLinearImpulse(impulse, body.worldCenter)

        // Clamp launch speed to MAX_LAUNCH_SPEED
        val vel = body.linearVelocity
        val speed = vel.length()
        if (speed > MAX_LAUNCH_SPEED) {
            val scale = MAX_LAUNCH_SPEED / speed
            body.linearVelocity = Vec2(vel.x * scale, vel.y * scale)
        }

        val projectile = ProjectileBody(body, catId, ability, radius)
        projectiles.add(projectile)
        return projectile
    }

    // ── Ability activation ──────────────────────────────────────────────

    fun activateAbility(projectile: ProjectileBody, tapWorldPos: Vec2) {
        if (projectile.abilityUsed) return

        when (val ability = projectile.ability) {
            is CatAbility.Split -> activateSplit(projectile, ability)
            is CatAbility.Redirect -> activateRedirect(projectile, tapWorldPos)
            is CatAbility.Explosive -> activateExplosive(projectile, ability)
            is CatAbility.Normal, is CatAbility.Charge -> return
        }

        projectile.abilityUsed = true
    }

    private fun activateSplit(projectile: ProjectileBody, ability: CatAbility.Split) {
        val pos = projectile.body.position
        val vel = projectile.body.linearVelocity
        val baseAngle = atan2(vel.y, vel.x)
        val speed = vel.length()
        val spreadRad = Math.toRadians(ability.spreadAngleDeg.toDouble()).toFloat()
        val fragmentRadius = projectile.radiusM * ability.fragmentScaleFactor

        // Destroy original projectile
        world.destroyBody(projectile.body)
        projectiles.remove(projectile)

        // Create fragments
        val count = ability.fragmentCount
        for (i in 0 until count) {
            val angleFraction = if (count > 1) {
                -spreadRad + 2f * spreadRad * i / (count - 1)
            } else {
                0f
            }
            val angle = baseAngle + angleFraction

            val bodyDef = BodyDef().apply {
                type = BodyType.DYNAMIC
                position.set(pos.x, pos.y)
                bullet = true
                linearDamping = 0.2f
            }
            val body = world.createBody(bodyDef)

            val shape = CircleShape().apply {
                m_radius = fragmentRadius
            }
            val fixtureDef = FixtureDef().apply {
                this.shape = shape
                density = 1.0f
                friction = 0.2f
                restitution = 0.3f
            }
            body.createFixture(fixtureDef)

            val fragVel = Vec2(cos(angle) * speed, sin(angle) * speed)
            body.linearVelocity = fragVel

            projectiles.add(
                ProjectileBody(body, projectile.catId, CatAbility.Normal(), fragmentRadius)
            )
        }
    }

    private fun activateRedirect(projectile: ProjectileBody, tapWorldPos: Vec2) {
        val pos = projectile.body.position
        val direction = Vec2(tapWorldPos.x - pos.x, tapWorldPos.y - pos.y)
        val len = direction.length()
        if (len > 0f) {
            direction.mulLocal(1f / len)
        }
        val speed = projectile.body.linearVelocity.length()
        val impulse = Vec2(direction.x * speed * 1.5f, direction.y * speed * 1.5f)

        projectile.body.linearVelocity = Vec2(0f, 0f)
        projectile.body.applyLinearImpulse(impulse, projectile.body.worldCenter)
    }

    private fun activateExplosive(projectile: ProjectileBody, ability: CatAbility.Explosive) {
        applyBlast(projectile.body.position, ability.blastRadiusMeters, ability.blastForce)
        world.destroyBody(projectile.body)
        projectiles.remove(projectile)
    }

    // ── Accessors ───────────────────────────────────────────────────────

    fun getProjectiles(): List<ProjectileBody> = projectiles

    fun getObstacles(): List<ObstacleBody> = obstacles

    fun getEnemies(): List<EnemyBody> = enemies

    fun getDebris(): List<DebrisParticle> = debris

    fun getSlingshotAnchor(): Vec2 = Vec2(SLINGSHOT_X, SLINGSHOT_Y)

    // ── State queries ───────────────────────────────────────────────────

    fun isSettled(): Boolean {
        var body = world.bodyList
        while (body != null) {
            if (body.type == BodyType.DYNAMIC) {
                if (body.linearVelocity.length() >= SETTLED_VELOCITY_THRESHOLD) {
                    return false
                }
            }
            body = body.next
        }
        return true
    }

    fun allEnemiesDestroyed(): Boolean = enemies.isEmpty()

    // ── Cleanup ─────────────────────────────────────────────────────────

    fun clear() {
        val bodiesToDestroy = mutableListOf<Body>()
        var body = world.bodyList
        while (body != null) {
            bodiesToDestroy.add(body)
            body = body.next
        }
        bodiesToDestroy.forEach { world.destroyBody(it) }

        obstacles.clear()
        enemies.clear()
        projectiles.clear()
        debris.clear()
        collisionQueue.clear()
        _score = 0

        // Recreate static environment
        createGround()
    }

    // ── Internal: static environment ────────────────────────────────────

    private fun createGround() {
        val bodyDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(WORLD_WIDTH / 2f, GROUND_HEIGHT / 2f)
        }
        val body = world.createBody(bodyDef)

        val shape = PolygonShape().apply {
            setAsBox(WORLD_WIDTH / 2f, GROUND_HEIGHT / 2f)
        }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            friction = 0.8f
            restitution = 0.05f
        }
        body.createFixture(fixtureDef)
    }

    // ── Internal: contact listener ──────────────────────────────────────

    private fun setupContactListener() {
        world.setContactListener(object : ContactListener {
            override fun beginContact(contact: Contact) {}
            override fun endContact(contact: Contact) {}
            override fun preSolve(contact: Contact, oldManifold: org.jbox2d.collision.Manifold) {}

            override fun postSolve(contact: Contact, impulse: ContactImpulse) {
                val maxImpulse = impulse.normalImpulses.maxOrNull() ?: 0f
                if (maxImpulse > 0.1f) {
                    collisionQueue.add(
                        CollisionEvent(
                            contact.fixtureA.body,
                            contact.fixtureB.body,
                            maxImpulse
                        )
                    )
                }
            }
        })
    }

    // ── Internal: collision processing ──────────────────────────────────

    private fun processCollisions() {
        for (event in collisionQueue) {
            val damage = (event.impulse * DAMAGE_MULTIPLIER).toInt()

            applyDamageToBody(event.bodyA, damage, event.bodyB)
            applyDamageToBody(event.bodyB, damage, event.bodyA)

            // Auto-trigger explosive on any collision
            projectiles.find { it.body === event.bodyA && it.ability is CatAbility.Explosive && !it.abilityUsed }
                ?.let { activateAbility(it, Vec2()) }
            projectiles.find { it.body === event.bodyB && it.ability is CatAbility.Explosive && !it.abilityUsed }
                ?.let { activateAbility(it, Vec2()) }

            // Charge penetration tracking
            handleChargePenetration(event.bodyA, event.bodyB)
            handleChargePenetration(event.bodyB, event.bodyA)
        }
        collisionQueue.clear()
    }

    private fun applyDamageToBody(body: Body, damage: Int, otherBody: Body) {
        obstacles.find { it.body === body }?.let { obstacle ->
            obstacle.hp -= damage
        }
        enemies.find { it.body === body }?.let { enemy ->
            enemy.hp -= damage
        }
    }

    private fun handleChargePenetration(projectileBody: Body, otherBody: Body) {
        val projectile = projectiles.find { it.body === projectileBody } ?: return
        val ability = projectile.ability as? CatAbility.Charge ?: return

        val hitsObstacle = obstacles.any { it.body === otherBody }
        val hitsEnemy = enemies.any { it.body === otherBody }
        if (hitsObstacle || hitsEnemy) {
            projectile.penetrateCount++
        }
    }

    // ── Internal: out-of-bounds removal ─────────────────────────────────

    private fun removeOutOfBounds() {
        val oobProjectiles = projectiles.filter { proj ->
            val pos = proj.body.position
            pos.x < -0.5f || pos.x > WORLD_WIDTH + 0.5f || pos.y < -1f
        }
        for (proj in oobProjectiles) {
            world.destroyBody(proj.body)
        }
        projectiles.removeAll(oobProjectiles.toSet())

        val oobObstacles = obstacles.filter { it.body.position.y < -1f }
        for (obstacle in oobObstacles) {
            world.destroyBody(obstacle.body)
        }
        obstacles.removeAll(oobObstacles.toSet())

        val oobEnemies = enemies.filter { it.body.position.y < -1f }
        for (enemy in oobEnemies) {
            world.destroyBody(enemy.body)
        }
        enemies.removeAll(oobEnemies.toSet())
    }

    // ── Internal: body removal and debris ───────────────────────────────

    private fun removeDestroyedBodies() {
        val destroyedObstacles = obstacles.filter { it.hp <= 0 }
        for (obstacle in destroyedObstacles) {
            _score += obstacle.material.scoreValue
            // TNT chain explosion
            if (obstacle.material == ObstacleMaterial.TNT) {
                applyBlast(obstacle.body.position, TNT_BLAST_RADIUS, TNT_BLAST_FORCE)
                pendingTntExplosions++
                totalTntExplosions++
            }
            spawnDebris(obstacle)
            world.destroyBody(obstacle.body)
        }
        obstacles.removeAll(destroyedObstacles.toSet())

        val destroyedEnemies = enemies.filter { it.hp <= 0 }
        for (enemy in destroyedEnemies) {
            _score += SCORE_ENEMY
            world.destroyBody(enemy.body)
        }
        enemies.removeAll(destroyedEnemies.toSet())

        // Destroy charge projectiles that exceeded max penetration
        val destroyedProjectiles = projectiles.filter { proj ->
            val ability = proj.ability
            ability is CatAbility.Charge && proj.penetrateCount >= ability.maxPenetrateCount
        }
        for (proj in destroyedProjectiles) {
            world.destroyBody(proj.body)
        }
        projectiles.removeAll(destroyedProjectiles.toSet())
    }

    private fun spawnDebris(obstacle: ObstacleBody) {
        val pos = obstacle.body.position
        for (i in 0 until DEBRIS_PER_OBSTACLE) {
            val angle = (2.0 * PI * i / DEBRIS_PER_OBSTACLE).toFloat()
            val speed = 1.5f + (Math.random().toFloat() * 2f)
            debris.add(
                DebrisParticle(
                    x = pos.x,
                    y = pos.y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed + 1f,
                    rotation = (Math.random() * 2 * PI).toFloat(),
                    rotSpeed = (-3f + Math.random().toFloat() * 6f),
                    life = 1.0f,
                    material = obstacle.material
                )
            )
        }
    }

    private fun updateDebris() {
        val iterator = debris.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * TIME_STEP
            p.y += p.vy * TIME_STEP
            p.vy += GRAVITY.y * TIME_STEP
            p.rotation += p.rotSpeed * TIME_STEP
            p.life -= TIME_STEP * 1.5f
            if (p.life <= 0f) {
                iterator.remove()
            }
        }
    }

    // ── Internal: blast mechanics ───────────────────────────────────────

    private fun applyBlast(center: Vec2, radius: Float, force: Float) {
        val aabb = AABB().apply {
            lowerBound.set(center.x - radius, center.y - radius)
            upperBound.set(center.x + radius, center.y + radius)
        }

        val affectedBodies = mutableSetOf<Body>()
        world.queryAABB({ fixture ->
            affectedBodies.add(fixture.body)
            true
        }, aabb)

        for (body in affectedBodies) {
            if (body.type != BodyType.DYNAMIC) continue

            val direction = Vec2(
                body.worldCenter.x - center.x,
                body.worldCenter.y - center.y
            )
            val distance = direction.length()
            if (distance < 0.01f || distance > radius) continue

            direction.mulLocal(1f / distance)
            val falloff = 1f - (distance / radius)
            val impulse = Vec2(
                direction.x * force * falloff,
                direction.y * force * falloff
            )
            body.applyLinearImpulse(impulse, body.worldCenter)

            // Apply blast damage
            val blastDamage = (force * falloff * 0.5f).toInt()
            obstacles.find { it.body === body }?.let { it.hp -= blastDamage }
            enemies.find { it.body === body }?.let { it.hp -= blastDamage }
        }
    }
}
