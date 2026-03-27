package com.meowrescue.game.ui

import android.graphics.Color

object Theme {
    const val COLOR_BACKGROUND = "#FFF9FB"
    const val COLOR_BACKGROUND_GAME = "#FFF5E6"
    const val COLOR_TOOLBAR = "#FF85A1"
    const val COLOR_TITLE_TEXT = "#E0246A"
    const val COLOR_PRIMARY_TEXT = "#333333"
    const val COLOR_SECONDARY_TEXT = "#555555"
    const val COLOR_MUTED_TEXT = "#999999"
    const val COLOR_LEVEL_UNLOCKED = "#2ECC71"
    const val COLOR_LEVEL_LOCKED = "#CCCCCC"
    const val COLOR_BUTTON_COLLECTION = "#9B59B6"
    const val COLOR_BUTTON_BACK = "#FF7043"
    const val COLOR_RARITY_LEGENDARY = "#FF85A1"
    const val COLOR_RARITY_RARE = "#9B59B6"
    const val COLOR_RARITY_COMMON = "#2ECC71"
    const val COLOR_CARD_BACKGROUND = "#FFFFFF"

    // Pastel + Warm Accent palette
    const val COLOR_CREAM = "#FFF8F0"
    const val COLOR_LAVENDER = "#EDE7F6"
    const val COLOR_WARM_BROWN = "#4E342E"
    const val COLOR_CORAL = "#FF7043"
    const val COLOR_TEAL = "#26A69A"
    const val COLOR_STAR_GOLD = "#FFD600"
    const val COLOR_LOCKED_GRAY = "#BDBDBD"
    const val COLOR_LEVEL_COMPLETED_BG = "#FFF3E0"
    const val COLOR_LEVEL_PLAYABLE_BG = "#E8F5E9"

    // UI panel colors
    val COLOR_GOLD = 0xFFFFD700.toInt()
    val COLOR_PANEL_BG = Color.argb(200, 20, 20, 40)
    val COLOR_PANEL_BORDER = Color.argb(120, 255, 255, 255)

    // HP bar colors
    val COLOR_HP_HIGH = 0xFF4CAF50.toInt()
    val COLOR_HP_MED = 0xFFFF9800.toInt()
    val COLOR_HP_LOW = 0xFFFF4444.toInt()

    // Particle pastel colors for celebrations
    val PARTICLE_COLORS = intArrayOf(
        0xFFFFB3BA.toInt(),  // pastel pink
        0xFFBAE1FF.toInt(),  // pastel blue
        0xFFBAFFBA.toInt(),  // pastel green
        0xFFFFFFBA.toInt(),  // pastel yellow
        0xFFE8BAFF.toInt(),  // pastel purple
        0xFFFFD6BA.toInt(),  // pastel orange
    )

    // Battle colors
    const val COLOR_HP_BAR = "#4CAF50"
    const val COLOR_HP_BAR_BG = "#333333"
    const val COLOR_BLOCK_ATTACK = "#FF4444"
    const val COLOR_BLOCK_FIRE = "#FF7043"
    const val COLOR_BLOCK_WATER = "#2196F3"
    const val COLOR_BLOCK_HEAL = "#2ECC71"
    const val COLOR_DAMAGE_TEXT = "#FF4444"
    const val COLOR_HEAL_TEXT = "#2ECC71"
}
