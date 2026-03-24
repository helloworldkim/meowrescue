package com.meowrescue.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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

    private var surfaceReady = false

    // Paints
    private val backgroundPaint = Paint().apply { color = Color.parseColor("#FFF5E6") }
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

    // Bitmap sprites
    private val ballNormalBmp = loadScaled(R.drawable.ball_normal, 60, 60)
    private val ballFireBmp = loadScaled(R.drawable.ball_fire, 60, 60)
    private val ballIronBmp = loadScaled(R.drawable.ball_iron, 72, 72)
    private val ballBombBmp = loadScaled(R.drawable.ball_bomb, 80, 80)

    private val pinNormalBmp = loadScaled(R.drawable.pin_normal, 50, 90)
    private val pinTimerBmp = loadScaled(R.drawable.pin_timer, 50, 90)
    private val pinDirectionBmp = loadScaled(R.drawable.pin_direction, 50, 90)
    private val pinLockedBmp = loadScaled(R.drawable.pin_locked, 50, 90)

    private val obstacleFireBmp = loadScaled(R.drawable.obstacle_fire, 100, 100)
    private val obstacleSpikeBmp = loadScaled(R.drawable.obstacle_spike, 100, 100)
    private val platformBmp = loadScaled(R.drawable.platform, 200, 30)
    private val teleportBmp = loadScaled(R.drawable.teleport, 80, 80)

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
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), backgroundPaint)

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
            canvas.drawBitmap(platformBmp, null, destRect, null)
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
                    canvas.drawBitmap(platformBmp, null, destRect, null)
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
                    obstaclePaint.color = if (obstacle.isOn) Color.parseColor("#228B22") else Color.parseColor("#CC2200")
                    canvas.drawRect(
                        obstacle.position.x, obstacle.position.y,
                        obstacle.position.x + obstacle.size.x, obstacle.position.y + obstacle.size.y,
                        obstaclePaint
                    )
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
                is Pin.Chain -> pinNormalBmp
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
                val overlayColor = Paint().apply {
                    color = Color.argb(100, 0, 200, 0)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cat.position.x, cat.position.y, bmp.width / 2f, overlayColor)
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
                overlayPaint.color = Color.argb(180, 0, 180, 80)
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), overlayPaint)
                canvas.drawText("Level Clear!", canvas.width / 2f, canvas.height / 2f - 100f, overlayTextPaint)
                val starCount = engine.calculateStars()
                val startX = canvas.width / 2f - 72f
                for (i in 0 until 3) {
                    val starBmp = if (i < starCount) starFullBmp else starEmptyBmp
                    canvas.drawBitmap(starBmp, startX + i * 72f, canvas.height / 2f - 60f, null)
                }
                canvas.drawBitmap(
                    btnNextBmp,
                    canvas.width / 2f - btnNextBmp.width / 2f,
                    canvas.height / 2f + 40f,
                    null
                )
            }
            GameEngine.GameState.FAILED -> {
                overlayPaint.color = Color.argb(180, 200, 40, 40)
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), overlayPaint)
                canvas.drawText("Failed!", canvas.width / 2f, canvas.height / 2f - 80f, overlayTextPaint)
                canvas.drawBitmap(
                    btnRetryBmp,
                    canvas.width / 2f - btnRetryBmp.width / 2f,
                    canvas.height / 2f + 20f,
                    null
                )
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
                GameEngine.GameState.SUCCESS -> onLevelComplete?.invoke()
                GameEngine.GameState.FAILED -> onLevelFailed?.invoke()
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
