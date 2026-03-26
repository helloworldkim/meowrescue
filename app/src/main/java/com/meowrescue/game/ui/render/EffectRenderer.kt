package com.meowrescue.game.ui.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class EffectRenderer {

    private data class FloatingText(
        val text: String,
        val x: Float,
        val startY: Float,
        val color: Int,
        val startTime: Long,
        val durationMs: Long = 800L
    )

    private val activeTexts = mutableListOf<FloatingText>()
    private var screenShakeAmount = 0f
    private var screenShakeDecay = 0f

    private val damagePaint = Paint().apply {
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun addDamageNumber(x: Float, y: Float, damage: Int, isWeakness: Boolean = false) {
        val color = if (isWeakness) Color.parseColor("#FF6B6B") else Color.WHITE
        val text = if (isWeakness) "$damage!" else "$damage"
        activeTexts.add(FloatingText(text, x, y, color, System.currentTimeMillis()))
    }

    fun addHealNumber(x: Float, y: Float, amount: Int) {
        activeTexts.add(FloatingText(
            "+$amount",
            x, y,
            Color.parseColor("#5CD85C"),
            System.currentTimeMillis()
        ))
    }

    fun triggerScreenShake(intensity: Float = 8f) {
        screenShakeAmount = intensity
        screenShakeDecay = intensity
    }

    /**
     * Apply screen shake to the canvas. Call before drawing other elements.
     * Returns the shake offset applied (for restoring later).
     */
    fun applyScreenShake(canvas: Canvas): Pair<Float, Float> {
        if (screenShakeAmount <= 0.5f) {
            screenShakeAmount = 0f
            return 0f to 0f
        }
        val dx = (Math.random() * 2 - 1).toFloat() * screenShakeAmount
        val dy = (Math.random() * 2 - 1).toFloat() * screenShakeAmount
        canvas.translate(dx, dy)
        screenShakeAmount *= 0.85f
        return dx to dy
    }

    fun draw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val iter = activeTexts.iterator()
        while (iter.hasNext()) {
            val ft = iter.next()
            val elapsed = now - ft.startTime
            if (elapsed >= ft.durationMs) {
                iter.remove()
                continue
            }
            val progress = elapsed.toFloat() / ft.durationMs
            val y = ft.startY - progress * 60f
            val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            val scale = 1f + progress * 0.3f

            damagePaint.color = ft.color
            damagePaint.alpha = alpha
            damagePaint.textSize = 40f * scale
            canvas.drawText(ft.text, ft.x, y, damagePaint)
        }
    }

    fun hasActiveEffects(): Boolean {
        return activeTexts.isNotEmpty() || screenShakeAmount > 0.5f
    }

    fun clear() {
        activeTexts.clear()
        screenShakeAmount = 0f
    }
}
