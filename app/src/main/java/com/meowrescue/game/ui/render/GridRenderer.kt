package com.meowrescue.game.ui.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.meowrescue.game.R
import com.meowrescue.game.model.BlockType
import com.meowrescue.game.model.GridState
import com.meowrescue.game.util.GridConstants

class GridRenderer(context: Context) {

    private val blockBitmaps = mapOf(
        BlockType.ATTACK to loadScaled(context, R.drawable.block_attack, 128, 128),
        BlockType.FIRE to loadScaled(context, R.drawable.block_fire, 128, 128),
        BlockType.WATER to loadScaled(context, R.drawable.block_water, 128, 128),
        BlockType.HEAL to loadScaled(context, R.drawable.block_heal, 128, 128)
    )

    private val gridBgPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }

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
        // Grid background
        val totalW = grid.width * cellSize + (grid.width + 1) * gap
        val totalH = grid.height * cellSize + (grid.height + 1) * gap
        val bgRect = RectF(gridLeft, gridTop, gridLeft + totalW, gridTop + totalH)
        canvas.drawRoundRect(bgRect, 20f, 20f, gridBgPaint)

        // Blocks
        for (row in 0 until grid.height) {
            for (col in 0 until grid.width) {
                val block = grid.get(row, col) ?: continue
                if (block.type == BlockType.EMPTY) continue

                val x = gridLeft + gap + col * (cellSize + gap)
                val y = gridTop + gap + row * (cellSize + gap)
                val rect = RectF(x, y, x + cellSize, y + cellSize)

                // Match animation: scale down matching blocks
                val isMatching = (row to col) in matchingPositions
                if (isMatching) {
                    val scale = 1f - matchAnimProgress * 0.5f
                    val cx = rect.centerX()
                    val cy = rect.centerY()
                    val hw = rect.width() / 2f * scale
                    val hh = rect.height() / 2f * scale
                    rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
                }

                val bmp = blockBitmaps[block.type]
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, rect, null)
                }
            }
        }
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
