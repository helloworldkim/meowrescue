package com.meowrescue.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class BattleHUD {

    private val hpBarBgPaint = Paint().apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private val hpBarPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private val hpBarBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val turnPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val chapterPaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        textSize = 28f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val pauseBgPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val pauseIconPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Pause button bounds (design coords)
    private var pauseBtnRect = RectF()

    fun draw(
        canvas: Canvas,
        designWidth: Float,
        playerHp: Int,
        playerMaxHp: Int,
        turnCount: Int,
        chapter: Int,
        stage: Int
    ) {
        val hudY = 40f
        val padding = 20f

        // Chapter / Stage indicator
        canvas.drawText("Chapter $chapter - Stage $stage", padding, hudY + 28f, chapterPaint)

        // Turn counter
        canvas.drawText("Turn $turnCount", designWidth / 2f, hudY + 28f, turnPaint)

        // Pause button (top right)
        val pauseSize = 50f
        val pauseX = designWidth - padding - pauseSize
        pauseBtnRect = RectF(pauseX, hudY, pauseX + pauseSize, hudY + pauseSize)
        canvas.drawRoundRect(pauseBtnRect, 8f, 8f, pauseBgPaint)
        // Draw pause icon (two vertical bars)
        val barW = 8f
        val barH = 24f
        val barY = pauseBtnRect.centerY() - barH / 2f
        canvas.drawRect(pauseBtnRect.centerX() - barW - 3f, barY, pauseBtnRect.centerX() - 3f, barY + barH, pauseIconPaint)
        canvas.drawRect(pauseBtnRect.centerX() + 3f, barY, pauseBtnRect.centerX() + barW + 3f, barY + barH, pauseIconPaint)

        // Player HP bar (bottom area)
        val hpBarY = 1840f
        val hpBarWidth = designWidth - padding * 2
        val hpBarHeight = 24f
        val hpBarRect = RectF(padding, hpBarY, padding + hpBarWidth, hpBarY + hpBarHeight)

        canvas.drawRoundRect(hpBarRect, 8f, 8f, hpBarBgPaint)

        val hpRatio = (playerHp.toFloat() / playerMaxHp).coerceIn(0f, 1f)
        hpBarPaint.color = when {
            hpRatio > 0.5f -> Color.parseColor("#4CAF50")
            hpRatio > 0.25f -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
        val hpFillRect = RectF(padding, hpBarY, padding + hpBarWidth * hpRatio, hpBarY + hpBarHeight)
        canvas.drawRoundRect(hpFillRect, 8f, 8f, hpBarPaint)
        canvas.drawRoundRect(hpBarRect, 8f, 8f, hpBarBorderPaint)

        // HP text
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("HP: $playerHp / $playerMaxHp", padding, hpBarY - 8f, textPaint)
    }

    fun isPauseTapped(designX: Float, designY: Float): Boolean {
        return pauseBtnRect.contains(designX, designY)
    }
}
