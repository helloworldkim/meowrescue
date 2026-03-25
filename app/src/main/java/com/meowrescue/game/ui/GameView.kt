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
    private val surfacePaint = Paint().apply { color = Color.parseColor("#8B4513"); style = Paint.Style.FILL }
    private val hudPaint = Paint().apply {
        color = Color.parseColor("#333333")
        textSize = 40f
        isAntiAlias = true
    }
    private val overlayPaint = Paint().apply { style = Paint.Style.FILL }
    private val overlayTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 72f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val overlaySubTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 44f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val obstaclePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rescuedCatOverlayPaint = Paint().apply {
        color = Color.argb(100, 0, 200, 0)
        style = Paint.Style.FILL
    }
    private val switchOffPaint = Paint().apply { alpha = (0.4f * 255).toInt() }

    // Bitmap sprites
    private val ballNormalBmp = loadScaled(R.drawable.ball_normal, 60, 60)
    private val ballFireBmp = loadScaled(R.drawable.ball_fire, 60, 60)
    private val ballIronBmp = loadScaled(R.drawable.ball_iron, 72, 72)
    private val ballBombBmp = loadScaled(R.drawable.ball_bomb, 80, 80)

    private val pinNormalBmp = loadScaled(R.drawable.pin_normal, 50, 90)
    private val pinTimerBmp = loadScaled(R.drawable.pin_timer, 50, 90)
    private val pinDirectionBmp = loadScaled(R.drawable.pin_direction, 50, 90)
    private val pinLockedBmp = loadScaled(R.drawable.pin_locked, 50, 90)
    private val pinChainBmp = loadScaled(R.drawable.pin_chain, 50, 90)

    private val obstacleFireBmp = loadScaled(R.drawable.obstacle_fire, 100, 100)
    private val obstacleSpikeBmp = loadScaled(R.drawable.obstacle_spike, 100, 100)
    private val platformBmp = loadScaled(R.drawable.platform, 200, 30)
    private val teleportBmp = loadScaled(R.drawable.teleport, 80, 80)
    private val platformCloudBmp = loadScaled(R.drawable.platform_cloud, 200, 30)
    private val switchBlockBmp = loadScaled(R.drawable.switch_block, 100, 100)

    private val platformBitmaps = listOf(
        loadScaled(R.drawable.platform_1, 200, 30),
        loadScaled(R.drawable.platform_2, 200, 30),
        loadScaled(R.drawable.platform_3, 200, 30),
        loadScaled(R.drawable.platform_4, 200, 30),
        loadScaled(R.drawable.platform_5, 200, 30),
        loadScaled(R.drawable.platform_6, 200, 30)
    )

    private val catBitmaps = listOf(
        loadScaled(R.drawable.cat_1, 80, 80),
        loadScaled(R.drawable.cat_2, 80, 80),
        loadScaled(R.drawable.cat_3, 80, 80),
        loadScaled(R.drawable.cat_4, 80, 80),
        loadScaled(R.drawable.cat_5, 80, 80),
        loadScaled(R.drawable.cat_6, 80, 80),
        loadScaled(R.drawable.cat_7, 80, 80),
        loadScaled(R.drawable.cat_8, 80, 80)
    )

    private val starFullBmp = loadScaled(R.drawable.star_full, 48, 48)
    private val starEmptyBmp = loadScaled(R.drawable.star_empty, 48, 48)
    private val pauseBmp = loadScaled(R.drawable.icon_pause, 50, 50)
    private val btnNextBmp = loadScaled(R.drawable.btn_next, 240, 80)
    private val btnRetryBmp = loadScaled(R.drawable.btn_retry, 240, 80)
    private val btnHomeBmp = loadScaled(R.drawable.btn_home, 240, 80)

    // Backgrounds
    private val bgTutorialBmp = loadScaled(R.drawable.bg_tutorial, 1080, 1920)
    private val bgBeginnerBmp = loadScaled(R.drawable.bg_beginner, 1080, 1920)
    private val bgIntermediateBmp = loadScaled(R.drawable.bg_intermediate, 1080, 1920)
    private val bgAdvancedBmp = loadScaled(R.drawable.bg_advanced, 1080, 1920)

    // Popup overlays
    private val popupClearBmp = loadScaled(R.drawable.popup_clear, 600, 400)
    private val popupFailBmp = loadScaled(R.drawable.popup_fail, 600, 400)

    var currentBackgroundBmp: Bitmap = bgTutorialBmp

    fun setBackgroundForLevel(difficulty: String) {
        currentBackgroundBmp = when (difficulty.lowercase()) {
            "beginner" -> bgBeginnerBmp
            "intermediate" -> bgIntermediateBmp
            "advanced" -> bgAdvancedBmp
            else -> bgTutorialBmp
        }
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
        platformBmp.recycle()
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
        bgTutorialBmp.recycle()
        bgBeginnerBmp.recycle()
        bgIntermediateBmp.recycle()
        bgAdvancedBmp.recycle()
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
        for ((index, surface) in engine.surfaces.withIndex()) {
            canvas.save()
            val cx = surface.position.x + surface.width / 2f
            val cy = surface.position.y + surface.height / 2f
            canvas.translate(cx, cy)
            if (surface.angle != 0f) canvas.rotate(surface.angle)
            val hw = surface.width / 2f
            val hh = surface.height / 2f
            val destRect = RectF(-hw, -hh, hw, hh)
            val bmp = platformBitmaps[index % platformBitmaps.size]
            canvas.drawBitmap(bmp, null, destRect, null)
            canvas.restore()
        }

        // Obstacles
        for (obstacle in engine.obstacles) {
            when (obstacle) {
                is Obstacle.Fire -> {
                    val destRect = RectF(
                        obstacle.position.x, obstacle.position.y,
                        obstacle.position.x + obstacle.size.x, obstacle.position.y + obstacle.size.y
                    )
                    canvas.drawBitmap(obstacleFireBmp, null, destRect, null)
                }
                is Obstacle.Spike -> {
                    val destRect = RectF(
                        obstacle.position.x, obstacle.position.y,
                        obstacle.position.x + obstacle.size.x, obstacle.position.y + obstacle.size.y
                    )
                    canvas.drawBitmap(obstacleSpikeBmp, null, destRect, null)
                }
                is Obstacle.MovingPlatform -> {
                    val destRect = RectF(
                        obstacle.position.x, obstacle.position.y,
                        obstacle.position.x + obstacle.size.x, obstacle.position.y + obstacle.size.y
                    )
                    canvas.drawBitmap(platformCloudBmp, null, destRect, null)
                }
                is Obstacle.Teleport -> {
                    canvas.drawBitmap(
                        teleportBmp,
                        obstacle.position.x - teleportBmp.width / 2f,
                        obstacle.position.y - teleportBmp.height / 2f,
                        null
                    )
                }
                is Obstacle.SwitchBlock -> {
                    val destRect = RectF(
                        obstacle.position.x, obstacle.position.y,
                        obstacle.position.x + obstacle.size.x, obstacle.position.y + obstacle.size.y
                    )
                    canvas.drawBitmap(switchBlockBmp, null, destRect, if (!obstacle.isOn) switchOffPaint else null)
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
        for (cat in engine.cats) {
            val catIndex = engine.cats.indexOf(cat) % catBitmaps.size
            val bmp = catBitmaps[catIndex]
            canvas.drawBitmap(bmp, cat.position.x - bmp.width / 2f, cat.position.y - bmp.height / 2f, null)
            if (cat.isRescued) {
                canvas.drawCircle(cat.position.x, cat.position.y, bmp.width / 2f, rescuedCatOverlayPaint)
            }
        }

        // HUD
        val hudY = 60f
        hudPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Level ${engine.levelData?.levelId ?: ""}", 20f, hudY, hudPaint)

        // HUD Stars
        val stars = engine.calculateStars()
        for (i in 0 until 3) {
            val starBmp = if (i < stars) starFullBmp else starEmptyBmp
            canvas.drawBitmap(starBmp, canvas.width / 2f - 72f + i * 48f, hudY - 36f, null)
        }

        // Pause button (top-right)
        canvas.drawBitmap(pauseBmp, canvas.width - pauseBmp.width - 20f, hudY - pauseBmp.height / 2f - 10f, null)

        // State overlays
        when (engine.gameState) {
            GameEngine.GameState.SUCCESS -> {
                // Semi-transparent dark overlay
                overlayPaint.color = Color.argb(120, 0, 0, 0)
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), overlayPaint)

                // Popup clear centered
                val popupX = canvas.width / 2f - popupClearBmp.width / 2f
                val popupY = canvas.height / 2f - popupClearBmp.height / 2f
                canvas.drawBitmap(popupClearBmp, popupX, popupY, null)

                // Stars on popup
                val starCount = engine.calculateStars()
                val startX = canvas.width / 2f - 72f
                for (i in 0 until 3) {
                    val starBmp = if (i < starCount) starFullBmp else starEmptyBmp
                    canvas.drawBitmap(starBmp, startX + i * 72f, popupY + 60f, null)
                }

                // btnNext right side, btnHome left side below popup center
                val btnY = popupY + popupClearBmp.height - btnNextBmp.height / 2f
                val centerX = canvas.width / 2f
                canvas.drawBitmap(btnHomeBmp, centerX - btnHomeBmp.width - 20f, btnY, null)
                canvas.drawBitmap(btnNextBmp, centerX + 20f, btnY, null)
            }
            GameEngine.GameState.FAILED -> {
                // Semi-transparent dark overlay
                overlayPaint.color = Color.argb(120, 0, 0, 0)
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), overlayPaint)

                // Popup fail centered
                val popupX = canvas.width / 2f - popupFailBmp.width / 2f
                val popupY = canvas.height / 2f - popupFailBmp.height / 2f
                canvas.drawBitmap(popupFailBmp, popupX, popupY, null)

                // btnRetry right side, btnHome left side
                val btnY = popupY + popupFailBmp.height - btnRetryBmp.height / 2f
                val centerX = canvas.width / 2f
                canvas.drawBitmap(btnHomeBmp, centerX - btnHomeBmp.width - 20f, btnY, null)
                canvas.drawBitmap(btnRetryBmp, centerX + 20f, btnY, null)
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
                GameEngine.GameState.SUCCESS -> {
                    if (!callbackFired) {
                        val popupY = height / 2f - popupClearBmp.height / 2f
                        val btnY = popupY + popupClearBmp.height - btnNextBmp.height / 2f
                        val centerX = width / 2f

                        val homeLeft = centerX - btnHomeBmp.width - 20f
                        val homeRight = centerX - 20f
                        val nextLeft = centerX + 20f
                        val nextRight = centerX + 20f + btnNextBmp.width
                        val btnBottom = btnY + btnNextBmp.height

                        if (x in nextLeft..nextRight && y in btnY..btnBottom) {
                            callbackFired = true
                            onLevelComplete?.invoke()
                        } else if (x in homeLeft..homeRight && y in btnY..btnBottom) {
                            callbackFired = true
                            onNavigateHome?.invoke()
                        }
                    }
                }
                GameEngine.GameState.FAILED -> {
                    if (!callbackFired) {
                        val popupY = height / 2f - popupFailBmp.height / 2f
                        val btnY = popupY + popupFailBmp.height - btnRetryBmp.height / 2f
                        val centerX = width / 2f

                        val homeLeft = centerX - btnHomeBmp.width - 20f
                        val homeRight = centerX - 20f
                        val retryLeft = centerX + 20f
                        val retryRight = centerX + 20f + btnRetryBmp.width
                        val btnBottom = btnY + btnRetryBmp.height

                        if (x in retryLeft..retryRight && y in btnY..btnBottom) {
                            callbackFired = true
                            onLevelFailed?.invoke()
                        } else if (x in homeLeft..homeRight && y in btnY..btnBottom) {
                            callbackFired = true
                            onNavigateHome?.invoke()
                        }
                    }
                }
                GameEngine.GameState.PLAYING -> {
                    val pin = engine.getPinAt(x, y)
                    if (pin != null) engine.requestPinRemoval(pin)
                }
                else -> {}
            }
        }
        return true
    }
}
