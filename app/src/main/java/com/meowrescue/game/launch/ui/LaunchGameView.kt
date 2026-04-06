package com.meowrescue.game.launch.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.meowrescue.game.launch.model.*
import com.meowrescue.game.launch.physics.LaunchPhysicsWorld
import com.meowrescue.game.ui.Theme
import kotlin.math.max
import kotlin.math.min

class LaunchGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private const val TARGET_FPS = 60L
        private const val FRAME_MS = 1000L / TARGET_FPS

        internal const val HUD_HEIGHT_DP = 56f

        private const val MAX_PULL_DISTANCE_DP = 120f
        private const val CAMERA_LERP = 0.08f
        private const val SETTLE_VELOCITY_THRESHOLD = LaunchPhysicsWorld.SETTLED_VELOCITY_THRESHOLD
        private const val VIEW_WIDTH_METERS_PORTRAIT = 10f
        private const val VIEW_WIDTH_METERS_LANDSCAPE = 14f
        internal const val PAN_THRESHOLD_PX = 15f

        private const val BG_SKY_TOP = Theme.LAUNCH_SKY_TOP
        private const val BG_SKY_BOTTOM = Theme.LAUNCH_SKY_BOTTOM

        private const val WORLD_WIDTH = LaunchPhysicsWorld.WORLD_WIDTH
        private const val GROUND_HEIGHT = LaunchPhysicsWorld.GROUND_HEIGHT
    }

    // ── Callbacks ────────────────────────────────────────────────────────────
    var onStageClear: ((catsUsed: Int, stars: Int) -> Unit)? = null
    var onNextStageClicked: (() -> Unit)? = null
    var onRetryClicked: (() -> Unit)? = null
    var onMenuClicked: (() -> Unit)? = null

    // ── Lock for thread-safe access between UI thread and render thread ──────
    private val lock = Any()

    // ── State (internal for helpers) ─────────────────────────────────────────
    internal var gameState = LaunchGameState.AIMING
    internal var physicsWorld: LaunchPhysicsWorld? = null
    internal var stageConfig: StageConfig? = null
    internal var currentCatIndex = 0
    internal var catsUsed = 0

    // ── Slingshot drag ───────────────────────────────────────────────────────
    internal var isDragging = false
    internal var dragStartX = 0f
    internal var dragStartY = 0f
    internal var dragCurrentX = 0f
    internal var dragCurrentY = 0f

    // ── Camera ───────────────────────────────────────────────────────────────
    internal var cameraOffsetX = 0f
    internal var cameraOffsetY = 0f

    // ── Manual camera panning ──────────────────────────────────────────────
    internal var isPanning = false
    internal var panLastX = 0f
    internal var panLastY = 0f
    internal var potentialTap = false
    internal var tapDownX = 0f
    internal var tapDownY = 0f
    internal var manualPanActive = false

    // ── Dynamic view width (adapts to orientation) ─────────────────────────
    private var viewWidthMeters = VIEW_WIDTH_METERS_PORTRAIT

    // ── Coordinate conversion ────────────────────────────────────────────────
    internal var pixelsPerMeter = 1f
    internal var worldOriginScreenY = 0f

    internal var maxPullDistancePx = 0f

    // ── Cat bitmaps ──────────────────────────────────────────────────────────
    internal var catBitmaps: Map<Int, Bitmap> = emptyMap()

    // ── Victory / Fail overlay ───────────────────────────────────────────────
    internal var victoryAlpha = 0f
    internal var victoryStars = 0

    // ── Pending callback deferral ────────────────────────────────────────────
    private var pendingStageClear: Pair<Int, Int>? = null

    // ── Celebration particles ────────────────────────────────────────────────
    internal data class CelebrationParticle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var radius: Float, var color: Int, var alpha: Float
    )
    internal val celebrationParticles = mutableListOf<CelebrationParticle>()

    // ── Explosion effects ────────────────────────────────────────────────────
    internal data class ExplosionEffect(
        var x: Float, var y: Float,
        var radius: Float, var maxRadius: Float,
        var alpha: Float, var startTime: Long
    )
    internal val explosions = mutableListOf<ExplosionEffect>()

    // ── Difficulty label ──────────────────────────────────────────────────────
    internal var difficultyLabel: String = ""

    // ── Settle timer ─────────────────────────────────────────────────────────
    internal var settleFrameCount = 0
    private val SETTLE_FRAMES_REQUIRED = 15

    // ── Button rects ─────────────────────────────────────────────────────────
    internal val pauseRect = RectF()
    internal val nextStageRect = RectF()
    internal val retryRect = RectF()
    internal val menuRect = RectF()

    // ── Sky gradient cache ───────────────────────────────────────────────────
    internal var skyGradient: LinearGradient? = null

    // ── Density ──────────────────────────────────────────────────────────────
    internal val density = resources.displayMetrics.density

    // ── Helpers ──────────────────────────────────────────────────────────────
    private val paints = LaunchPaints(density)
    internal val renderer = LaunchRenderer(this, paints)
    private val inputHandler = LaunchInputHandler(this)

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
    // Layout
    // ─────────────────────────────────────────────────────────────────────────

    private fun recalcLayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val hudHeightPx = HUD_HEIGHT_DP * density
        viewWidthMeters = if (w > h) VIEW_WIDTH_METERS_LANDSCAPE else VIEW_WIDTH_METERS_PORTRAIT
        pixelsPerMeter = w / viewWidthMeters
        maxPullDistancePx = MAX_PULL_DISTANCE_DP * density

        val groundScreenY = h * 0.75f
        worldOriginScreenY = groundScreenY + GROUND_HEIGHT * pixelsPerMeter

        skyGradient = LinearGradient(
            0f, hudHeightPx, 0f, groundScreenY,
            BG_SKY_TOP, BG_SKY_BOTTOM,
            Shader.TileMode.CLAMP
        )
        paints.skyPaint.shader = skyGradient
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Touch handling (delegates to InputHandler)
    // ─────────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        val callback: (() -> Unit)? = synchronized(lock) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> inputHandler.handleDown(x, y)
                MotionEvent.ACTION_MOVE -> { inputHandler.handleMove(x, y); null }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> inputHandler.handleUp(x, y)
                else -> null
            }
        }
        callback?.invoke()
        return true
    }

    internal fun clampCamera() {
        val maxCamX = (WORLD_WIDTH - viewWidthMeters).coerceAtLeast(0f)
        cameraOffsetX = cameraOffsetX.coerceIn(0f, maxCamX)
        cameraOffsetY = cameraOffsetY.coerceIn(0f, 8f)
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
                renderer.render(canvas)
                stageClearData = pendingStageClear
            }
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
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
                    gameState = LaunchGameState.SETTLING
                    settleFrameCount = 0
                }

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
                        val remainingCats = config.catIds.size - currentCatIndex
                        pw.addRemainingCatBonus(remainingCats)
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
                        gameState = LaunchGameState.AIMING
                        manualPanActive = false
                    } else {
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
                if (!isPanning && !manualPanActive) {
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
        if (manualPanActive || isPanning) return

        val projectiles = pw.getProjectiles()
        if (projectiles.isNotEmpty()) {
            val active = projectiles.last()
            val pos = active.body.position
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
            val progress = elapsed / 400f
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
            p.vy += 0.15f
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
                vx = kotlin.math.cos(angle) * speed,
                vy = kotlin.math.sin(angle) * speed - 3f,
                radius = 4f + Math.random().toFloat() * 6f,
                color = colors[(Math.random() * colors.size).toInt()],
                alpha = 1f
            ))
        }
    }
}
