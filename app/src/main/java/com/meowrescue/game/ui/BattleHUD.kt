package com.meowrescue.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.meowrescue.game.R
import com.meowrescue.game.model.Relic

class BattleHUD(context: Context, catSpriteResId: Int = R.drawable.cat_1) {

    // --- Cat portrait (uses existing high-quality cat_1~8 images) ---

    private val catPortrait: Bitmap = run {
        val raw = BitmapFactory.decodeResource(context.resources, catSpriteResId)
        Bitmap.createScaledBitmap(raw, 64, 64, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    private val portraitBorderPaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // --- HP section ---

    private val hpPanelPaint = Paint().apply {
        color = Color.argb(180, 20, 20, 30)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val hpBarBgPaint = Paint().apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val hpBarTopPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val hpBarBottomPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val hpBarBorderPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val hpTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val heartPaint = Paint().apply {
        color = Color.parseColor("#FF4466")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // --- Chapter/Stage banner ---

    private val bannerPaint = Paint().apply {
        color = Color.argb(180, 10, 10, 30)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val chapterPaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    // --- Turn counter badge ---

    private val turnBadgePaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val turnBadgeBorderPaint = Paint().apply {
        color = Color.parseColor("#FFA000")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val turnTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val turnLabelPaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        textSize = 16f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // --- Pause button ---

    private val pauseShadowPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pauseBgPaint = Paint().apply {
        color = Color.argb(140, 40, 40, 60)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pauseBorderPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val pauseIconPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // --- Shuffle indicator ---

    private val shuffleBgPaint = Paint().apply {
        color = Color.argb(160, 30, 30, 60)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val shuffleTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    private val shuffleActivePaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        textSize = 20f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        isFakeBoldText = true
    }

    // --- Relic slots ---

    private val relicSlotPaint = Paint().apply {
        color = Color.argb(60, 200, 200, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val relicSlotBorderPaint = Paint().apply {
        color = Color.argb(140, 200, 200, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }

    private val relicLabelPaint = Paint().apply {
        color = Color.argb(160, 200, 200, 255)
        textSize = 14f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    private var pauseBtnRect = RectF()

    fun draw(
        canvas: Canvas,
        designWidth: Float,
        playerHp: Int,
        playerMaxHp: Int,
        turnCount: Int,
        chapter: Int,
        stage: Int,
        shufflesRemaining: Int,
        relics: List<Relic>
    ) {
        val padding = 20f

        // ── Chapter / Stage banner (top center) – roguelike "Floor X-Y" format ──
        val bannerHeight = 52f
        val bannerRect = RectF(0f, 0f, designWidth, bannerHeight)
        canvas.drawRoundRect(
            RectF(bannerRect.left, bannerRect.top, bannerRect.right, bannerRect.bottom + 12f),
            0f, 0f, bannerPaint
        )
        // Rounded bottom corners only – draw over top part
        canvas.drawRoundRect(
            RectF(bannerRect.left, bannerRect.top - 12f, bannerRect.right, bannerRect.bottom + 12f),
            12f, 12f, bannerPaint
        )
        canvas.drawText(
            "Floor $chapter-$stage",
            designWidth / 2f,
            bannerHeight / 2f + 10f,
            chapterPaint
        )

        // ── Relic slots (below banner) ──
        val relicSlotSize = 28f
        val relicSlotSpacing = 10f
        val relicSlotsY = bannerHeight + 8f
        val totalRelicSlots = 3
        val relicSlotsStartX = padding

        canvas.drawText("Relics", relicSlotsStartX, relicSlotsY + relicSlotSize + 14f, relicLabelPaint)
        for (i in 0 until totalRelicSlots) {
            val slotLeft = relicSlotsStartX + i * (relicSlotSize + relicSlotSpacing)
            val slotRect = RectF(slotLeft, relicSlotsY, slotLeft + relicSlotSize, relicSlotsY + relicSlotSize)
            canvas.drawRoundRect(slotRect, 5f, 5f, relicSlotPaint)
            canvas.drawRoundRect(slotRect, 5f, 5f, relicSlotBorderPaint)
        }

        // ── Turn counter badge (right of banner area) ──
        val turnBadgeRadius = 26f
        val turnBadgeCx = designWidth - padding - 60f - turnBadgeRadius
        val turnBadgeCy = bannerHeight + 20f + turnBadgeRadius
        // shadow
        canvas.drawCircle(turnBadgeCx + 2f, turnBadgeCy + 2f, turnBadgeRadius, pauseShadowPaint)
        canvas.drawCircle(turnBadgeCx, turnBadgeCy, turnBadgeRadius, turnBadgePaint)
        canvas.drawCircle(turnBadgeCx, turnBadgeCy, turnBadgeRadius, turnBadgeBorderPaint)
        canvas.drawText("$turnCount", turnBadgeCx, turnBadgeCy + 8f, turnTextPaint)
        canvas.drawText("TURN", turnBadgeCx, turnBadgeCy + turnBadgeRadius + 16f, turnLabelPaint)

        // ── Shuffle indicator (below turn badge) ──
        val shuffleY = turnBadgeCy + turnBadgeRadius + 32f
        val shuffleRect = RectF(turnBadgeCx - turnBadgeRadius, shuffleY, turnBadgeCx + turnBadgeRadius, shuffleY + 28f)
        canvas.drawRoundRect(shuffleRect, 8f, 8f, shuffleBgPaint)
        val shuffleLabel = "SHF:$shufflesRemaining"
        val shufflePaint = if (shufflesRemaining > 0) shuffleActivePaint else shuffleTextPaint
        shufflePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(shuffleLabel, turnBadgeCx, shuffleY + 20f, shufflePaint)
        shufflePaint.textAlign = Paint.Align.LEFT

        // ── Pause button (top right) ──
        val pauseSize = 50f
        val pauseX = designWidth - padding - pauseSize
        val pauseY = 1f
        pauseBtnRect = RectF(pauseX, pauseY, pauseX + pauseSize, pauseY + pauseSize)

        // Shadow
        canvas.drawRoundRect(
            RectF(pauseBtnRect.left + 3f, pauseBtnRect.top + 3f, pauseBtnRect.right + 3f, pauseBtnRect.bottom + 3f),
            12f, 12f, pauseShadowPaint
        )
        canvas.drawRoundRect(pauseBtnRect, 12f, 12f, pauseBgPaint)
        canvas.drawRoundRect(pauseBtnRect, 12f, 12f, pauseBorderPaint)

        // Pause icon bars
        val barW = 7f
        val barH = 22f
        val barY = pauseBtnRect.centerY() - barH / 2f
        val barRect1 = RectF(pauseBtnRect.centerX() - barW - 3f, barY, pauseBtnRect.centerX() - 3f, barY + barH)
        val barRect2 = RectF(pauseBtnRect.centerX() + 3f, barY, pauseBtnRect.centerX() + barW + 3f, barY + barH)
        canvas.drawRoundRect(barRect1, 2f, 2f, pauseIconPaint)
        canvas.drawRoundRect(barRect2, 2f, 2f, pauseIconPaint)

        // ── Player HP bar (bottom area) ──
        val hpBarY = 1830f
        val hpBarHeight = 30f
        // Portrait is 64x64, placed at x=padding, vertically centered on the panel
        val portraitX = padding
        val portraitY = hpBarY - 38f
        val portraitSize = 64f
        val portraitRight = portraitX + portraitSize + 10f  // gap after portrait

        val hpBarWidth = designWidth - portraitRight - padding
        val panelPadding = 12f
        val panelRect = RectF(
            padding - panelPadding,
            hpBarY - 46f,
            padding + (portraitSize + 10f) + hpBarWidth + panelPadding,
            hpBarY + hpBarHeight + panelPadding + 4f
        )
        canvas.drawRoundRect(panelRect, 16f, 16f, hpPanelPaint)

        // Cat portrait (circular clip + gold border)
        val pCx = portraitX + portraitSize / 2f
        val pCy = portraitY + portraitSize / 2f
        val pRadius = portraitSize / 2f
        canvas.save()
        val clipPath = Path().apply { addCircle(pCx, pCy, pRadius, Path.Direction.CW) }
        canvas.clipPath(clipPath)
        canvas.drawBitmap(catPortrait, null, RectF(portraitX, portraitY, portraitX + portraitSize, portraitY + portraitSize), null)
        canvas.restore()
        canvas.drawCircle(pCx, pCy, pRadius, portraitBorderPaint)

        // Heart icon (shifted right of portrait)
        drawHeartIcon(canvas, portraitRight + 2f, hpBarY - 32f, 18f)

        // HP text (shifted right of portrait)
        hpTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("HP: $playerHp/$playerMaxHp", portraitRight + 26f, hpBarY - 14f, hpTextPaint)

        // Bar background
        val hpBarRect = RectF(portraitRight, hpBarY, portraitRight + hpBarWidth, hpBarY + hpBarHeight)
        canvas.drawRoundRect(hpBarRect, 12f, 12f, hpBarBgPaint)

        // HP fill – gradient style (brighter top, darker bottom)
        val hpRatio = (playerHp.toFloat() / playerMaxHp).coerceIn(0f, 1f)
        val (topColor, bottomColor) = when {
            hpRatio > 0.5f -> Pair(Color.parseColor("#66BB6A"), Color.parseColor("#2E7D32"))
            hpRatio > 0.25f -> Pair(Color.parseColor("#FFB74D"), Color.parseColor("#E65100"))
            else -> Pair(Color.parseColor("#EF5350"), Color.parseColor("#B71C1C"))
        }

        if (hpRatio > 0f) {
            val fillRight = portraitRight + hpBarWidth * hpRatio
            hpBarTopPaint.color = topColor
            canvas.drawRoundRect(
                RectF(portraitRight, hpBarY, fillRight, hpBarY + hpBarHeight),
                12f, 12f, hpBarTopPaint
            )
            hpBarBottomPaint.color = bottomColor
            canvas.drawRoundRect(
                RectF(portraitRight, hpBarY + hpBarHeight * 0.45f, fillRight, hpBarY + hpBarHeight),
                0f, 0f, hpBarBottomPaint
            )
        }

        // Border
        canvas.drawRoundRect(hpBarRect, 12f, 12f, hpBarBorderPaint)
    }

    private fun drawHeartIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path()
        val halfSize = size / 2f
        path.moveTo(x + halfSize, y + size * 0.9f)
        path.cubicTo(x - halfSize * 0.5f, y + halfSize * 0.5f, x - halfSize * 0.2f, y - halfSize * 0.6f, x + halfSize, y + halfSize * 0.2f)
        path.cubicTo(x + size + halfSize * 0.2f, y - halfSize * 0.6f, x + size + halfSize * 0.5f, y + halfSize * 0.5f, x + halfSize, y + size * 0.9f)
        path.close()
        canvas.drawPath(path, heartPaint)
    }

    fun isPauseTapped(designX: Float, designY: Float): Boolean {
        return pauseBtnRect.contains(designX, designY)
    }

    fun cleanup() {
        catPortrait.recycle()
    }
}
