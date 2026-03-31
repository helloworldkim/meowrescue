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
    const val COLOR_ENDLESS_PURPLE = "#9575CD"
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

    // Shared Int color constants (for Canvas/Paint usage)
    const val INT_BG_CREAM     = 0xFFFFF8F0.toInt()     // = COLOR_CREAM
    const val INT_WARM_BROWN   = 0xFF4E342E.toInt()     // = COLOR_WARM_BROWN
    const val INT_CORAL        = 0xFFFF7043.toInt()     // = COLOR_CORAL
    const val INT_TEAL         = 0xFF26A69A.toInt()     // = COLOR_TEAL
    const val INT_GRAY         = 0xFF9E9E9E.toInt()

    // ── Cat Launch 미니게임 ─────────────────────────────────────────
    // 배경
    const val LAUNCH_SKY_TOP       = 0xFF87CEEB.toInt()  // 하늘 상단 (스카이블루)
    const val LAUNCH_SKY_BOTTOM    = 0xFFF0F8FF.toInt()  // 하늘 하단 (앨리스블루)
    const val LAUNCH_GROUND        = 0xFF8D6E63.toInt()  // 지면 (브라운)
    const val LAUNCH_GRASS         = 0xFF4CAF50.toInt()  // 잔디 (그린)
    // 새총
    const val LAUNCH_SLINGSHOT     = 0xFF5D4037.toInt()  // 새총 프레임 (다크브라운)
    const val LAUNCH_BAND          = 0xFF37474F.toInt()  // 새총 밴드 (차콜)
    // 적
    const val LAUNCH_ENEMY         = 0xFF66BB6A.toInt()  // 적 캐릭터 (그린)
    // 재료
    const val LAUNCH_MAT_WOOD      = 0xFFA1887F.toInt()  // 나무 (탄)
    const val LAUNCH_MAT_GLASS     = 0xFF80DEEA.toInt()  // 유리 (시안)
    const val LAUNCH_MAT_STONE     = 0xFF90A4AE.toInt()  // 돌 (블루그레이)
    // 능력 색상
    const val LAUNCH_ABILITY_NORMAL    = 0xFF9E9E9E.toInt()  // 일반 (회색)
    const val LAUNCH_ABILITY_REDIRECT  = 0xFF42A5F5.toInt()  // 방향전환 (블루)
    const val LAUNCH_ABILITY_SPLIT     = 0xFF66BB6A.toInt()  // 분열 (그린)
    const val LAUNCH_ABILITY_EXPLOSIVE = 0xFFEF5350.toInt()  // 폭발 (레드)
    const val LAUNCH_ABILITY_CHARGE    = 0xFFFF7043.toInt()  // 돌진 (코랄)
    // HUD & 오버레이
    const val LAUNCH_HUD_SHADOW    = 0xCC000000.toInt()  // HUD 배경 (80% 블랙)
    const val LAUNCH_HUD_ORANGE    = 0xFFFF9800.toInt()  // HUD 주황 (능력 태그)
    const val LAUNCH_STAR_GOLD     = 0xFFFFD600.toInt()  // 별/골드
    const val LAUNCH_LIGHT_GRAY    = 0xFFE0E0E0.toInt()  // 연회색 텍스트
    const val LAUNCH_BLUE_GRAY     = 0xFF78909C.toInt()  // 블루그레이 (부제)
    // 축하 파티클
    val LAUNCH_CONFETTI = intArrayOf(
        0xFFFF7043.toInt(),  // 코랄
        0xFFFFD600.toInt(),  // 골드
        0xFF66BB6A.toInt(),  // 그린
        0xFF4FC3F7.toInt(),  // 라이트블루
        0xFFCE93D8.toInt(),  // 퍼플
        0xFFF48FB1.toInt(),  // 핑크
    )
    // 난이도 다이얼로그
    const val LAUNCH_DIFF_EASY     = 0xFF66BB6A.toInt()  // 쉬움 (그린)
    const val LAUNCH_DIFF_NORMAL   = 0xFFFFA726.toInt()  // 보통 (앰버)
    const val LAUNCH_DIFF_HARD     = 0xFFEF5350.toInt()  // 어려움 (레드)

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
