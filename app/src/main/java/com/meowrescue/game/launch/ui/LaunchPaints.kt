package com.meowrescue.game.launch.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.meowrescue.game.ui.Theme

class LaunchPaints(private val density: Float) {

    val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_GROUND
    }
    val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_GRASS
    }
    val slingshotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_SLINGSHOT; style = Paint.Style.STROKE
        strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_BAND; style = Paint.Style.STROKE
        strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    val obstaclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    val enemyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_ENEMY
    }
    val enemyEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    val enemyPupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_HUD_SHADOW
    }
    val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val overlayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x00000000
    }
    val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.INT_CORAL
    }
    val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val explosionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_HUD_ORANGE
    }
    val debrisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val catQueueBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
    }
    val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_STAR_GOLD
    }
    val starEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_ABILITY_NORMAL
    }
    val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val catFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.INT_CORAL
    }
    val overlayTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val overlayInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_LIGHT_GRAY; textAlign = Paint.Align.CENTER
    }
    val retryButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_BLUE_GRAY
    }
    val menuButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_BLUE_GRAY
    }
    val slingshotBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_SLINGSHOT; style = Paint.Style.FILL
    }
    val abilityLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val abilityBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val tntMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
    }
    val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.LAUNCH_STAR_GOLD; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }
}
