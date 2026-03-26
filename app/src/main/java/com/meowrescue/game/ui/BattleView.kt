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

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    val gridRenderer = GridRenderer(context)
    val enemyRenderer = EnemyRenderer(context)
    val effectRenderer = EffectRenderer()
    private val battleHUD = BattleHUD()

    private var backgroundBmp: Bitmap = loadScaled(R.drawable.bg_tutorial, 1080, 1920)

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
    private val phaseTextPaint = Paint().apply {
        color = Color.argb(200, 255, 255, 255)
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private var matchAnimStartTime = 0L
    private var matchAnimPositions: Set<Pair<Int, Int>> = emptySet()
    private var isAnimating = false

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

        val (shakeX, shakeY) = effectRenderer.applyScreenShake(canvas)

        canvas.drawBitmap(backgroundBmp, null, RectF(0f, 0f, GridConstants.DESIGN_WIDTH, GridConstants.DESIGN_HEIGHT), null)

        enemyRenderer.draw(canvas, state.enemies, 80f, GridConstants.DESIGN_WIDTH)

        updateMatchAnimation()
        gridRenderer.draw(canvas, state.grid)

        effectRenderer.draw(canvas)

        battleHUD.draw(
            canvas, GridConstants.DESIGN_WIDTH,
            state.playerCurrentHp, state.playerMaxHp,
            state.turnCount, state.chapter, state.stage
        )

        drawPhaseIndicator(canvas, state.phase)

        if (showingVictory) drawResultOverlay(canvas, true)
        if (showingDefeat) drawResultOverlay(canvas, false)

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
            BattleTurnPhase.VICTORY, BattleTurnPhase.DEFEAT -> return
        }
        canvas.drawText(text, GridConstants.DESIGN_WIDTH / 2f, 730f, phaseTextPaint)
    }

    private fun drawResultOverlay(canvas: Canvas, isVictory: Boolean) {
        overlayPaint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(0f, 0f, GridConstants.DESIGN_WIDTH, GridConstants.DESIGN_HEIGHT, overlayPaint)

        val centerX = GridConstants.DESIGN_WIDTH / 2f
        val centerY = GridConstants.DESIGN_HEIGHT / 2f

        overlayPaint.color = Color.parseColor(if (isVictory) "#2E7D32" else "#C62828")
        canvas.drawRoundRect(RectF(centerX - 250f, centerY - 200f, centerX + 250f, centerY + 200f), 24f, 24f, overlayPaint)

        canvas.drawText(if (isVictory) "Victory!" else "Defeat...", centerX, centerY - 80f, overlayTextPaint)
        canvas.drawText(if (isVictory) "Cat Rescued!" else "Try Again?", centerX, centerY - 20f, overlaySubTextPaint)

        overlayBtnPaint.color = Color.parseColor(if (isVictory) "#4CAF50" else "#FF5722")
        canvas.drawRoundRect(RectF(centerX - 120f, centerY + 40f, centerX + 120f, centerY + 110f), 16f, 16f, overlayBtnPaint)

        canvas.drawText(if (isVictory) "Continue" else "Retry", centerX, centerY + 85f, overlayBtnTextPaint)
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

        if (battleHUD.isPauseTapped(x, y)) {
            onPause?.invoke()
            return true
        }

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
        gridRenderer.cleanup()
        enemyRenderer.cleanup()
        effectRenderer.clear()
        backgroundBmp.recycle()
    }
}
