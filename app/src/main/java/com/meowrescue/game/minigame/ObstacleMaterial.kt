package com.meowrescue.game.minigame

import com.meowrescue.game.ui.Theme

enum class ObstacleMaterial(val maxHp: Int, val color: Int, val density: Float, val scoreValue: Int) {
    WOOD(30, Theme.LAUNCH_MAT_WOOD, 0.5f, 100),
    GLASS(15, Theme.LAUNCH_MAT_GLASS, 0.3f, 50),
    STONE(60, Theme.LAUNCH_MAT_STONE, 1.2f, 200),
    TNT(10, Theme.LAUNCH_MAT_TNT, 0.3f, 150)
}
