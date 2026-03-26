package com.meowrescue.game.ui.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.meowrescue.game.R
import com.meowrescue.game.model.BlockType
import com.meowrescue.game.model.GridState
import com.meowrescue.game.util.GridConstants
import kotlin.math.pow
import kotlin.math.sin

class GridRenderer(context: Context) {

    private val blockBitmaps = mapOf(
        BlockType.ATTACK to loadScaled(context, R.drawable.block_attack, 128, 128),
        BlockType.FIRE to loadScaled(context, R.drawable.block_fire, 128, 128),
        BlockType.WATER to loadScaled(context, R.drawable.block_water, 128, 128),
        BlockType.HEAL to loadScaled(context, R.drawable.block_heal, 128, 128)
    )

    private val gridBgPaint = Paint().apply {
        color = Color.argb(120, 10, 10, 30)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gridBorderPaint = Paint().apply {
        color = Color.argb(80, 180, 180, 220)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val blockShadowPaint = Paint().apply {
        color = Color.argb(90, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val selectionGlowPaint = Paint().apply {
        color = Color.argb(60, 255, 255, 100)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val selectionStrokePaint = Paint().apply {
        color = Color.argb(200, 255, 255, 0)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val matchFlashPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Selection state
    private var selectedRow = -1
    private var selectedCol = -1

    // Animation state
    private var matchingPositions: Set<Pair<Int, Int>> = emptySet()
    private var matchAnimProgress = 0f

    // Grid layout computed values
    private var gridLeft = 0f
    private var gridTop = 0f
    private var cellSize = 0f
    private var gap = 0f

    private fun loadScaled(context: Context, resId: Int, w: Int, h: Int): Bitmap {
        val raw = BitmapFactory.decodeResource(context.resources, resId)
        return Bitmap.createScaledBitmap(raw, w, h, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    fun computeLayout(designWidth: Float, gridYOffset: Float, grid: GridState) {
        val totalGap = (grid.width + 1) * GridConstants.BLOCK_GAP_DP
        cellSize = (designWidth - GridConstants.GRID_PADDING_DP * 2 - totalGap) / grid.width.toFloat()
        gap = GridConstants.BLOCK_GAP_DP.toFloat()
        gridLeft = (designWidth - (grid.width * cellSize + (grid.width + 1) * gap)) / 2f
        gridTop = gridYOffset
    }

    fun draw(canvas: Canvas, grid: GridState) {
        // Grid background - gradient panel with rounded corners and border
        val totalW = grid.width * cellSize + (grid.width + 1) * gap
        val totalH = grid.height * cellSize + (grid.height + 1) * gap
        val bgRect = RectF(gridLeft, gridTop, gridLeft + totalW, gridTop + totalH)

        gridBgPaint.shader = LinearGradient(
            bgRect.left, bgRect.top, bgRect.left, bgRect.bottom,
            Color.argb(130, 15, 15, 45),
            Color.argb(150, 5, 5, 20),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bgRect, 24f, 24f, gridBgPaint)
        canvas.drawRoundRect(bgRect, 24f, 24f, gridBorderPaint)

        // Pulsing selection timing
        val pulseTime = System.currentTimeMillis()
        val pulseAlpha = (180 + 75 * sin(pulseTime / 200.0)).toInt().coerceIn(0, 255)

        // Blocks
        for (row in 0 until grid.height) {
            for (col in 0 until grid.width) {
                val block = grid.get(row, col) ?: continue
                if (block.type == BlockType.EMPTY) continue

                val x = gridLeft + gap + col * (cellSize + gap)
                val y = gridTop + gap + row * (cellSize + gap)
                val rect = RectF(x, y, x + cellSize, y + cellSize)

                // Match animation: ease-out shrink with alpha fade
                val isMatching = (row to col) in matchingPositions
                var blockAlpha = 255
                if (isMatching) {
                    val easedProgress = 1f - (1f - matchAnimProgress).pow(2)
                    val scale = 1f - easedProgress * 0.6f
                    blockAlpha = ((1f - easedProgress) * 255).toInt().coerceIn(0, 255)
                    val cx = rect.centerX()
                    val cy = rect.centerY()
                    val hw = rect.width() / 2f * scale
                    val hh = rect.height() / 2f * scale
                    rect.set(cx - hw, cy - hh, cx + hw, cy + hh)

                    // White flash overlay at the start of animation
                    if (matchAnimProgress < 0.3f) {
                        val flashAlpha = ((1f - matchAnimProgress / 0.3f) * 180).toInt().coerceIn(0, 255)
                        matchFlashPaint.alpha = flashAlpha
                        canvas.drawRoundRect(rect, 8f, 8f, matchFlashPaint)
                    }
                }

                // Block shadow for 3D depth
                if (!isMatching || blockAlpha > 50) {
                    val shadowOffset = cellSize * 0.04f
                    val shadowRect = RectF(
                        rect.left + shadowOffset,
                        rect.top + shadowOffset,
                        rect.right + shadowOffset,
                        rect.bottom + shadowOffset
                    )
                    blockShadowPaint.alpha = if (isMatching) (blockAlpha * 0.35f).toInt() else 90
                    canvas.drawRoundRect(shadowRect, 8f, 8f, blockShadowPaint)
                }

                // Draw block bitmap
                val bmp = blockBitmaps[block.type]
                if (bmp != null) {
                    val bitmapPaint = if (blockAlpha < 255) {
                        Paint().apply { alpha = blockAlpha }
                    } else null
                    canvas.drawBitmap(bmp, null, rect, bitmapPaint)
                }

                // Selection highlight - pulsing glow
                if (row == selectedRow && col == selectedCol) {
                    val glowInset = -4f
                    val glowRect = RectF(
                        rect.left + glowInset, rect.top + glowInset,
                        rect.right - glowInset, rect.bottom - glowInset
                    )
                    selectionGlowPaint.alpha = (pulseAlpha * 0.35f).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(glowRect, 10f, 10f, selectionGlowPaint)

                    selectionStrokePaint.alpha = pulseAlpha
                    selectionStrokePaint.strokeWidth = 4f + 2f * sin(pulseTime / 200.0).toFloat()
                    canvas.drawRoundRect(rect, 8f, 8f, selectionStrokePaint)
                }
            }
        }
    }

    fun setSelection(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
    }

    fun clearSelection() {
        selectedRow = -1
        selectedCol = -1
    }

    fun setMatchAnimation(positions: Set<Pair<Int, Int>>, progress: Float) {
        matchingPositions = positions
        matchAnimProgress = progress
    }

    fun clearMatchAnimation() {
        matchingPositions = emptySet()
        matchAnimProgress = 0f
    }

    fun screenToGrid(designX: Float, designY: Float, grid: GridState): Pair<Int, Int>? {
        val relX = designX - gridLeft - gap
        val relY = designY - gridTop - gap
        if (relX < 0 || relY < 0) return null

        val col = (relX / (cellSize + gap)).toInt()
        val row = (relY / (cellSize + gap)).toInt()

        if (col !in 0 until grid.width || row !in 0 until grid.height) return null

        val cellRelX = relX - col * (cellSize + gap)
        val cellRelY = relY - row * (cellSize + gap)
        if (cellRelX > cellSize || cellRelY > cellSize) return null

        return row to col
    }

    fun getGridBottom(grid: GridState): Float {
        return gridTop + grid.height * cellSize + (grid.height + 1) * gap
    }

    fun cleanup() {
        blockBitmaps.values.forEach { it.recycle() }
    }
}
