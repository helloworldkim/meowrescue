package com.meowrescue.game.ui.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin

class EffectRenderer {

    private data class FloatingText(
        val text: String,
        val x: Float,
        val startY: Float,
        val color: Int,
        val startTime: Long,
        val durationMs: Long = 800L,
        val isWeakness: Boolean = false,
        val isHeal: Boolean = false
    )

    private val activeTexts = mutableListOf<FloatingText>()
    private var screenShakeAmount = 0f
    private var screenShakeDecay = 0f

    private val damagePaint = Paint().apply {
        textSize = 44f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val outlinePaint = Paint().apply {
        textSize = 44f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.argb(200, 0, 0, 0)
    }

    private val healOutlinePaint = Paint().apply {
        textSize = 44f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.argb(180, 255, 255, 255)
    }

    fun addDamageNumber(x: Float, y: Float, damage: Int, isWeakness: Boolean = false) {
        val color = if (isWeakness) Color.parseColor("#FFD700") else Color.WHITE
        val text = if (isWeakness) "$damage!" else "$damage"
        activeTexts.add(FloatingText(text, x, y, color, System.currentTimeMillis(), isWeakness = isWeakness))
    }

    fun addHealNumber(x: Float, y: Float, amount: Int) {
        activeTexts.add(FloatingText(
            "+$amount",
            x, y,
            Color.parseColor("#44EE44"),
            System.currentTimeMillis(),
            isHeal = true
        ))
    }

    fun triggerScreenShake(intensity: Float = 8f) {
        screenShakeAmount = intensity * 0.85f
        screenShakeDecay = intensity * 0.85f
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
        screenShakeAmount *= 0.80f
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

            // Bounce: go up, slight dip, then continue up
            val bounceY = if (progress < 0.25f) {
                progress * 4f * 40f
            } else if (progress < 0.4f) {
                val p = (progress - 0.25f) / 0.15f
                40f - p * 8f
            } else {
                32f + (progress - 0.4f) / 0.6f * 36f
            }
            val y = ft.startY - bounceY

            val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            val baseSize = if (ft.isWeakness) 52f else 44f
            val scale = 1f + progress * 0.3f

            val textSize = baseSize * scale
            damagePaint.color = ft.color
            damagePaint.alpha = alpha
            damagePaint.textSize = textSize

            if (ft.isHeal) {
                // Heal: white outline for readability on dark backgrounds
                healOutlinePaint.alpha = (alpha * 0.7f).toInt().coerceIn(0, 255)
                healOutlinePaint.textSize = textSize
                canvas.drawText(ft.text, ft.x, y, healOutlinePaint)
            } else {
                // Damage: black outline for readability
                outlinePaint.alpha = (alpha * 0.8f).toInt().coerceIn(0, 255)
                outlinePaint.textSize = textSize
                canvas.drawText(ft.text, ft.x, y, outlinePaint)
            }

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
