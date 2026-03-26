package com.meowrescue.game.ui.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.meowrescue.game.model.BlockType
import com.meowrescue.game.model.Enemy

class EnemyRenderer(private val context: Context) {

    private val enemySprites = mutableMapOf<Int, Bitmap>()

    // Card panel behind each enemy
    private val cardPaint = Paint().apply {
        color = Color.argb(140, 20, 20, 40)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cardBorderPaint = Paint().apply {
        color = Color.argb(80, 180, 180, 220)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // Name label
    private val nameBannerPaint = Paint().apply {
        color = Color.argb(180, 30, 30, 50)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val namePaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    // HP bar
    private val hpBarBgPaint = Paint().apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val hpBarFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val hpBarTopPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val hpBarBorderPaint = Paint().apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val hpTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    // Weakness / resistance info
    private val infoPaint = Paint().apply {
        color = Color.WHITE
        textSize = 16f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    private val infoDotPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Hit flash overlay
    private val hitFlashPaint = Paint().apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Attack animation state
    private var attackingEnemyId: String? = null
    private var attackAnimProgress = 0f

    // Damage flash state
    private var damagedEnemyId: String? = null
    private var damageFlashProgress = 0f

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

            val sprite = getSprite(enemy.spriteResId)
            val spriteHalfW = sprite.width / 2f
            val spriteHalfH = sprite.height / 2f

            // ── Card panel ──
            val cardPadding = 16f
            val cardRect = RectF(
                drawX - spriteHalfW - cardPadding,
                cy - spriteHalfH - 40f,
                drawX + spriteHalfW + cardPadding,
                cy + spriteHalfH + 100f
            )
            canvas.drawRoundRect(cardRect, 14f, 14f, cardPaint)
            canvas.drawRoundRect(cardRect, 14f, 14f, cardBorderPaint)

            // ── Name banner (above sprite) ──
            val nameWidth = namePaint.measureText(enemy.name) + 24f
            val nameBannerRect = RectF(
                drawX - nameWidth / 2f,
                cy - spriteHalfH - 34f,
                drawX + nameWidth / 2f,
                cy - spriteHalfH - 8f
            )
            canvas.drawRoundRect(nameBannerRect, 8f, 8f, nameBannerPaint)
            canvas.drawText(enemy.name, drawX, cy - spriteHalfH - 14f, namePaint)

            // ── Enemy sprite ──
            canvas.drawBitmap(
                sprite,
                drawX - spriteHalfW,
                cy - spriteHalfH,
                null
            )

            // ── Hit flash overlay ──
            if (enemy.id == damagedEnemyId && damageFlashProgress > 0f) {
                val flashAlpha = (180 * (1f - damageFlashProgress)).toInt().coerceIn(0, 255)
                hitFlashPaint.color = Color.argb(flashAlpha, 255, 255, 255)
                canvas.drawRoundRect(
                    RectF(drawX - spriteHalfW, cy - spriteHalfH, drawX + spriteHalfW, cy + spriteHalfH),
                    8f, 8f, hitFlashPaint
                )
            }

            // ── HP bar ──
            val hpBarWidth = 120f
            val hpBarHeight = 14f
            val hpBarLeft = drawX - hpBarWidth / 2f
            val hpBarTop = cy + spriteHalfH + 10f
            val hpBarRect = RectF(hpBarLeft, hpBarTop, hpBarLeft + hpBarWidth, hpBarTop + hpBarHeight)

            canvas.drawRoundRect(hpBarRect, 6f, 6f, hpBarBgPaint)

            val hpRatio = (enemy.currentHp.toFloat() / enemy.maxHp).coerceIn(0f, 1f)
            if (hpRatio > 0f) {
                val (topColor, bottomColor) = when {
                    hpRatio > 0.5f -> Pair(Color.parseColor("#66BB6A"), Color.parseColor("#2E7D32"))
                    hpRatio > 0.25f -> Pair(Color.parseColor("#FFB74D"), Color.parseColor("#E65100"))
                    else -> Pair(Color.parseColor("#EF5350"), Color.parseColor("#B71C1C"))
                }
                val fillRight = hpBarLeft + hpBarWidth * hpRatio

                // Bottom (darker) layer – full bar
                hpBarFillPaint.color = bottomColor
                canvas.drawRoundRect(
                    RectF(hpBarLeft, hpBarTop, fillRight, hpBarTop + hpBarHeight),
                    6f, 6f, hpBarFillPaint
                )
                // Top (brighter) layer – upper half
                hpBarTopPaint.color = topColor
                canvas.drawRoundRect(
                    RectF(hpBarLeft, hpBarTop, fillRight, hpBarTop + hpBarHeight * 0.55f),
                    6f, 6f, hpBarTopPaint
                )
            }

            canvas.drawRoundRect(hpBarRect, 6f, 6f, hpBarBorderPaint)

            // ── HP text ──
            canvas.drawText(
                "${enemy.currentHp}/${enemy.maxHp}",
                drawX,
                hpBarTop + hpBarHeight + 20f,
                hpTextPaint
            )

            // ── Weakness / Resistance indicators ──
            var infoY = hpBarTop + hpBarHeight + 38f
            val dotRadius = 7f
            val dotTextGap = 4f
            val infoLineHeight = 22f

            enemy.weakness?.let { weak ->
                val dotColor = blockTypeColor(weak)
                val label = "Weak: ${weak.name}"
                val totalWidth = dotRadius * 2 + dotTextGap + infoPaint.measureText(label)
                val startX = drawX - totalWidth / 2f
                infoDotPaint.color = dotColor
                canvas.drawCircle(startX + dotRadius, infoY - dotRadius * 0.4f, dotRadius, infoDotPaint)
                infoPaint.color = Color.parseColor("#FFDD44")
                canvas.drawText(label, startX + dotRadius * 2 + dotTextGap, infoY, infoPaint)
                infoY += infoLineHeight
            }

            enemy.resistance?.let { resist ->
                val dotColor = blockTypeColor(resist)
                val label = "Resist: ${resist.name}"
                val totalWidth = dotRadius * 2 + dotTextGap + infoPaint.measureText(label)
                val startX = drawX - totalWidth / 2f
                infoDotPaint.color = dotColor
                canvas.drawCircle(startX + dotRadius, infoY - dotRadius * 0.4f, dotRadius, infoDotPaint)
                infoPaint.color = Color.parseColor("#AADDFF")
                canvas.drawText(label, startX + dotRadius * 2 + dotTextGap, infoY, infoPaint)
            }
        }
    }

    private fun blockTypeColor(type: BlockType): Int = when (type) {
        BlockType.FIRE -> Color.parseColor("#FF6B35")
        BlockType.WATER -> Color.parseColor("#4FC3F7")
        BlockType.ATTACK -> Color.parseColor("#FF5252")
        BlockType.HEAL -> Color.parseColor("#66BB6A")
        BlockType.EMPTY -> Color.GRAY
    }

    fun setAttackAnimation(enemyId: String, progress: Float) {
        attackingEnemyId = enemyId
        attackAnimProgress = progress
    }

    fun clearAttackAnimation() {
        attackingEnemyId = null
        attackAnimProgress = 0f
    }

    fun setDamageFlash(enemyId: String, progress: Float) {
        damagedEnemyId = enemyId
        damageFlashProgress = progress
    }

    fun clearDamageFlash() {
        damagedEnemyId = null
        damageFlashProgress = 0f
    }

    fun cleanup() {
        enemySprites.values.forEach { it.recycle() }
        enemySprites.clear()
    }
}
