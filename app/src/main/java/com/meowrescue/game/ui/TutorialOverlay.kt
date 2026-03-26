package com.meowrescue.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class TutorialOverlay(context: Context) {

    private val prefs = context.getSharedPreferences("meow_rescue", Context.MODE_PRIVATE)

    private val dimPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val boxPaint = Paint().apply {
        color = Color.argb(230, 40, 40, 60)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val titlePaint = Paint().apply {
        color = Color.parseColor("#FFD54F")
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val hintPaint = Paint().apply {
        color = Color.argb(180, 200, 200, 200)
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val blockColorPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val blockLabelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }

    data class TutorialStep(
        val title: String,
        val lines: List<String>
    )

    private val steps = listOf(
        TutorialStep(
            "Welcome!",
            listOf(
                "Rescue cats by defeating enemies!",
                "Match blocks to attack and heal."
            )
        ),
        TutorialStep(
            "How to Swap",
            listOf(
                "Tap a block to select it.",
                "Then tap an adjacent block to swap.",
                "If 3+ same blocks line up, they match!"
            )
        ),
        TutorialStep(
            "Block Types",
            listOf(
                "ATTACK (Red) - Physical damage",
                "FIRE (Orange) - Fire damage",
                "WATER (Blue) - Water damage",
                "HEAL (Green) - Restore your HP"
            )
        ),
        TutorialStep(
            "Bigger = Better!",
            listOf(
                "Match 4+ blocks for bonus damage!",
                "Chain combos give +25% per chain."
            )
        ),
        TutorialStep(
            "Weaknesses",
            listOf(
                "Enemies have elemental weaknesses.",
                "Hit weakness: 1.5x damage!",
                "Hit resistance: 0.5x damage."
            )
        ),
        TutorialStep(
            "Ready?",
            listOf(
                "Defeat all enemies to clear the stage!",
                "Good luck rescuing the cats!"
            )
        )
    )

    var currentStep = 0
        private set

    private var enabled = false

    val isActive: Boolean get() = enabled && !isCompleted() && currentStep < steps.size

    fun init(chapter: Int, stage: Int) {
        enabled = shouldShow(chapter, stage)
    }

    fun advance(): Boolean {
        currentStep++
        if (currentStep >= steps.size) {
            markCompleted()
            return false
        }
        return true
    }

    fun draw(canvas: Canvas, designWidth: Float, designHeight: Float) {
        if (!isActive) return
        val step = steps[currentStep]

        canvas.drawRect(0f, 0f, designWidth, designHeight, dimPaint)

        val centerX = designWidth / 2f
        val centerY = designHeight / 2f

        val boxH = 120f + step.lines.size * 44f
        val boxRect = RectF(centerX - 380f, centerY - boxH / 2f, centerX + 380f, centerY + boxH / 2f)
        canvas.drawRoundRect(boxRect, 24f, 24f, boxPaint)

        var y = boxRect.top + 60f
        canvas.drawText(step.title, centerX, y, titlePaint)
        y += 50f

        for (line in step.lines) {
            canvas.drawText(line, centerX, y, textPaint)
            y += 44f
        }

        canvas.drawText("Tap to continue", centerX, boxRect.bottom - 16f, hintPaint)
    }

    private fun isCompleted(): Boolean {
        return prefs.getBoolean("tutorial_completed", false)
    }

    private fun markCompleted() {
        prefs.edit().putBoolean("tutorial_completed", true).apply()
    }

    fun shouldShow(chapter: Int, stage: Int): Boolean {
        return chapter == 1 && stage == 1 && !isCompleted()
    }
}
