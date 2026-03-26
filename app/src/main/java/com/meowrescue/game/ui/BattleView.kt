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
import com.meowrescue.game.engine.BattleEngine
import com.meowrescue.game.engine.BattleTurnPhase
import com.meowrescue.game.model.MatchResult
import com.meowrescue.game.ui.render.EffectRenderer
import com.meowrescue.game.ui.render.EnemyRenderer
import com.meowrescue.game.ui.render.GridRenderer
import com.meowrescue.game.util.GridConstants

class BattleView(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    var battleEngine: BattleEngine? = null
    var onVictory: (() -> Unit)? = null
    var onDefeat: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null

    var surfaceReady = false
        private set

    // Virtual coordinate scaling
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // Sub-renderers
    val gridRenderer = GridRenderer()
    val enemyRenderer = EnemyRenderer(context)
    val effectRenderer = EffectRenderer()
    private val battleHUD = BattleHUD()

    // Background
    private var backgroundBmp: Bitmap = loadScaled(R.drawable.bg_tutorial, 1080, 1920)

    // Overlay popups
    private val overlayPaint = Paint().apply { style = Paint.Style.FILL }
    private val overlayTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 64f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val overlaySubTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val overlayBtnPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val overlayBtnTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    // Animation state
    private var matchAnimStartTime = 0L
    private var matchAnimPositions: Set<Pair<Int, Int>> = emptySet()
    private var isAnimating = false

    // Overlay state
    private var showingVictory = false
    private var showingDefeat = false
    private var overlayCallbackFired = false

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val scaleX = width / GridConstants.DESIGN_WIDTH
        val scaleY = height / GridConstants.DESIGN_HEIGHT
        scale = minOf(scaleX, scaleY)
        offsetX = (width - GridConstants.DESIGN_WIDTH * scale) / 2f
        offsetY = (height - GridConstants.DESIGN_HEIGHT * scale) / 2f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    fun setupGrid() {
        val engine = battleEngine ?: return
        gridRenderer.computeLayout(GridConstants.DESIGN_WIDTH, 750f, engine.state.grid)
    }

    fun render() {
        if (!surfaceReady) return
        val canvas = holder.lockCanvas() ?: return
        try {
            drawFrame(canvas)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawFrame(canvas: Canvas) {
        val engine = battleEngine ?: return
        val state = engine.state

        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // Screen shake
        val (shakeX, shakeY) = effectRenderer.applyScreenShake(canvas)

        // Background
        val bgRect = RectF(0f, 0f, GridConstants.DESIGN_WIDTH, GridConstants.DESIGN_HEIGHT)
        canvas.drawBitmap(backgroundBmp, null, bgRect, null)

        // Enemies (top area)
        enemyRenderer.draw(canvas, state.enemies, 80f, GridConstants.DESIGN_WIDTH)

        // Grid (middle area)
        updateMatchAnimation()
        gridRenderer.draw(canvas, state.grid)

        // Effects (damage numbers, etc)
        effectRenderer.draw(canvas)

        // HUD
        battleHUD.draw(
            canvas, GridConstants.DESIGN_WIDTH,
            state.playerCurrentHp, state.playerMaxHp,
            state.turnCount, state.chapter, state.stage
        )

        // Phase indicator
        drawPhaseIndicator(canvas, state.phase)

        // Victory/Defeat overlay
        if (showingVictory) drawResultOverlay(canvas, true)
        if (showingDefeat) drawResultOverlay(canvas, false)

        // Undo screen shake
        if (shakeX != 0f || shakeY != 0f) {
            canvas.translate(-shakeX, -shakeY)
        }

        canvas.restore()
    }

    private fun drawPhaseIndicator(canvas: Canvas, phase: BattleTurnPhase) {
        val text = when (phase) {
            BattleTurnPhase.PLAYER_INPUT -> "Your Turn"
            BattleTurnPhase.MATCHING -> "Matching!"
            BattleTurnPhase.CASCADING -> "Cascade!"
            BattleTurnPhase.PLAYER_ATTACK -> "Attack!"
            BattleTurnPhase.ENEMY_ATTACK -> "Enemy Turn"
            BattleTurnPhase.VICTORY -> ""
            BattleTurnPhase.DEFEAT -> ""
        }
        if (text.isNotEmpty()) {
            val phaseTextPaint = Paint().apply {
                color = Color.argb(200, 255, 255, 255)
                textSize = 30f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(text, GridConstants.DESIGN_WIDTH / 2f, 730f, phaseTextPaint)
        }
    }

    private fun drawResultOverlay(canvas: Canvas, isVictory: Boolean) {
        overlayPaint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(0f, 0f, GridConstants.DESIGN_WIDTH, GridConstants.DESIGN_HEIGHT, overlayPaint)

        val centerX = GridConstants.DESIGN_WIDTH / 2f
        val centerY = GridConstants.DESIGN_HEIGHT / 2f

        // Popup background
        val popupRect = RectF(centerX - 250f, centerY - 200f, centerX + 250f, centerY + 200f)
        overlayPaint.color = Color.parseColor(if (isVictory) "#2E7D32" else "#C62828")
        canvas.drawRoundRect(popupRect, 24f, 24f, overlayPaint)

        val title = if (isVictory) "Victory!" else "Defeat..."
        canvas.drawText(title, centerX, centerY - 80f, overlayTextPaint)

        val subtitle = if (isVictory) "Cat Rescued!" else "Try Again?"
        canvas.drawText(subtitle, centerX, centerY - 20f, overlaySubTextPaint)

        // Continue button
        val btnRect = RectF(centerX - 120f, centerY + 40f, centerX + 120f, centerY + 110f)
        overlayBtnPaint.color = Color.parseColor(if (isVictory) "#4CAF50" else "#FF5722")
        canvas.drawRoundRect(btnRect, 16f, 16f, overlayBtnPaint)

        val btnText = if (isVictory) "Continue" else "Retry"
        canvas.drawText(btnText, centerX, centerY + 85f, overlayBtnTextPaint)
    }

    fun showVictory() {
        showingVictory = true
        overlayCallbackFired = false
    }

    fun showDefeat() {
        showingDefeat = true
        overlayCallbackFired = false
    }

    fun startMatchAnimation(positions: Set<Pair<Int, Int>>) {
        matchAnimPositions = positions
        matchAnimStartTime = System.currentTimeMillis()
        isAnimating = true
    }

    fun isAnimatingMatch(): Boolean = isAnimating

    private fun updateMatchAnimation() {
        if (!isAnimating || matchAnimPositions.isEmpty()) {
            gridRenderer.clearMatchAnimation()
            return
        }
        val elapsed = System.currentTimeMillis() - matchAnimStartTime
        val progress = (elapsed.toFloat() / GridConstants.MATCH_ANIM_MS).coerceIn(0f, 1f)
        gridRenderer.setMatchAnimation(matchAnimPositions, progress)
        if (progress >= 1f) {
            isAnimating = false
            gridRenderer.clearMatchAnimation()
            matchAnimPositions = emptySet()
        }
    }

    private fun screenToDesign(screenX: Float, screenY: Float): Pair<Float, Float> {
        val gameX = (screenX - offsetX) / scale
        val gameY = (screenY - offsetY) / scale
        return gameX to gameY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val (x, y) = screenToDesign(event.x, event.y)
        val engine = battleEngine ?: return true

        // Victory/Defeat overlay tap
        if (showingVictory || showingDefeat) {
            if (!overlayCallbackFired) {
                val centerX = GridConstants.DESIGN_WIDTH / 2f
                val centerY = GridConstants.DESIGN_HEIGHT / 2f
                if (x in (centerX - 120f)..(centerX + 120f) && y in (centerY + 40f)..(centerY + 110f)) {
                    overlayCallbackFired = true
                    if (showingVictory) onVictory?.invoke() else onDefeat?.invoke()
                }
            }
            return true
        }

        // Pause button
        if (battleHUD.isPauseTapped(x, y)) {
            onPause?.invoke()
            return true
        }

        // Grid tap (only during PLAYER_INPUT)
        if (engine.state.phase == BattleTurnPhase.PLAYER_INPUT && !isAnimating) {
            val gridPos = gridRenderer.screenToGrid(x, y, engine.state.grid)
            if (gridPos != null) {
                val (row, col) = gridPos
                engine.onPlayerSelectBlock(row, col)
            }
        }

        return true
    }

    private fun loadScaled(resId: Int, w: Int, h: Int): Bitmap {
        val raw = BitmapFactory.decodeResource(resources, resId)
        return Bitmap.createScaledBitmap(raw, w, h, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    fun cleanup() {
        enemyRenderer.cleanup()
        effectRenderer.clear()
        backgroundBmp.recycle()
    }
}
