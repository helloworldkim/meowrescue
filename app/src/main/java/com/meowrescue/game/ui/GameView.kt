package com.meowrescue.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.meowrescue.game.R
import com.meowrescue.game.game.GameEngine
import com.meowrescue.game.model.Ball
import com.meowrescue.game.model.Obstacle
import com.meowrescue.game.model.Pin

class GameView(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    var gameEngine: GameEngine? = null
    var onLevelComplete: (() -> Unit)? = null
    var onLevelFailed: (() -> Unit)? = null
    var onNavigateHome: (() -> Unit)? = null

    private var surfaceReady = false
    private var callbackFired = false

    // Paints
    private val backgroundPaint = Paint().apply { color = Color.parseColor(Theme.COLOR_BACKGROUND_GAME) }
    private val hudPaint = Paint().apply {
        color = Color.parseColor(Theme.COLOR_PRIMARY_TEXT)
        textSize = 40f
        isAntiAlias = true
    }
    private val overlayPaint = Paint().apply { style = Paint.Style.FILL }
    private val rescuedCatOverlayPaint = Paint().apply {
        color = Color.argb(100, 0, 200, 0)
        style = Paint.Style.FILL
    }
    private val switchOffPaint = Paint().apply { alpha = (0.4f * 255).toInt() }

    // Bitmap sprites (sizes preserve actual image aspect ratios)
    // Balls: actual 768x512 (3:2) — keep square for circular physics objects
    private val ballNormalBmp = loadScaled(R.drawable.ball_normal, 60, 60)
    private val ballFireBmp = loadScaled(R.drawable.ball_fire, 60, 60)
    private val ballIronBmp = loadScaled(R.drawable.ball_iron, 72, 72)
    private val ballBombBmp = loadScaled(R.drawable.ball_bomb, 80, 80)

    // Pins: actual 384x563 (2:3 portrait), pin_timer 614x461 (4:3)
    private val pinNormalBmp = loadScaled(R.drawable.pin_normal, 40, 58)
    private val pinTimerBmp = loadScaled(R.drawable.pin_timer, 58, 44)
    private val pinDirectionBmp = loadScaled(R.drawable.pin_direction, 40, 58)
    private val pinLockedBmp = loadScaled(R.drawable.pin_locked, 40, 58)
    private val pinChainBmp = loadScaled(R.drawable.pin_chain, 40, 58)

    // Obstacles: fire/spike 512x512 (1:1), teleport/switch 768x512 (3:2)
    private val obstacleFireBmp = loadScaled(R.drawable.obstacle_fire, 100, 100)
    private val obstacleSpikeBmp = loadScaled(R.drawable.obstacle_spike, 100, 100)
    private val teleportBmp = loadScaled(R.drawable.teleport, 90, 60)
    private val platformCloudBmp = loadScaled(R.drawable.platform_cloud, 200, 200)
    private val switchBlockBmp = loadScaled(R.drawable.switch_block, 120, 80)

    // Platforms: actual 768x341 (2.25:1)
    private val platformBitmaps = listOf(
        loadScaled(R.drawable.platform_1, 200, 89),
        loadScaled(R.drawable.platform_2, 200, 89),
        loadScaled(R.drawable.platform_3, 200, 89),
        loadScaled(R.drawable.platform_4, 200, 89),
        loadScaled(R.drawable.platform_5, 200, 89),
        loadScaled(R.drawable.platform_6, 200, 89)
    )

    // Cats: actual 384x512 (3:4 portrait)
    private val catBitmaps = listOf(
        loadScaled(R.drawable.cat_1, 60, 80),
        loadScaled(R.drawable.cat_2, 60, 80),
        loadScaled(R.drawable.cat_3, 60, 80),
        loadScaled(R.drawable.cat_4, 60, 80),
        loadScaled(R.drawable.cat_5, 60, 80),
        loadScaled(R.drawable.cat_6, 60, 80),
        loadScaled(R.drawable.cat_7, 60, 80),
        loadScaled(R.drawable.cat_8, 60, 80)
    )

    // Stars: actual 384x512 (3:4 portrait)
    private val starFullBmp = loadScaled(R.drawable.star_full, 36, 48)
    private val starEmptyBmp = loadScaled(R.drawable.star_empty, 36, 48)
    private val pauseBmp = loadScaled(R.drawable.icon_pause, 50, 50)
    // Buttons: actual 384x512 (3:4 portrait) — sized to fit overlay popup
    private val btnNextBmp = loadScaled(R.drawable.btn_next, 90, 120)
    private val btnRetryBmp = loadScaled(R.drawable.btn_retry, 90, 120)
    private val btnHomeBmp = loadScaled(R.drawable.btn_home, 90, 120)

    // Background: load only the needed one (actual 512x768 = 2:3 portrait, ~7MB each)
    private var currentBackgroundBmp: Bitmap = loadScaled(R.drawable.bg_tutorial, 1080, 1620)

    // Popup overlays: actual 384x512 (3:4 portrait)
    private val popupClearBmp = loadScaled(R.drawable.popup_clear, 360, 480)
    private val popupFailBmp = loadScaled(R.drawable.popup_fail, 360, 480)

    private val bgResMap = mapOf(
        "tutorial" to R.drawable.bg_tutorial,
        "beginner" to R.drawable.bg_beginner,
        "intermediate" to R.drawable.bg_intermediate,
        "advanced" to R.drawable.bg_advanced
    )

    fun setBackgroundForLevel(difficulty: String) {
        val resId = bgResMap[difficulty.lowercase()] ?: R.drawable.bg_tutorial
        val oldBmp = currentBackgroundBmp
        currentBackgroundBmp = loadScaled(resId, 1080, 1620)
        oldBmp.recycle()
    }

    private fun loadScaled(resId: Int, w: Int, h: Int): Bitmap {
        val raw = BitmapFactory.decodeResource(resources, resId)
        return Bitmap.createScaledBitmap(raw, w, h, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    /** Call after game loop has fully stopped to safely recycle all bitmaps. */
    fun cleanup() {
        recycleBitmaps()
    }

    private fun recycleBitmaps() {
        ballNormalBmp.recycle()
        ballFireBmp.recycle()
        ballIronBmp.recycle()
        ballBombBmp.recycle()
        pinNormalBmp.recycle()
        pinTimerBmp.recycle()
        pinDirectionBmp.recycle()
        pinLockedBmp.recycle()
        pinChainBmp.recycle()
        obstacleFireBmp.recycle()
        obstacleSpikeBmp.recycle()
        teleportBmp.recycle()
        platformCloudBmp.recycle()
        switchBlockBmp.recycle()
        platformBitmaps.forEach { it.recycle() }
        catBitmaps.forEach { it.recycle() }
        starFullBmp.recycle()
        starEmptyBmp.recycle()
        pauseBmp.recycle()
        btnNextBmp.recycle()
        btnRetryBmp.recycle()
        btnHomeBmp.recycle()
        currentBackgroundBmp.recycle()
        popupClearBmp.recycle()
        popupFailBmp.recycle()
    }

    fun resetCallbackState() {
        callbackFired = false
    }

    fun render(engine: GameEngine) {
        if (!surfaceReady) return
        val canvas = holder.lockCanvas() ?: return
        try {
            drawFrame(canvas, engine)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawFrame(canvas: Canvas, engine: GameEngine) {
        // Background
        val bgDestRect = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        canvas.drawBitmap(currentBackgroundBmp, null, bgDestRect, null)

        // Surfaces / platforms (rotate around center to match dyn4j physics)
        for (surface in engine.surfaces) {
            canvas.save()
            val cx = surface.position.x + surface.width / 2f
            val cy = surface.position.y + surface.height / 2f
            canvas.translate(cx, cy)
            if (surface.angle != 0f) canvas.rotate(surface.angle)
            val hw = surface.width / 2f
            val hh = surface.height / 2f
            val destRect = RectF(-hw, -hh, hw, hh)
            val bmp = platformBitmaps[surface.bitmapIndex % platformBitmaps.size]
            canvas.drawBitmap(bmp, null, destRect, null)
            canvas.restore()
        }

        // Obstacles
        for (obstacle in engine.obstacles) {
            when (obstacle) {
                is Obstacle.Fire -> canvas.drawBitmap(obstacleFireBmp, null, obstacle.toRectF(), null)
                is Obstacle.Spike -> canvas.drawBitmap(obstacleSpikeBmp, null, obstacle.toRectF(), null)
                is Obstacle.MovingPlatform -> canvas.drawBitmap(platformCloudBmp, null, obstacle.toRectF(), null)
                is Obstacle.Teleport -> {
                    canvas.drawBitmap(
                        teleportBmp,
                        obstacle.position.x - teleportBmp.width / 2f,
                        obstacle.position.y - teleportBmp.height / 2f,
                        null
                    )
                }
                is Obstacle.SwitchBlock -> {
                    canvas.drawBitmap(switchBlockBmp, null, obstacle.toRectF(), if (!obstacle.isOn) switchOffPaint else null)
                }
            }
        }

        // Pins
        for (pin in engine.pins) {
            if (pin.isRemoved) continue
            val bmp = when (pin) {
                is Pin.Normal -> pinNormalBmp
                is Pin.Timer -> pinTimerBmp
                is Pin.Directional -> pinDirectionBmp
                is Pin.Chain -> pinChainBmp
                is Pin.Locked -> pinLockedBmp
            }
            canvas.drawBitmap(bmp, pin.position.x - bmp.width / 2f, pin.position.y - bmp.height / 2f, null)
        }

        // Balls
        for (ball in engine.balls) {
            val bmp = when (ball) {
                is Ball.Normal -> ballNormalBmp
                is Ball.Fire -> ballFireBmp
                is Ball.Iron -> ballIronBmp
                is Ball.Bomb -> ballBombBmp
            }
            canvas.drawBitmap(bmp, ball.position.x - bmp.width / 2f, ball.position.y - bmp.height / 2f, null)
        }

        // Cats
        for ((catIndex, cat) in engine.cats.withIndex()) {
            val bmp = catBitmaps[catIndex % catBitmaps.size]
            canvas.drawBitmap(bmp, cat.position.x - bmp.width / 2f, cat.position.y - bmp.height / 2f, null)
            if (cat.isRescued) {
                canvas.drawCircle(cat.position.x, cat.position.y, bmp.width / 2f, rescuedCatOverlayPaint)
            }
        }

        // HUD
        val hudY = 60f
        hudPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Level ${engine.levelData?.levelId ?: ""}", 20f, hudY, hudPaint)

        // HUD Stars (36x48 each, 4px gap → 40px step, total 116px → offset -58)
        val stars = engine.calculateStars()
        for (i in 0 until 3) {
            val starBmp = if (i < stars) starFullBmp else starEmptyBmp
            canvas.drawBitmap(starBmp, canvas.width / 2f - 58f + i * 40f, hudY - 36f, null)
        }

        // Pause button (top-right)
        canvas.drawBitmap(pauseBmp, canvas.width - pauseBmp.width - 20f, hudY - pauseBmp.height / 2f - 10f, null)

        // State overlays
        when (engine.gameState) {
            GameEngine.GameState.SUCCESS -> {
                val starCount = engine.calculateStars()
                drawOverlay(canvas, popupClearBmp, btnNextBmp, starCount)
            }
            GameEngine.GameState.FAILED -> {
                drawOverlay(canvas, popupFailBmp, btnRetryBmp, showStars = 0)
            }
            else -> {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            val engine = gameEngine ?: return true

            when (engine.gameState) {
                GameEngine.GameState.SUCCESS ->
                    handleOverlayTouch(x, y, popupClearBmp, btnNextBmp, onLevelComplete)
                GameEngine.GameState.FAILED ->
                    handleOverlayTouch(x, y, popupFailBmp, btnRetryBmp, onLevelFailed)
                GameEngine.GameState.PLAYING -> {
                    val pin = engine.getPinAt(x, y)
                    if (pin != null) engine.requestPinRemoval(pin)
                }
                else -> {}
            }
        }
        return true
    }

    /** Creates a RectF from an obstacle's position and size. */
    private fun Obstacle.toRectF(): RectF = RectF(
        position.x, position.y,
        position.x + size.x, position.y + size.y
    )

    /** Draws a popup overlay with dimmed background, optional stars, and two action buttons. */
    private fun drawOverlay(canvas: Canvas, popupBmp: Bitmap, actionBtnBmp: Bitmap, showStars: Int) {
        // Semi-transparent dark overlay
        overlayPaint.color = Color.argb(120, 0, 0, 0)
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), overlayPaint)

        // Popup centered
        val popupX = canvas.width / 2f - popupBmp.width / 2f
        val popupY = canvas.height / 2f - popupBmp.height / 2f
        canvas.drawBitmap(popupBmp, popupX, popupY, null)

        // Stars on popup (only if showStars > 0, 36x48 each, 40px step)
        if (showStars > 0) {
            val startX = canvas.width / 2f - 58f
            for (i in 0 until 3) {
                val starBmp = if (i < showStars) starFullBmp else starEmptyBmp
                canvas.drawBitmap(starBmp, startX + i * 40f, popupY + 60f, null)
            }
        }

        // btnHome left side, action button right side below popup
        val btnY = popupY + popupBmp.height - actionBtnBmp.height / 2f
        val centerX = canvas.width / 2f
        canvas.drawBitmap(btnHomeBmp, centerX - btnHomeBmp.width - 20f, btnY, null)
        canvas.drawBitmap(actionBtnBmp, centerX + 20f, btnY, null)
    }

    /** Handles touch events on overlay popup buttons (home + action). */
    private fun handleOverlayTouch(
        x: Float, y: Float,
        popupBmp: Bitmap, actionBtnBmp: Bitmap,
        actionCallback: (() -> Unit)?
    ) {
        if (callbackFired) return
        val popupY = height / 2f - popupBmp.height / 2f
        val btnY = popupY + popupBmp.height - actionBtnBmp.height / 2f
        val centerX = width / 2f
        val btnBottom = btnY + actionBtnBmp.height

        val homeLeft = centerX - btnHomeBmp.width - 20f
        val homeRight = centerX - 20f
        val actionLeft = centerX + 20f
        val actionRight = centerX + 20f + actionBtnBmp.width

        if (x in actionLeft..actionRight && y in btnY..btnBottom) {
            callbackFired = true
            actionCallback?.invoke()
        } else if (x in homeLeft..homeRight && y in btnY..btnBottom) {
            callbackFired = true
            onNavigateHome?.invoke()
        }
    }
}
