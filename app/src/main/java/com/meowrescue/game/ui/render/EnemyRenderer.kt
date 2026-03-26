package com.meowrescue.game.ui.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.meowrescue.game.model.Enemy

class EnemyRenderer(private val context: Context) {

    private val enemySprites = mutableMapOf<Int, Bitmap>()

    private val hpBarBgPaint = Paint().apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private val hpBarPaint = Paint().apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.FILL
    }

    private val hpBarBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val namePaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val hpTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // Attack animation state
    private var attackingEnemyId: String? = null
    private var attackAnimProgress = 0f

    private fun getSprite(resId: Int): Bitmap {
        return enemySprites.getOrPut(resId) {
            val raw = BitmapFactory.decodeResource(context.resources, resId)
            Bitmap.createScaledBitmap(raw, 120, 160, true).also {
                if (it !== raw) raw.recycle()
            }
        }
    }

    fun draw(canvas: Canvas, enemies: List<Enemy>, areaTop: Float, areaWidth: Float) {
        val aliveEnemies = enemies.filter { it.isAlive }
        if (aliveEnemies.isEmpty()) return

        val spacing = areaWidth / (aliveEnemies.size + 1)

        for ((index, enemy) in aliveEnemies.withIndex()) {
            val cx = spacing * (index + 1)
            val cy = areaTop + 100f

            // Attack animation: shake
            var drawX = cx
            if (enemy.id == attackingEnemyId) {
                val shake = (kotlin.math.sin(attackAnimProgress * Math.PI * 6) * 10f).toFloat()
                drawX += shake
            }

            // Enemy sprite
            val sprite = getSprite(enemy.spriteResId)
            canvas.drawBitmap(
                sprite,
                drawX - sprite.width / 2f,
                cy - sprite.height / 2f,
                null
            )

            // Name
            canvas.drawText(enemy.name, drawX, cy + sprite.height / 2f + 30f, namePaint)

            // HP bar
            val hpBarWidth = 100f
            val hpBarHeight = 12f
            val hpBarLeft = drawX - hpBarWidth / 2f
            val hpBarTop = cy + sprite.height / 2f + 40f
            val hpBarRect = RectF(hpBarLeft, hpBarTop, hpBarLeft + hpBarWidth, hpBarTop + hpBarHeight)

            canvas.drawRoundRect(hpBarRect, 4f, 4f, hpBarBgPaint)

            val hpRatio = (enemy.currentHp.toFloat() / enemy.maxHp).coerceIn(0f, 1f)
            val hpFillRect = RectF(hpBarLeft, hpBarTop, hpBarLeft + hpBarWidth * hpRatio, hpBarTop + hpBarHeight)
            // Color changes based on HP
            hpBarPaint.color = when {
                hpRatio > 0.5f -> Color.parseColor("#4CAF50")
                hpRatio > 0.25f -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            }
            canvas.drawRoundRect(hpFillRect, 4f, 4f, hpBarPaint)
            canvas.drawRoundRect(hpBarRect, 4f, 4f, hpBarBorderPaint)

            // HP text
            canvas.drawText(
                "${enemy.currentHp}/${enemy.maxHp}",
                drawX,
                hpBarTop + hpBarHeight + 24f,
                hpTextPaint
            )
        }
    }

    fun setAttackAnimation(enemyId: String, progress: Float) {
        attackingEnemyId = enemyId
        attackAnimProgress = progress
    }

    fun clearAttackAnimation() {
        attackingEnemyId = null
        attackAnimProgress = 0f
    }

    fun cleanup() {
        enemySprites.values.forEach { it.recycle() }
        enemySprites.clear()
    }
}
