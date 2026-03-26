package com.meowrescue.game.ui.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.meowrescue.game.model.BlockType
import kotlin.math.sin
import kotlin.random.Random

class EffectRenderer {

    private data class FloatingText(
        val text: String,
        val x: Float,
        val startY: Float,
        val color: Int,
        val startTime: Long,
        val durationMs: Long = 800L,
        val isWeakness: Boolean = false,
        val isHeal: Boolean = false,
        val isCombo: Boolean = false,
        val elementType: BlockType? = null
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        val vx: Float,
        val vy: Float,
        val color: Int,
        val startTime: Long,
        val maxLife: Long,
        val size: Float
    )

    private val activeTexts = mutableListOf<FloatingText>()
    private val particles = mutableListOf<Particle>()
    private var screenShakeAmount = 0f

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

    private val particlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    fun addDamageNumber(x: Float, y: Float, damage: Int, isWeakness: Boolean = false, elementType: BlockType? = null) {
        val color = when {
            isWeakness -> Color.parseColor("#FFD700")
            else -> when (elementType) {
                BlockType.FIRE -> Color.parseColor("#FF6B35")
                BlockType.WATER -> Color.parseColor("#4FC3F7")
                BlockType.ATTACK -> Color.parseColor("#FF5252")
                else -> Color.WHITE
            }
        }
        val text = if (isWeakness) "$damage!" else "$damage"
        activeTexts.add(FloatingText(text, x, y, color, System.currentTimeMillis(), isWeakness = isWeakness, elementType = elementType))
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

    fun addComboText(x: Float, y: Float, chainLevel: Int) {
        val text = "x$chainLevel CHAIN!"
        activeTexts.add(FloatingText(
            text, x, y,
            Color.parseColor("#FFD700"),
            System.currentTimeMillis(),
            durationMs = 1200L,
            isCombo = true
        ))
    }

    fun triggerScreenShake(intensity: Float = 8f, comboLevel: Int = 0) {
        val scaledIntensity = intensity * (1f + comboLevel * 0.3f)
        screenShakeAmount = scaledIntensity * 0.85f
    }

    fun addElementBurst(x: Float, y: Float, elementType: BlockType, count: Int = 8) {
        val color = when (elementType) {
            BlockType.FIRE -> Color.parseColor("#FF6B35")
            BlockType.WATER -> Color.parseColor("#4FC3F7")
            BlockType.ATTACK -> Color.parseColor("#FF5252")
            BlockType.HEAL -> Color.parseColor("#44EE44")
            else -> Color.WHITE
        }
        val now = System.currentTimeMillis()
        repeat(count) {
            val angle = (it.toFloat() / count) * 2f * Math.PI.toFloat() + Random.nextFloat() * 0.5f
            val speed = 200f + Random.nextFloat() * 200f
            particles.add(Particle(
                x = x,
                y = y,
                vx = kotlin.math.cos(angle) * speed,
                vy = kotlin.math.sin(angle) * speed,
                color = color,
                startTime = now,
                maxLife = 600L,
                size = 8f + Random.nextFloat() * 8f
            ))
        }
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

        // Draw and update particles
        val particleIter = particles.iterator()
        while (particleIter.hasNext()) {
            val p = particleIter.next()
            val elapsed = (now - p.startTime).toFloat()
            if (elapsed >= p.maxLife) {
                particleIter.remove()
                continue
            }
            val progress = elapsed / p.maxLife
            val dt = elapsed / 1000f
            val px = p.x + p.vx * dt
            val py = p.y + p.vy * dt
            val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            val currentSize = p.size * (1f - progress * 0.5f)

            particlePaint.color = p.color
            particlePaint.alpha = alpha
            canvas.drawCircle(px, py, currentSize, particlePaint)
        }

        // Draw floating texts
        val textIter = activeTexts.iterator()
        while (textIter.hasNext()) {
            val ft = textIter.next()
            val elapsed = now - ft.startTime
            if (elapsed >= ft.durationMs) {
                textIter.remove()
                continue
            }
            val progress = elapsed.toFloat() / ft.durationMs

            if (ft.isCombo) {
                // Combo text: scale up dramatically then fade
                val scalePhase = if (progress < 0.2f) {
                    progress / 0.2f
                } else {
                    1f
                }
                val bounceY = if (progress < 0.3f) {
                    progress * (1f / 0.3f) * 60f
                } else if (progress < 0.5f) {
                    val p2 = (progress - 0.3f) / 0.2f
                    60f - p2 * 15f
                } else {
                    45f + (progress - 0.5f) / 0.5f * 40f
                }
                val y = ft.startY - bounceY
                val alpha = if (progress > 0.7f) {
                    ((1f - (progress - 0.7f) / 0.3f) * 255).toInt().coerceIn(0, 255)
                } else {
                    255
                }
                val textSize = 60f * (0.5f + scalePhase * 0.5f)

                damagePaint.color = ft.color
                damagePaint.alpha = alpha
                damagePaint.textSize = textSize

                outlinePaint.alpha = (alpha * 0.8f).toInt().coerceIn(0, 255)
                outlinePaint.textSize = textSize
                canvas.drawText(ft.text, ft.x, y, outlinePaint)
                canvas.drawText(ft.text, ft.x, y, damagePaint)
            } else {
                // Bounce: go up, slight dip, then continue up
                val bounceY = if (progress < 0.25f) {
                    progress * 4f * 40f
                } else if (progress < 0.4f) {
                    val p2 = (progress - 0.25f) / 0.15f
                    40f - p2 * 8f
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
    }

    fun hasActiveEffects(): Boolean {
        return activeTexts.isNotEmpty() || screenShakeAmount > 0.5f || particles.isNotEmpty()
    }

    fun clear() {
        activeTexts.clear()
        particles.clear()
        screenShakeAmount = 0f
    }
}
