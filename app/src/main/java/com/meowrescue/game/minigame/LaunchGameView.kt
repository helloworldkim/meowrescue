package com.meowrescue.game.minigame

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.meowrescue.game.ui.Theme
import org.jbox2d.common.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class LaunchGameState { AIMING, FLYING, SETTLING, ABILITY_READY, STAGE_CLEAR, STAGE_FAIL }

class LaunchGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private const val TARGET_FPS = 60L
        private const val FRAME_MS = 1000L / TARGET_FPS

        private const val HUD_HEIGHT_DP = 56f

        private const val MAX_PULL_DISTANCE_DP = 120f
        private const val TRAJECTORY_DOT_COUNT = 30
        private const val CAMERA_LERP = 0.08f
        private const val SETTLE_VELOCITY_THRESHOLD = LaunchPhysicsWorld.SETTLED_VELOCITY_THRESHOLD
        private const val VIEW_WIDTH_METERS_PORTRAIT = 10f
        private const val VIEW_WIDTH_METERS_LANDSCAPE = 14f
        private const val PAN_THRESHOLD_PX = 15f

        private const val BG_SKY_TOP = Theme.LAUNCH_SKY_TOP
        private const val BG_SKY_BOTTOM = Theme.LAUNCH_SKY_BOTTOM
        private const val BG_GROUND = Theme.LAUNCH_GROUND
        private const val ENEMY_COLOR = Theme.LAUNCH_ENEMY
        private const val SLINGSHOT_COLOR = Theme.LAUNCH_SLINGSHOT
        private const val BAND_COLOR = Theme.LAUNCH_BAND

        private const val WORLD_WIDTH = LaunchPhysicsWorld.WORLD_WIDTH
        private const val GROUND_HEIGHT = LaunchPhysicsWorld.GROUND_HEIGHT
        private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
    }

    // ── Callbacks ────────────────────────────────────────────────────────────
    var onStageClear: ((catsUsed: Int, stars: Int) -> Unit)? = null
    var onNextStageClicked: (() -> Unit)? = null
    var onRetryClicked: (() -> Unit)? = null
    var onMenuClicked: (() -> Unit)? = null

    // ── Lock for thread-safe access between UI thread and render thread ──────
    private val lock = Any()

    // ── State ────────────────────────────────────────────────────────────────
    private var gameState = LaunchGameState.AIMING
    private var physicsWorld: LaunchPhysicsWorld? = null
    private var stageConfig: StageConfig? = null
    private var currentCatIndex = 0
    private var catsUsed = 0

    // ── Slingshot drag ───────────────────────────────────────────────────────
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragCurrentX = 0f
    private var dragCurrentY = 0f

    // ── Camera ───────────────────────────────────────────────────────────────
    private var cameraOffsetX = 0f
    private var cameraOffsetY = 0f

    // ── Manual camera panning ──────────────────────────────────────────────
    private var isPanning = false
    private var panLastX = 0f
    private var panLastY = 0f
    private var potentialTap = false
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var manualPanActive = false

    // ── Dynamic view width (adapts to orientation) ─────────────────────────
    private var viewWidthMeters = VIEW_WIDTH_METERS_PORTRAIT

    // ── Coordinate conversion ────────────────────────────────────────────────
    private var pixelsPerMeter = 1f
    private var worldOriginScreenX = 0f
    private var worldOriginScreenY = 0f
    private var maxPullDistancePx = 0f

    // ── Cat bitmaps ──────────────────────────────────────────────────────────
    private var catBitmaps: Map<Int, Bitmap> = emptyMap()

    // ── Victory / Fail overlay ───────────────────────────────────────────────
    private var victoryAlpha = 0f
    private var victoryStars = 0

    // ── Pending callback deferral ────────────────────────────────────────────
    private var pendingStageClear: Pair<Int, Int>? = null

    // ── Celebration particles ────────────────────────────────────────────────
    private data class CelebrationParticle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var radius: Float, var color: Int, var alpha: Float
    )
    private val celebrationParticles = mutableListOf<CelebrationParticle>()

    // ── Explosion effects ────────────────────────────────────────────────────
    private data class ExplosionEffect(
        var x: Float, var y: Float,
        var radius: Float, var maxRadius: Float,
        var alpha: Float, var startTime: Long
    )
    private val explosions = mutableListOf<ExplosionEffect>()

    // ── Difficulty label ──────────────────────────────────────────────────────
    private var difficultyLabel: String = ""

    // ── Settle timer ─────────────────────────────────────────────────────────
    private var settleFrameCount = 0
    private val SETTLE_FRAMES_REQUIRED = 15

    // ── Button rects ─────────────────────────────────────────────────────────
    private val pauseRect = RectF()
    private val nextStageRect = RectF()
    private val retryRect = RectF()
    private val menuRect = RectF()

    // ── Reusable Rect/RectF to avoid per-frame allocation ──────────────────
    private val tmpSrcRect = Rect()
    private val tmpDstRect = RectF()

    // ── Sky gradient cache ───────────────────────────────────────────────────
    private var skyGradient: LinearGradient? = null

    // ── Density ──────────────────────────────────────────────────────────────
    private val density = resources.displayMetrics.density

    // ── Paints (ALL class-level, no per-frame allocation) ────────────────────
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BG_GROUND
    }
    private val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_GRASS
    }
    private val slingshotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SLINGSHOT_COLOR; style = Paint.Style.STROKE
        strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BAND_COLOR; style = Paint.Style.STROKE
        strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val obstaclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val enemyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ENEMY_COLOR
    }
    private val enemyEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val enemyPupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_HUD_SHADOW
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val overlayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x00000000
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.INT_CORAL
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val explosionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_HUD_ORANGE
    }
    private val debrisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val catQueueBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_STAR_GOLD
    }
    private val starEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_ABILITY_NORMAL
    }
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val catFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.INT_CORAL
    }
    private val overlayTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val overlayInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_LIGHT_GRAY; textAlign = Paint.Align.CENTER
    }
    private val retryButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_BLUE_GRAY
    }
    private val menuButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_BLUE_GRAY
    }
    private val slingshotBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SLINGSHOT_COLOR; style = Paint.Style.FILL
    }
    private val abilityLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val abilityBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Render thread ────────────────────────────────────────────────────────
    private var renderThread: Thread? = null
    @Volatile private var running = false

    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun setStage(config: StageConfig, world: LaunchPhysicsWorld) {
        synchronized(lock) {
            stageConfig = config
            physicsWorld = world
            gameState = LaunchGameState.AIMING
            currentCatIndex = 0
            catsUsed = 0
            isDragging = false
            cameraOffsetX = 0f
            cameraOffsetY = 0f
            victoryAlpha = 0f
            victoryStars = 0
            settleFrameCount = 0
            celebrationParticles.clear()
            explosions.clear()
            pendingStageClear = null
            isPanning = false
            potentialTap = false
            manualPanActive = false
        }
        recalcLayout()
    }

    fun setCatBitmaps(bitmaps: Map<Int, Bitmap>) {
        synchronized(lock) {
            catBitmaps = bitmaps
        }
    }

    fun setDifficultyLabel(label: String) {
        synchronized(lock) {
            difficultyLabel = label
        }
    }

    fun pause() {
        running = false
    }

    fun resume() {
        if (!running && holder.surface.isValid) {
            startRenderThread()
        }
    }

    fun recycleBitmaps() {
        synchronized(lock) {
            catBitmaps = emptyMap()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SurfaceHolder.Callback
    // ─────────────────────────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) {
        recalcLayout()
        startRenderThread()
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        recalcLayout()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        stopRenderThread()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Coordinate conversion
    // ─────────────────────────────────────────────────────────────────────────

    private fun worldToScreenX(wx: Float): Float =
        (wx - cameraOffsetX) * pixelsPerMeter

    private fun worldToScreenY(wy: Float): Float =
        worldOriginScreenY - (wy - cameraOffsetY) * pixelsPerMeter

    private fun screenToWorldX(sx: Float): Float =
        sx / pixelsPerMeter + cameraOffsetX

    private fun screenToWorldY(sy: Float): Float =
        (worldOriginScreenY - sy) / pixelsPerMeter + cameraOffsetY

    private fun recalcLayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val hudHeightPx = HUD_HEIGHT_DP * density
        viewWidthMeters = if (w > h) VIEW_WIDTH_METERS_LANDSCAPE else VIEW_WIDTH_METERS_PORTRAIT
        pixelsPerMeter = w / viewWidthMeters
        maxPullDistancePx = MAX_PULL_DISTANCE_DP * density

        // Ground sits at GROUND_HEIGHT in world coords
        // worldOriginScreenY = where world Y=0 maps to on screen
        // We want ground top (y=GROUND_HEIGHT) to be about 75% down the view
        val groundScreenY = h * 0.75f
        worldOriginScreenY = groundScreenY + GROUND_HEIGHT * pixelsPerMeter

        worldOriginScreenX = 0f

        // Cache sky gradient and assign to paint once
        skyGradient = LinearGradient(
            0f, hudHeightPx, 0f, groundScreenY,
            BG_SKY_TOP, BG_SKY_BOTTOM,
            Shader.TileMode.CLAMP
        )
        skyPaint.shader = skyGradient
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Touch handling
    // ─────────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        val callback: (() -> Unit)? = synchronized(lock) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> handleDown(x, y)
                MotionEvent.ACTION_MOVE -> { handleMove(x, y); null }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(x, y)
                else -> null
            }
        }
        callback?.invoke()
        return true
    }

    /** Returns a callback to invoke outside synchronized(lock), or null. */
    private fun handleDown(x: Float, y: Float): (() -> Unit)? {
        // HUD: pause button
        if (pauseRect.contains(x, y)) {
            val cb = onMenuClicked
            return { cb?.invoke() }
        }

        // Overlay buttons
        if (gameState == LaunchGameState.STAGE_CLEAR) {
            if (nextStageRect.contains(x, y)) {
                val cb = onNextStageClicked
                return { cb?.invoke() }
            }
            if (retryRect.contains(x, y)) {
                val cb = onRetryClicked
                return { cb?.invoke() }
            }
            if (menuRect.contains(x, y)) {
                val cb = onMenuClicked
                return { cb?.invoke() }
            }
            return null
        }
        if (gameState == LaunchGameState.STAGE_FAIL) {
            if (retryRect.contains(x, y)) {
                val cb = onRetryClicked
                return { cb?.invoke() }
            }
            if (menuRect.contains(x, y)) {
                val cb = onMenuClicked
                return { cb?.invoke() }
            }
            return null
        }

        // Ability activation / camera pan during flight
        if (gameState == LaunchGameState.FLYING || gameState == LaunchGameState.ABILITY_READY) {
            potentialTap = true
            tapDownX = x
            tapDownY = y
            panLastX = x
            panLastY = y
            return null
        }

        // Slingshot drag or camera pan in AIMING state
        if (gameState == LaunchGameState.AIMING) {
            val anchorScreenX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
            val anchorScreenY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
            val touchRadius = 80f * density
            val dx = x - anchorScreenX
            val dy = y - anchorScreenY
            if (sqrt(dx * dx + dy * dy) <= touchRadius) {
                isDragging = true
                dragStartX = x
                dragStartY = y
                dragCurrentX = x
                dragCurrentY = y
            } else {
                // Start camera pan
                isPanning = true
                panLastX = x
                panLastY = y
            }
        }
        return null
    }

    private fun handleMove(x: Float, y: Float) {
        // Slingshot dragging
        if (isDragging) {
            dragCurrentX = x
            dragCurrentY = y

            // Clamp pull distance
            val anchorScreenX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
            val anchorScreenY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
            val dx = dragCurrentX - anchorScreenX
            val dy = dragCurrentY - anchorScreenY
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > maxPullDistancePx) {
                val scale = maxPullDistancePx / dist
                dragCurrentX = anchorScreenX + dx * scale
                dragCurrentY = anchorScreenY + dy * scale
            }
            return
        }

        // Convert potential tap to pan if moved enough (FLYING/ABILITY_READY)
        if (potentialTap) {
            val dx = x - tapDownX
            val dy = y - tapDownY
            if (sqrt(dx * dx + dy * dy) > PAN_THRESHOLD_PX * density) {
                potentialTap = false
                isPanning = true
                manualPanActive = true
                panLastX = x
                panLastY = y
            }
            return
        }

        // Camera panning
        if (isPanning) {
            val dx = panLastX - x
            val dy = panLastY - y
            cameraOffsetX += dx / pixelsPerMeter
            cameraOffsetY -= dy / pixelsPerMeter  // Screen Y is inverted
            clampCamera()
            panLastX = x
            panLastY = y
        }
    }

    // x, y 미사용: handleDown/handleMove와 인터페이스 대칭 유지
    @Suppress("UNUSED_PARAMETER")
    private fun handleUp(x: Float, y: Float): (() -> Unit)? {
        // End camera panning
        if (isPanning) {
            isPanning = false
            return null
        }

        // Ability activation: was a short tap during flight (not a pan)
        if (potentialTap) {
            potentialTap = false
            val pw = physicsWorld ?: return null
            val projectiles = pw.getProjectiles()
            val activeProjectile = projectiles.lastOrNull()
            if (activeProjectile != null && !activeProjectile.abilityUsed) {
                val tapWorldPos = Vec2(screenToWorldX(tapDownX), screenToWorldY(tapDownY))
                val prePos = Vec2(activeProjectile.body.position.x, activeProjectile.body.position.y)
                pw.activateAbility(activeProjectile, tapWorldPos)
                if (activeProjectile.ability is CatAbility.Explosive) {
                    explosions.add(ExplosionEffect(
                        x = prePos.x, y = prePos.y,
                        radius = 0f,
                        maxRadius = (activeProjectile.ability as CatAbility.Explosive).blastRadiusMeters,
                        alpha = 1f,
                        startTime = System.currentTimeMillis()
                    ))
                }
            }
            return null
        }

        // Slingshot release
        if (!isDragging) return null
        isDragging = false

        val pw = physicsWorld ?: return null
        val config = stageConfig ?: return null
        if (currentCatIndex >= config.catIds.size) return null

        val anchorScreenX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val anchorScreenY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)

        val pullScreenX = anchorScreenX - dragCurrentX
        val pullScreenY = anchorScreenY - dragCurrentY

        val pullDist = sqrt(pullScreenX * pullScreenX + pullScreenY * pullScreenY)
        if (pullDist < 10f * density) return null

        val pullWorldX = pullScreenX / pixelsPerMeter
        val pullWorldY = -pullScreenY / pixelsPerMeter

        val pullVector = Vec2(pullWorldX, pullWorldY)

        val catId = config.catIds[currentCatIndex]
        val ability = CatAbility.forCatId(catId)
        pw.launchProjectile(catId, ability, pullVector)

        currentCatIndex++
        catsUsed++
        settleFrameCount = 0
        manualPanActive = false

        val hasAbility = ability !is CatAbility.Normal && ability !is CatAbility.Charge
        gameState = if (hasAbility) LaunchGameState.ABILITY_READY else LaunchGameState.FLYING

        return null
    }

    private fun clampCamera() {
        val maxCamX = (WORLD_WIDTH - viewWidthMeters).coerceAtLeast(0f)
        cameraOffsetX = cameraOffsetX.coerceIn(0f, maxCamX)
        cameraOffsetY = cameraOffsetY.coerceIn(0f, 5f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render loop
    // ─────────────────────────────────────────────────────────────────────────

    private fun startRenderThread() {
        running = true
        renderThread = Thread {
            while (running) {
                val start = System.currentTimeMillis()
                drawFrame()
                val elapsed = System.currentTimeMillis() - start
                val sleep = FRAME_MS - elapsed
                if (sleep > 0) Thread.sleep(sleep)
            }
        }.also { it.name = "LaunchRenderThread"; it.start() }
    }

    private fun stopRenderThread() {
        running = false
        renderThread?.join(500)
        renderThread = null
    }

    private fun drawFrame() {
        val h = holder
        if (!h.surface.isValid) return
        val canvas = h.lockCanvas() ?: return
        var stageClearData: Pair<Int, Int>? = null
        try {
            synchronized(lock) {
                pendingStageClear = null
                update()
                render(canvas)
                stageClearData = pendingStageClear
            }
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
        // Invoke callbacks outside lock
        stageClearData?.let { (cats, stars) ->
            onStageClear?.invoke(cats, stars)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update (called inside synchronized(lock))
    // ─────────────────────────────────────────────────────────────────────────

    private fun update() {
        val pw = physicsWorld ?: return
        val config = stageConfig ?: return

        when (gameState) {
            LaunchGameState.FLYING, LaunchGameState.ABILITY_READY -> {
                pw.step()
                updateCamera(pw)
                updateExplosions()

                // Check if active projectile stopped or out of bounds
                val projectiles = pw.getProjectiles()
                if (projectiles.isNotEmpty()) {
                    val active = projectiles.last()
                    val vel = active.body.linearVelocity
                    val pos = active.body.position
                    val speed = vel.length()
                    val outOfBounds = pos.x < -0.5f || pos.x > WORLD_WIDTH + 0.5f || pos.y < -1f
                    if (speed < SETTLE_VELOCITY_THRESHOLD || outOfBounds) {
                        gameState = LaunchGameState.SETTLING
                        settleFrameCount = 0
                    }
                } else {
                    // All projectiles gone (e.g., explosive destroyed them)
                    gameState = LaunchGameState.SETTLING
                    settleFrameCount = 0
                }

                // Auto-transition from ABILITY_READY to FLYING after ability used
                if (gameState == LaunchGameState.ABILITY_READY) {
                    val last = projectiles.lastOrNull()
                    if (last == null || last.abilityUsed) {
                        gameState = LaunchGameState.FLYING
                    }
                }
            }

            LaunchGameState.SETTLING -> {
                pw.step()
                updateCamera(pw)
                updateExplosions()

                if (pw.isSettled()) {
                    settleFrameCount++
                } else {
                    settleFrameCount = 0
                }

                if (settleFrameCount >= SETTLE_FRAMES_REQUIRED) {
                    if (pw.allEnemiesDestroyed()) {
                        gameState = LaunchGameState.STAGE_CLEAR
                        val thresholds = config.starThresholds
                        victoryStars = when {
                            catsUsed <= thresholds.threeStar -> 3
                            catsUsed <= thresholds.twoStar -> 2
                            else -> 1
                        }
                        victoryAlpha = 0f
                        spawnCelebrationParticles()
                        pendingStageClear = Pair(catsUsed, victoryStars)
                    } else if (currentCatIndex < config.catIds.size) {
                        // More cats available
                        gameState = LaunchGameState.AIMING
                        manualPanActive = false
                    } else {
                        // No more cats, not all enemies destroyed
                        gameState = LaunchGameState.STAGE_FAIL
                        victoryAlpha = 0f
                    }
                }
            }

            LaunchGameState.STAGE_CLEAR -> {
                victoryAlpha = min(1f, victoryAlpha + 0.04f)
                updateCelebrationParticles()
            }

            LaunchGameState.STAGE_FAIL -> {
                victoryAlpha = min(1f, victoryAlpha + 0.04f)
            }

            LaunchGameState.AIMING -> {
                // When not manually panning, smoothly return camera to slingshot
                if (!isPanning) {
                    manualPanActive = false
                    val targetX = 0f
                    val targetY = 0f
                    cameraOffsetX += (targetX - cameraOffsetX) * CAMERA_LERP
                    cameraOffsetY += (targetY - cameraOffsetY) * CAMERA_LERP
                    clampCamera()
                }
            }
        }
    }

    private fun updateCamera(pw: LaunchPhysicsWorld) {
        // Skip auto-tracking when user is manually panning
        if (manualPanActive || isPanning) return

        val projectiles = pw.getProjectiles()
        if (projectiles.isNotEmpty()) {
            val active = projectiles.last()
            val pos = active.body.position
            // Target camera so projectile is roughly 1/3 from left
            val targetX = max(0f, pos.x - viewWidthMeters * 0.33f)
            val targetY = max(0f, pos.y - 3f)
            cameraOffsetX += (targetX - cameraOffsetX) * CAMERA_LERP
            cameraOffsetY += (targetY - cameraOffsetY) * CAMERA_LERP
            clampCamera()
        }
    }

    private fun updateExplosions() {
        val now = System.currentTimeMillis()
        val iterator = explosions.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            val elapsed = now - e.startTime
            val progress = elapsed / 400f // 400ms duration
            if (progress >= 1f) {
                iterator.remove()
            } else {
                e.radius = e.maxRadius * progress
                e.alpha = 1f - progress
            }
        }
    }

    private fun updateCelebrationParticles() {
        val iterator = celebrationParticles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.15f // gravity
            p.alpha = max(0f, p.alpha - 0.012f)
            if (p.alpha <= 0f) {
                iterator.remove()
            }
        }
    }

    private fun spawnCelebrationParticles() {
        val cx = width / 2f
        val cy = height / 3f
        val colors = Theme.LAUNCH_CONFETTI
        for (i in 0 until 40) {
            val angle = (Math.random() * Math.PI * 2).toFloat()
            val speed = (2f + Math.random().toFloat() * 4f)
            celebrationParticles.add(CelebrationParticle(
                x = cx, y = cy,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 3f,
                radius = 4f + Math.random().toFloat() * 6f,
                color = colors[(Math.random() * colors.size).toInt()],
                alpha = 1f
            ))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render (called inside synchronized(lock))
    // ─────────────────────────────────────────────────────────────────────────

    private fun render(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawSky(canvas)
        drawGround(canvas)

        val pw = physicsWorld ?: return

        drawObstacles(canvas, pw)
        drawEnemies(canvas, pw)
        drawSlingshot(canvas)

        if (isDragging) {
            drawBand(canvas)
            drawTrajectoryPreview(canvas)
        }

        drawCurrentCatOnSlingshot(canvas)
        drawProjectiles(canvas, pw)
        drawDebris(canvas, pw)
        drawExplosions(canvas)
        drawHud(canvas)
        drawCatQueue(canvas)
        drawCelebration(canvas)

        when (gameState) {
            LaunchGameState.STAGE_CLEAR -> drawVictoryOverlay(canvas)
            LaunchGameState.STAGE_FAIL -> drawFailOverlay(canvas)
            else -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw methods
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawSky(canvas: Canvas) {
        if (skyGradient != null) {
            canvas.drawRect(0f, 0f, width.toFloat(), worldToScreenY(GROUND_HEIGHT), skyPaint)
        } else {
            canvas.drawColor(BG_SKY_TOP)
        }
    }

    private fun drawGround(canvas: Canvas) {
        val groundTop = worldToScreenY(GROUND_HEIGHT)
        val h = height.toFloat()

        // Brown ground
        canvas.drawRect(0f, groundTop, width.toFloat(), h, groundPaint)

        // Green grass strip on top
        val grassHeight = 4f * density
        canvas.drawRect(0f, groundTop, width.toFloat(), groundTop + grassHeight, grassPaint)
    }

    private fun drawSlingshot(canvas: Canvas) {
        val baseX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val baseY = worldToScreenY(GROUND_HEIGHT)
        val topY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
        val forkSpread = 12f * density
        val forkHeight = 15f * density

        slingshotPaint.strokeWidth = 6f * density

        // Base vertical line
        canvas.drawLine(baseX, baseY, baseX, topY, slingshotPaint)

        // Left fork
        canvas.drawLine(baseX, topY, baseX - forkSpread, topY - forkHeight, slingshotPaint)

        // Right fork
        canvas.drawLine(baseX, topY, baseX + forkSpread, topY - forkHeight, slingshotPaint)

        // Small base rectangle
        val baseW = 10f * density
        val baseH = 5f * density
        canvas.drawRect(
            baseX - baseW / 2f, baseY - baseH,
            baseX + baseW / 2f, baseY,
            slingshotBasePaint
        )
    }

    private fun drawBand(canvas: Canvas) {
        val baseX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val topY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
        val forkSpread = 12f * density
        val forkHeight = 15f * density

        val leftTipX = baseX - forkSpread
        val leftTipY = topY - forkHeight
        val rightTipX = baseX + forkSpread
        val rightTipY = topY - forkHeight

        bandPaint.strokeWidth = 4f * density

        // Left band from fork tip to drag point
        canvas.drawLine(leftTipX, leftTipY, dragCurrentX, dragCurrentY, bandPaint)

        // Right band from fork tip to drag point
        canvas.drawLine(rightTipX, rightTipY, dragCurrentX, dragCurrentY, bandPaint)

        // Draw cat at drag point
        val config = stageConfig ?: return
        if (currentCatIndex < config.catIds.size) {
            val catId = config.catIds[currentCatIndex]
            val catRadius = 12f * density
            val bm = catBitmaps[catId]
            if (bm != null) {
                tmpSrcRect.set(0, 0, bm.width, bm.height)
                tmpDstRect.set(dragCurrentX - catRadius, dragCurrentY - catRadius,
                               dragCurrentX + catRadius, dragCurrentY + catRadius)
                canvas.drawBitmap(bm, tmpSrcRect, tmpDstRect, null)
            } else {
                canvas.drawCircle(dragCurrentX, dragCurrentY, catRadius, catFallbackPaint)
            }
        }
    }

    private fun drawTrajectoryPreview(canvas: Canvas) {
        val anchorScreenX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val anchorScreenY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)

        // Pull vector in screen space
        val pullScreenX = anchorScreenX - dragCurrentX
        val pullScreenY = anchorScreenY - dragCurrentY

        // Convert to world velocity estimate
        var vx = pullScreenX / pixelsPerMeter * LaunchPhysicsWorld.POWER_FACTOR
        var vy = -pullScreenY / pixelsPerMeter * LaunchPhysicsWorld.POWER_FACTOR

        // Clamp to MAX_LAUNCH_SPEED (match actual physics)
        val previewSpeed = sqrt(vx * vx + vy * vy)
        if (previewSpeed > LaunchPhysicsWorld.MAX_LAUNCH_SPEED) {
            val scale = LaunchPhysicsWorld.MAX_LAUNCH_SPEED / previewSpeed
            vx *= scale
            vy *= scale
        }

        val gx = LaunchPhysicsWorld.GRAVITY.x
        val gy = LaunchPhysicsWorld.GRAVITY.y
        val dt = LaunchPhysicsWorld.TIME_STEP * 3f // Larger steps for preview

        var wx = LaunchPhysicsWorld.SLINGSHOT_X
        var wy = LaunchPhysicsWorld.SLINGSHOT_Y
        var cvx = vx
        var cvy = vy

        for (i in 0 until TRAJECTORY_DOT_COUNT) {
            wx += cvx * dt
            wy += cvy * dt
            cvx += gx * dt
            cvy += gy * dt

            val sx = worldToScreenX(wx)
            val sy = worldToScreenY(wy)

            val alpha = (1f - i.toFloat() / TRAJECTORY_DOT_COUNT) * 200
            trajectoryPaint.alpha = alpha.toInt()
            val dotRadius = (3f - i.toFloat() / TRAJECTORY_DOT_COUNT * 2f) * density
            canvas.drawCircle(sx, sy, dotRadius, trajectoryPaint)
        }
        trajectoryPaint.alpha = 255
    }

    private fun drawObstacles(canvas: Canvas, pw: LaunchPhysicsWorld) {
        for (obstacle in pw.getObstacles()) {
            val pos = obstacle.body.position
            val angle = obstacle.body.angle
            val sx = worldToScreenX(pos.x)
            val sy = worldToScreenY(pos.y)
            val halfW = obstacle.widthM / 2f * pixelsPerMeter
            val halfH = obstacle.heightM / 2f * pixelsPerMeter
            val cornerRadius = 3f * density

            canvas.save()
            canvas.translate(sx, sy)
            canvas.rotate(-angle * RAD_TO_DEG)

            obstaclePaint.color = obstacle.material.color
            canvas.drawRoundRect(
                -halfW, -halfH, halfW, halfH,
                cornerRadius, cornerRadius, obstaclePaint
            )

            // Draw cracks if HP < 50%
            val hpRatio = obstacle.hp.toFloat() / obstacle.material.maxHp
            if (hpRatio < 0.5f) {
                val crackCount = if (hpRatio < 0.25f) 3 else 1
                for (c in 0 until crackCount) {
                    val cx1 = -halfW * 0.3f + c * halfW * 0.2f
                    val cy1 = -halfH * 0.5f
                    val cx2 = halfW * 0.2f + c * halfW * 0.1f
                    val cy2 = halfH * 0.5f
                    canvas.drawLine(cx1, cy1, cx2, cy2, crackPaint)
                }
            }

            canvas.restore()
        }
    }

    private fun drawEnemies(canvas: Canvas, pw: LaunchPhysicsWorld) {
        for (enemy in pw.getEnemies()) {
            val pos = enemy.body.position
            val sx = worldToScreenX(pos.x)
            val sy = worldToScreenY(pos.y)
            val radiusPx = enemy.radiusM * pixelsPerMeter

            // Green circle body
            canvas.drawCircle(sx, sy, radiusPx, enemyPaint)

            // White eyes
            val eyeOffsetX = radiusPx * 0.3f
            val eyeOffsetY = radiusPx * 0.2f
            val eyeRadius = radiusPx * 0.2f
            val pupilRadius = radiusPx * 0.1f

            canvas.drawCircle(sx - eyeOffsetX, sy - eyeOffsetY, eyeRadius, enemyEyePaint)
            canvas.drawCircle(sx + eyeOffsetX, sy - eyeOffsetY, eyeRadius, enemyEyePaint)

            // Black pupils
            canvas.drawCircle(sx - eyeOffsetX, sy - eyeOffsetY, pupilRadius, enemyPupilPaint)
            canvas.drawCircle(sx + eyeOffsetX, sy - eyeOffsetY, pupilRadius, enemyPupilPaint)
        }
    }

    private fun drawProjectiles(canvas: Canvas, pw: LaunchPhysicsWorld) {
        for (proj in pw.getProjectiles()) {
            val pos = proj.body.position
            val angle = proj.body.angle
            val sx = worldToScreenX(pos.x)
            val sy = worldToScreenY(pos.y)
            val radiusPx = proj.radiusM * pixelsPerMeter

            val bm = catBitmaps[proj.catId]
            if (bm != null) {
                canvas.save()
                canvas.translate(sx, sy)
                canvas.rotate(-angle * RAD_TO_DEG)
                tmpSrcRect.set(0, 0, bm.width, bm.height)
                tmpDstRect.set(-radiusPx, -radiusPx, radiusPx, radiusPx)
                canvas.drawBitmap(bm, tmpSrcRect, tmpDstRect, null)
                canvas.restore()
            } else {
                canvas.drawCircle(sx, sy, radiusPx, catFallbackPaint)
            }
        }
    }

    private fun drawDebris(canvas: Canvas, pw: LaunchPhysicsWorld) {
        for (d in pw.getDebris()) {
            val sx = worldToScreenX(d.x)
            val sy = worldToScreenY(d.y)
            val size = 4f * density

            debrisPaint.color = d.material.color
            debrisPaint.alpha = (d.life * 255).toInt().coerceIn(0, 255)

            canvas.save()
            canvas.translate(sx, sy)
            canvas.rotate(d.rotation * RAD_TO_DEG)
            canvas.drawRect(-size / 2f, -size / 2f, size / 2f, size / 2f, debrisPaint)
            canvas.restore()
        }
    }

    private fun drawExplosions(canvas: Canvas) {
        for (e in explosions) {
            val sx = worldToScreenX(e.x)
            val sy = worldToScreenY(e.y)
            val radiusPx = e.radius * pixelsPerMeter

            explosionPaint.alpha = (e.alpha * 200).toInt().coerceIn(0, 255)
            canvas.drawCircle(sx, sy, radiusPx, explosionPaint)
        }
    }

    private fun abilityColor(ability: CatAbility): Int = when (ability) {
        is CatAbility.Normal -> Theme.LAUNCH_ABILITY_NORMAL
        is CatAbility.Redirect -> Theme.LAUNCH_ABILITY_REDIRECT
        is CatAbility.Split -> Theme.LAUNCH_ABILITY_SPLIT
        is CatAbility.Explosive -> Theme.LAUNCH_ABILITY_EXPLOSIVE
        is CatAbility.Charge -> Theme.LAUNCH_ABILITY_CHARGE
    }

    private fun drawHud(canvas: Canvas) {
        val w = width.toFloat()
        val hudH = HUD_HEIGHT_DP * density

        // Background bar
        canvas.drawRect(0f, 0f, w, hudH, hudBgPaint)

        hudTextPaint.textSize = 16f * density
        val textY = hudH / 2f + hudTextPaint.textSize / 3f

        // Stage text + difficulty label
        val config = stageConfig
        val diffLabel = if (difficultyLabel.isNotEmpty()) " [$difficultyLabel]" else ""
        val stageText = if (config != null) "Stage ${config.stageId}$diffLabel" else "Stage"
        canvas.drawText(stageText, w / 2f, textY, hudTextPaint)

        // Cats remaining (left side)
        val remaining = if (config != null) config.catIds.size - currentCatIndex else 0
        val catsText = "x$remaining"
        hudTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(catsText, 16f * density + 20f * density, textY, hudTextPaint)
        hudTextPaint.textAlign = Paint.Align.CENTER

        // Cat icon (small circle)
        catFallbackPaint.alpha = 255
        canvas.drawCircle(16f * density + 8f * density, hudH / 2f, 8f * density, catFallbackPaint)

        // Current cat ability label (below stage text)
        if (config != null && currentCatIndex < config.catIds.size && gameState == LaunchGameState.AIMING) {
            val catId = config.catIds[currentCatIndex]
            val ability = CatAbility.forCatId(catId)
            val abilityName = ability.displayName
            val abColor = abilityColor(ability)

            abilityLabelPaint.textSize = 11f * density
            abilityBgPaint.color = abColor

            val tagW = abilityLabelPaint.measureText(abilityName) + 12f * density
            val tagH = 16f * density
            val tagX = w / 2f - tagW / 2f
            val tagY = textY + 6f * density

            canvas.drawRoundRect(
                RectF(tagX, tagY, tagX + tagW, tagY + tagH),
                tagH / 2f, tagH / 2f, abilityBgPaint
            )
            abilityLabelPaint.color = Color.WHITE
            canvas.drawText(abilityName, w / 2f, tagY + tagH - 4f * density, abilityLabelPaint)
        }

        // Pause button (right side)
        val pauseSize = 32f * density
        val pauseRight = w - 12f * density
        val pauseLeft = pauseRight - pauseSize
        val pauseTop = (hudH - pauseSize) / 2f
        val pauseBottom = pauseTop + pauseSize
        pauseRect.set(pauseLeft, pauseTop, pauseRight, pauseBottom)

        // Draw pause icon (two vertical bars)
        val barW = 4f * density
        val barH = 16f * density
        val barGap = 4f * density
        val barCX = (pauseLeft + pauseRight) / 2f
        val barCY = (pauseTop + pauseBottom) / 2f
        hudTextPaint.style = Paint.Style.FILL
        canvas.drawRect(
            barCX - barGap - barW, barCY - barH / 2f,
            barCX - barGap, barCY + barH / 2f,
            hudTextPaint
        )
        canvas.drawRect(
            barCX + barGap, barCY - barH / 2f,
            barCX + barGap + barW, barCY + barH / 2f,
            hudTextPaint
        )
    }

    /** AIMING 상태에서 드래그 전에도 새총 위에 현재 고양이 표시 */
    private fun drawCurrentCatOnSlingshot(canvas: Canvas) {
        if (gameState != LaunchGameState.AIMING || isDragging) return
        val config = stageConfig ?: return
        if (currentCatIndex >= config.catIds.size) return

        val catId = config.catIds[currentCatIndex]
        val sx = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val sy = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
        val catRadius = 12f * density

        val bm = catBitmaps[catId]
        if (bm != null) {
            tmpSrcRect.set(0, 0, bm.width, bm.height)
            tmpDstRect.set(sx - catRadius, sy - catRadius, sx + catRadius, sy + catRadius)
            canvas.drawBitmap(bm, tmpSrcRect, tmpDstRect, null)
        } else {
            canvas.drawCircle(sx, sy, catRadius, catFallbackPaint)
        }
    }

    private fun drawCatQueue(canvas: Canvas) {
        val config = stageConfig ?: return
        if (currentCatIndex >= config.catIds.size) return

        // 현재 고양이 + 대기 고양이 최대 4마리 표시
        val remaining = config.catIds.size - currentCatIndex
        val showCount = min(remaining, 5)
        if (showCount <= 0) return

        val baseX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val baseY = worldToScreenY(GROUND_HEIGHT) + 8f * density
        val currentSize = 26f * density  // 현재 고양이는 더 크게
        val queueSize = 20f * density
        val spacing = 4f * density

        // 전체 너비 계산: 현재(큰) + 나머지(작은)
        val totalW = currentSize + spacing +
            (showCount - 1).coerceAtLeast(0) * (queueSize + spacing) + 8f * density
        val bgRect = RectF(
            baseX - totalW / 2f, baseY,
            baseX + totalW / 2f, baseY + currentSize + 8f * density
        )
        canvas.drawRoundRect(bgRect, 4f * density, 4f * density, catQueueBgPaint)

        var drawX = bgRect.left + 4f * density

        for (i in 0 until showCount) {
            val catIdx = currentCatIndex + i
            if (catIdx >= config.catIds.size) break
            val catId = config.catIds[catIdx]
            val isCurrent = (i == 0)
            val size = if (isCurrent) currentSize else queueSize

            val cx = drawX + size / 2f
            val cy = baseY + 4f * density + currentSize / 2f

            val bm = catBitmaps[catId]
            if (bm != null) {
                tmpSrcRect.set(0, 0, bm.width, bm.height)
                tmpDstRect.set(cx - size / 2f, cy - size / 2f,
                               cx + size / 2f, cy + size / 2f)
                canvas.drawBitmap(bm, tmpSrcRect, tmpDstRect, null)
            } else {
                catFallbackPaint.alpha = if (isCurrent) 255 else 180
                canvas.drawCircle(cx, cy, size / 2f - 2f, catFallbackPaint)
                catFallbackPaint.alpha = 255
            }

            // 현재 고양이: 하이라이트 테두리
            if (isCurrent) {
                abilityBgPaint.color = Theme.INT_CORAL
                abilityBgPaint.style = Paint.Style.STROKE
                abilityBgPaint.strokeWidth = 2f * density
                canvas.drawCircle(cx, cy, size / 2f, abilityBgPaint)
                abilityBgPaint.style = Paint.Style.FILL
            }

            // 능력 색상 도트
            val ability = CatAbility.forCatId(catId)
            abilityBgPaint.color = abilityColor(ability)
            abilityBgPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy + currentSize / 2f + 3f * density, 3f * density, abilityBgPaint)

            drawX += size + spacing
        }
    }

    private fun drawCelebration(canvas: Canvas) {
        for (p in celebrationParticles) {
            particlePaint.color = p.color
            particlePaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
        }
    }

    private fun drawVictoryOverlay(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val alpha = victoryAlpha
        val isLandscape = w > h

        // Semi-transparent background
        overlayBgPaint.color = Color.argb((alpha * 180).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, overlayBgPaint)

        if (alpha < 0.3f) return

        // 가로모드: 패널을 넓고 높게, 세로모드: 기존 비율
        val panelW = if (isLandscape) w * 0.55f else w * 0.8f
        val panelH = if (isLandscape) h * 0.82f else h * 0.45f
        val panelLeft = (w - panelW) / 2f
        val panelTop = (h - panelH) / 2f
        val panelRight = panelLeft + panelW
        val panelBottom = panelTop + panelH
        val cornerR = 16f * density

        // Panel background
        buttonPaint.color = Theme.INT_BG_CREAM
        buttonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom, cornerR, cornerR, buttonPaint)

        // 패널 내부를 비례 배치 (고정 dp 대신 panelH 비율)
        val titleY = panelTop + panelH * 0.13f
        val starCenterY = panelTop + panelH * 0.30f
        val infoY = panelTop + panelH * 0.44f
        val btnStartY = panelTop + panelH * 0.55f

        // Title
        val titleSize = min(28f * density, panelH * 0.10f)
        overlayTitlePaint.textSize = titleSize
        overlayTitlePaint.color = Theme.INT_CORAL
        overlayTitlePaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Stage Clear!", w / 2f, titleY, overlayTitlePaint)

        // Stars
        val starSize = min(28f * density, panelH * 0.10f)
        val starSpacing = starSize * 1.5f
        val starStartX = w / 2f - starSpacing

        for (i in 0 until 3) {
            val sx = starStartX + i * starSpacing
            if (i < victoryStars) {
                starPaint.alpha = (alpha * 255).toInt()
                canvas.drawCircle(sx, starCenterY, starSize / 2f, starPaint)
                overlayTitlePaint.textSize = starSize * 0.7f
                overlayTitlePaint.color = Theme.INT_BG_CREAM
                overlayTitlePaint.alpha = (alpha * 255).toInt()
                canvas.drawText("\u2605", sx, starCenterY + starSize * 0.2f, overlayTitlePaint)
            } else {
                starEmptyPaint.alpha = (alpha * 255).toInt()
                canvas.drawCircle(sx, starCenterY, starSize / 2f, starEmptyPaint)
                overlayTitlePaint.textSize = starSize * 0.7f
                overlayTitlePaint.color = Color.WHITE
                overlayTitlePaint.alpha = (alpha * 150).toInt()
                canvas.drawText("\u2605", sx, starCenterY + starSize * 0.2f, overlayTitlePaint)
            }
        }

        // Cats used info
        val infoSize = min(16f * density, panelH * 0.06f)
        overlayInfoPaint.textSize = infoSize
        overlayInfoPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Cats used: $catsUsed", w / 2f, infoY, overlayInfoPaint)

        // Buttons — 비례 배치
        val btnW = panelW * 0.7f
        val btnH = min(44f * density, panelH * 0.12f)
        val btnGap = min(10f * density, panelH * 0.03f)
        val btnLeft = (w - btnW) / 2f
        val btnRight = btnLeft + btnW
        val btnCorner = btnH / 2f
        val btnTextSize = min(16f * density, btnH * 0.45f)

        // Next Stage button
        val nextTop = btnStartY
        val nextBottom = nextTop + btnH
        nextStageRect.set(btnLeft, nextTop, btnRight, nextBottom)
        buttonPaint.color = Theme.INT_CORAL
        buttonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(nextStageRect, btnCorner, btnCorner, buttonPaint)
        buttonTextPaint.textSize = btnTextSize
        buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Next Stage", w / 2f, nextTop + btnH / 2f + btnTextSize / 3f, buttonTextPaint)

        // Retry button
        val retryTop = nextBottom + btnGap
        val retryBottom = retryTop + btnH
        retryRect.set(btnLeft, retryTop, btnRight, retryBottom)
        retryButtonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(retryRect, btnCorner, btnCorner, retryButtonPaint)
        buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Retry", w / 2f, retryTop + btnH / 2f + btnTextSize / 3f, buttonTextPaint)

        // Menu button
        val menuTop = retryBottom + btnGap
        val menuBottom = menuTop + btnH
        menuRect.set(btnLeft, menuTop, btnRight, menuBottom)
        menuButtonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(menuRect, btnCorner, btnCorner, menuButtonPaint)
        buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Menu", w / 2f, menuTop + btnH / 2f + btnTextSize / 3f, buttonTextPaint)

        // Reset alpha on reused paints
        buttonPaint.alpha = 255
        overlayTitlePaint.alpha = 255
        buttonTextPaint.alpha = 255
    }

    private fun drawFailOverlay(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val alpha = victoryAlpha
        val isLandscape = w > h

        // Semi-transparent background
        overlayBgPaint.color = Color.argb((alpha * 180).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, overlayBgPaint)

        if (alpha < 0.3f) return

        // 가로모드: 패널 높이 확대, 세로모드: 기존 비율
        val panelW = if (isLandscape) w * 0.55f else w * 0.8f
        val panelH = if (isLandscape) h * 0.65f else h * 0.35f
        val panelLeft = (w - panelW) / 2f
        val panelTop = (h - panelH) / 2f
        val panelRight = panelLeft + panelW
        val panelBottom = panelTop + panelH
        val cornerR = 16f * density

        // Panel background
        buttonPaint.color = Theme.INT_BG_CREAM
        buttonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom, cornerR, cornerR, buttonPaint)

        // Title — 비례 배치
        val titleSize = min(28f * density, panelH * 0.12f)
        overlayTitlePaint.textSize = titleSize
        overlayTitlePaint.color = Theme.LAUNCH_ABILITY_EXPLOSIVE
        overlayTitlePaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Stage Failed", w / 2f, panelTop + panelH * 0.25f, overlayTitlePaint)

        // Buttons — 비례 배치
        val btnW = panelW * 0.7f
        val btnH = min(44f * density, panelH * 0.15f)
        val btnGap = min(10f * density, panelH * 0.04f)
        val btnLeft = (w - btnW) / 2f
        val btnRight = btnLeft + btnW
        val btnCorner = btnH / 2f
        val btnTextSize = min(16f * density, btnH * 0.45f)

        // Retry button
        val retryTop = panelTop + panelH * 0.48f
        val retryBottom = retryTop + btnH
        retryRect.set(btnLeft, retryTop, btnRight, retryBottom)
        buttonPaint.color = Theme.INT_CORAL
        buttonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(retryRect, btnCorner, btnCorner, buttonPaint)
        buttonTextPaint.textSize = btnTextSize
        buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Retry", w / 2f, retryTop + btnH / 2f + btnTextSize / 3f, buttonTextPaint)

        // Menu button
        val menuTop = retryBottom + btnGap
        val menuBottom = menuTop + btnH
        menuRect.set(btnLeft, menuTop, btnRight, menuBottom)
        menuButtonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(menuRect, btnCorner, btnCorner, menuButtonPaint)
        buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Menu", w / 2f, menuTop + btnH / 2f + btnTextSize / 3f, buttonTextPaint)

        // Clear nextStageRect so it is not tappable in fail state
        nextStageRect.setEmpty()

        // Reset alpha on reused paints
        buttonPaint.alpha = 255
        overlayTitlePaint.alpha = 255
        buttonTextPaint.alpha = 255
    }
}
