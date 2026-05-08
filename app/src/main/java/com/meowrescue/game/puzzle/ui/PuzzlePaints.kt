package com.meowrescue.game.puzzle.ui

import android.graphics.*

class PuzzlePaints(density: Float) {

    val gridBgPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PuzzleView.GRID_BG }
    val linePaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PuzzleView.CELL_LINE; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    val blockPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    val blockShadow   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000; style = Paint.Style.FILL
    }
    val textPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val hudTextPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val hudBgPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF3E0.toInt()
    }
    val exitPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PuzzleView.EXIT_COLOR }
    val overlayPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    val buttonPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt()
    }
    val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val glossPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val flashPaint    = Paint()
    val tutBgPaint    = Paint()
    val tutPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShadowLayer(8f, 0f, 4f, 0x44000000) }
    val tutTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val tutBodyPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt(); textAlign = Paint.Align.CENTER
    }
    val tutHintPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF616161.toInt(); textAlign = Paint.Align.CENTER
    }

    val starInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8D6E63.toInt(); textAlign = Paint.Align.CENTER
    }
    val bannerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF3E0.toInt()
    }
    val bannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = 0xFF5D4037.toInt()
    }
    val portalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val portalInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val portalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.WHITE
    }
    val lockCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    val lockIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val badgeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    val checkpointCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val checkpointBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    val checkpointStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    val checkpointPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    val checkpointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val exit2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PuzzleView.EXIT2_COLOR; style = Paint.Style.FILL
    }
    val exit2GapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PuzzleView.EXIT2_COLOR; alpha = 80
    }
    val hintGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    val hintArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    val hintShaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    val wallXPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFBCAAA4.toInt(); strokeWidth = 3f; style = Paint.Style.STROKE
    }
    var catDragBlur: BlurMaskFilter? = null
    var hintGlowBlur: BlurMaskFilter? = null
    val catShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val catRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    val catFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PuzzleView.CAT_COLOR; alpha = 200
    }
    val chainIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.WHITE
    }
    val slideOutFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PuzzleView.CAT_COLOR; alpha = 200
    }

    // ── Combo text ──────────────────────────────────────────────────
    val comboTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val comboShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    // ── Coin HUD ────────────────────────────────────────────────────
    val coinTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT
    }
    val coinIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt()
    }
    val coinAnimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    // ── Power-up buttons ────────────────────────────────────────────
    val powerUpBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val powerUpIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    val powerUpLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }

    // ── Score / New Record ──────────────────────────────────────────
    val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val newRecordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF1744.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    // ── Share button ────────────────────────────────────────────────
    val shareBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF26A69A.toInt()
    }

    // ── Cat expression overlay ──────────────────────────────────────
    val expressionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    val victoryPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF8F0.toInt()
        setShadowLayer(12f, 0f, 4f, 0x44000000)
    }
    val victoryTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val victoryMovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val victoryShimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt()
    }
    val victoryStarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val victoryPerfectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val pauseFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt()
    }

    // ── Path cache ─────────────────────────────────────────────────────────
    val starPath  = Path()
    val arrowPath = Path()

    // ── Gloss gradient cache ───────────────────────────────────────────────
    var glossGradient: LinearGradient? = null

    fun updateLayout(cellSize: Float) {
        catDragBlur = BlurMaskFilter(cellSize * 0.22f, BlurMaskFilter.Blur.NORMAL)
        hintGlowBlur = BlurMaskFilter(cellSize * 0.18f, BlurMaskFilter.Blur.OUTER)
        glossGradient = LinearGradient(
            0f, 0f, 0f, cellSize * 0.4f,
            intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
        )
    }
}
