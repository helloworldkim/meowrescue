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
    @Volatile
    private var battleHUD: BattleHUD = BattleHUD(context)
    val tutorialOverlay = TutorialOverlay(context)

    fun setCatPortrait(catSpriteResId: Int) {
        val old = battleHUD
        battleHUD = BattleHUD(context, catSpriteResId)
        old.cleanup()
    }

    private var backgroundBmp: Bitmap = loadScaled(R.drawable.bg_tutorial, 1080, 1920)

    // ── Overlay paints ──

    private val overlayDimPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Victory popup
    private val victoryPopupPaint = Paint().apply {
        color = Color.parseColor("#1B3A1B")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val victoryBorderPaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    private val victoryTitlePaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        textSize = 80f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
        setShadowLayer(6f, 3f, 3f, Color.argb(160, 0, 0, 0))
    }
    private val victorySubPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val victoryBtnPaint = Paint().apply {
        color = Color.parseColor("#43A047")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val victoryBtnTopPaint = Paint().apply {
        color = Color.parseColor("#66BB6A")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val victoryBtnShadowPaint = Paint().apply {
        color = Color.argb(80, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Defeat popup
    private val defeatPopupPaint = Paint().apply {
        color = Color.parseColor("#2A0A0A")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val defeatBorderPaint = Paint().apply {
        color = Color.parseColor("#8B0000")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val defeatTitlePaint = Paint().apply {
        color = Color.parseColor("#FF4444")
        textSize = 76f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
        setShadowLayer(6f, 3f, 3f, Color.argb(160, 0, 0, 0))
    }
    private val defeatSubPaint = Paint().apply {
        color = Color.parseColor("#FFCCCC")
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val defeatBtnPaint = Paint().apply {
        color = Color.parseColor("#E64A19")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val defeatBtnTopPaint = Paint().apply {
        color = Color.parseColor("#FF7043")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Button text (shared)
    private val overlayBtnTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
        setShadowLayer(3f, 1f, 1f, Color.argb(120, 0, 0, 0))
    }

    // Block legend strip
    private val legendBgPaint = Paint().apply {
        color = Color.argb(140, 10, 10, 30)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val legendSquarePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val legendTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    // Phase indicator
    private val phaseBgPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val phaseTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
        setShadowLayer(4f, 2f, 2f, Color.argb(120, 0, 0, 0))
    }

    private var matchAnimStartTime = 0L
    private var matchAnimPositions: Set<Pair<Int, Int>> = emptySet()
    private var isAnimating = false

    private var showingVictory = false
    private var showingDefeat = false
    private var overlayCallbackFired = false

    // Button rect for overlay tap detection
    private val overlayBtnRect = RectF()

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
            state.turnCount, state.chapter, state.stage,
            if (state.canShuffle) 1 else 0,
            state.relics
        )

        drawBlockLegend(canvas)

        drawPhaseIndicator(canvas, state.phase)

        if (showingVictory) drawVictoryOverlay(canvas)
        if (showingDefeat) drawDefeatOverlay(canvas)

        if (tutorialOverlay.isActive) {
            tutorialOverlay.draw(canvas, GridConstants.DESIGN_WIDTH, GridConstants.DESIGN_HEIGHT)
        }

        if (shakeX != 0f || shakeY != 0f) {
            canvas.translate(-shakeX, -shakeY)
        }

        canvas.restore()
    }

    private fun drawBlockLegend(canvas: Canvas) {
        val engine = battleEngine ?: return
        val gridBottom = gridRenderer.getGridBottom(engine.state.grid) + 10f

        data class LegendEntry(val label: String, val color: Int)
        val entries = listOf(
            LegendEntry("ATK",   Color.parseColor("#FF5252")),
            LegendEntry("FIRE",  Color.parseColor("#FF6B35")),
            LegendEntry("WATER", Color.parseColor("#4FC3F7")),
            LegendEntry("HEAL",  Color.parseColor("#66BB6A"))
        )

        val squareSize = 20f
        val squareTextGap = 6f
        val itemSpacing = 16f
        val vertPad = 8f
        val horizPad = 12f

        // Measure total width to center the strip
        var totalW = horizPad * 2f
        for (entry in entries) {
            totalW += squareSize + squareTextGap + legendTextPaint.measureText(entry.label) + itemSpacing
        }
        totalW -= itemSpacing  // remove trailing spacing

        val stripLeft = (GridConstants.DESIGN_WIDTH - totalW) / 2f
        val stripRight = stripLeft + totalW
        val stripTop = gridBottom
        val stripBottom = stripTop + squareSize + vertPad * 2f

        // Semi-transparent background
        canvas.drawRoundRect(RectF(stripLeft, stripTop, stripRight, stripBottom), 10f, 10f, legendBgPaint)

        var curX = stripLeft + horizPad
        val textY = stripTop + vertPad + squareSize * 0.78f  // baseline aligned with square center

        for (entry in entries) {
            // Colored square
            legendSquarePaint.color = entry.color
            canvas.drawRoundRect(
                RectF(curX, stripTop + vertPad, curX + squareSize, stripTop + vertPad + squareSize),
                4f, 4f, legendSquarePaint
            )
            curX += squareSize + squareTextGap

            // Label
            canvas.drawText(entry.label, curX, textY, legendTextPaint)
            curX += legendTextPaint.measureText(entry.label) + itemSpacing
        }
    }

    private fun drawPhaseIndicator(canvas: Canvas, phase: BattleTurnPhase) {
        val text = when (phase) {
            BattleTurnPhase.PLAYER_INPUT -> "Your Turn"
            BattleTurnPhase.SWAP_BACK -> "No Match!"
            BattleTurnPhase.MATCHING -> "Matching!"
            BattleTurnPhase.CASCADING -> "Cascade!"
            BattleTurnPhase.PLAYER_ATTACK -> "Attack!"
            BattleTurnPhase.ENEMY_ATTACK -> "Enemy Turn"
            BattleTurnPhase.NO_MOVES -> "No Moves!"
            BattleTurnPhase.VICTORY, BattleTurnPhase.DEFEAT -> return
        }

        val bgColor = when (phase) {
            BattleTurnPhase.PLAYER_INPUT -> Color.argb(180, 30, 100, 30)
            BattleTurnPhase.PLAYER_ATTACK -> Color.argb(180, 180, 80, 0)
            BattleTurnPhase.ENEMY_ATTACK -> Color.argb(180, 150, 30, 30)
            BattleTurnPhase.MATCHING, BattleTurnPhase.CASCADING -> Color.argb(180, 40, 80, 160)
            BattleTurnPhase.SWAP_BACK, BattleTurnPhase.NO_MOVES -> Color.argb(180, 100, 40, 40)
            else -> Color.argb(180, 60, 60, 60)
        }

        val centerX = GridConstants.DESIGN_WIDTH / 2f
        val pillY = 710f
        val textWidth = phaseTextPaint.measureText(text)
        val pillPadH = 24f
        val pillPadV = 10f
        val pillRect = RectF(
            centerX - textWidth / 2f - pillPadH,
            pillY - phaseTextPaint.textSize / 2f - pillPadV,
            centerX + textWidth / 2f + pillPadH,
            pillY + phaseTextPaint.textSize / 2f + pillPadV
        )

        phaseBgPaint.color = bgColor
        canvas.drawRoundRect(pillRect, 20f, 20f, phaseBgPaint)
        canvas.drawText(text, centerX, pillY + 12f, phaseTextPaint)
    }

    private fun drawVictoryOverlay(canvas: Canvas) {
        val centerX = GridConstants.DESIGN_WIDTH / 2f
        val centerY = GridConstants.DESIGN_HEIGHT / 2f

        // Dim background
        canvas.drawRect(0f, 0f, GridConstants.DESIGN_WIDTH, GridConstants.DESIGN_HEIGHT, overlayDimPaint)

        // Popup card
        val popupRect = RectF(centerX - 300f, centerY - 260f, centerX + 300f, centerY + 240f)
        canvas.drawRoundRect(popupRect, 28f, 28f, victoryPopupPaint)
        canvas.drawRoundRect(popupRect, 28f, 28f, victoryBorderPaint)

        // Inner gold line
        val innerRect = RectF(popupRect.left + 8f, popupRect.top + 8f, popupRect.right - 8f, popupRect.bottom - 8f)
        victoryBorderPaint.strokeWidth = 2f
        canvas.drawRoundRect(innerRect, 22f, 22f, victoryBorderPaint)
        victoryBorderPaint.strokeWidth = 5f

        // Title
        canvas.drawText("VICTORY!", centerX, centerY - 120f, victoryTitlePaint)

        // Cat rescued text with emoji
        canvas.drawText("\uD83D\uDC31 Cat Rescued!", centerX, centerY - 50f, victorySubPaint)

        // Button with shadow + gradient effect
        val btnRect = RectF(centerX - 140f, centerY + 60f, centerX + 140f, centerY + 140f)
        overlayBtnRect.set(btnRect)

        // Shadow
        canvas.drawRoundRect(
            RectF(btnRect.left + 3f, btnRect.top + 4f, btnRect.right + 3f, btnRect.bottom + 4f),
            18f, 18f, victoryBtnShadowPaint
        )
        // Base button
        canvas.drawRoundRect(btnRect, 18f, 18f, victoryBtnPaint)
        // Top highlight
        canvas.drawRoundRect(
            RectF(btnRect.left, btnRect.top, btnRect.right, btnRect.centerY()),
            18f, 18f, victoryBtnTopPaint
        )

        canvas.drawText("Continue", centerX, centerY + 110f, overlayBtnTextPaint)
    }

    private fun drawDefeatOverlay(canvas: Canvas) {
        val centerX = GridConstants.DESIGN_WIDTH / 2f
        val centerY = GridConstants.DESIGN_HEIGHT / 2f

        // Dim background
        canvas.drawRect(0f, 0f, GridConstants.DESIGN_WIDTH, GridConstants.DESIGN_HEIGHT, overlayDimPaint)

        // Popup card
        val popupRect = RectF(centerX - 300f, centerY - 260f, centerX + 300f, centerY + 240f)
        canvas.drawRoundRect(popupRect, 28f, 28f, defeatPopupPaint)
        canvas.drawRoundRect(popupRect, 28f, 28f, defeatBorderPaint)

        // Title
        canvas.drawText("DEFEAT", centerX, centerY - 120f, defeatTitlePaint)

        // Sub text
        canvas.drawText("Try Again?", centerX, centerY - 50f, defeatSubPaint)

        // Button with gradient effect
        val btnRect = RectF(centerX - 140f, centerY + 60f, centerX + 140f, centerY + 140f)
        overlayBtnRect.set(btnRect)

        // Shadow
        canvas.drawRoundRect(
            RectF(btnRect.left + 3f, btnRect.top + 4f, btnRect.right + 3f, btnRect.bottom + 4f),
            18f, 18f, victoryBtnShadowPaint
        )
        // Base button
        canvas.drawRoundRect(btnRect, 18f, 18f, defeatBtnPaint)
        // Top highlight
        canvas.drawRoundRect(
            RectF(btnRect.left, btnRect.top, btnRect.right, btnRect.centerY()),
            18f, 18f, defeatBtnTopPaint
        )

        canvas.drawText("Retry", centerX, centerY + 110f, overlayBtnTextPaint)
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

        if (tutorialOverlay.isActive) {
            tutorialOverlay.advance()
            return true
        }

        if (showingVictory || showingDefeat) {
            if (!overlayCallbackFired) {
                if (overlayBtnRect.contains(x, y)) {
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
                if (engine.selectedRow >= 0) {
                    gridRenderer.setSelection(engine.selectedRow, engine.selectedCol)
                } else {
                    gridRenderer.clearSelection()
                }
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
        battleHUD.cleanup()
    }
}
