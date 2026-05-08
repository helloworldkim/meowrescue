package com.meowrescue.game.puzzle.model

/**
 * Defines the visual theme for each world (background, block palette, BGM key).
 * Stage numbers map to worlds via [forStage].
 */
data class WorldTheme(
    val worldIndex: Int,
    val name: String,
    val bgTop: Int,
    val bgBottom: Int,
    val gridBg: Int,
    val cellLine: Int,
    val blockColors: IntArray,
    val bgmKey: String
) {
    companion object {

        private val THEMES = listOf(
            // 1: 정원 (Garden) — stages 1-30
            WorldTheme(0, "정원", 0xFFFFF8F0.toInt(), 0xFFE8F5E9.toInt(),
                0xFFEFEBE9.toInt(), 0xFFD7CCC8.toInt(),
                intArrayOf(
                    0xFF78909C.toInt(), 0xFF81C784.toInt(), 0xFFFFD54F.toInt(),
                    0xFFCE93D8.toInt(), 0xFFF48FB1.toInt(), 0xFF4FC3F7.toInt()
                ), "beginner"),

            // 2: 해변 (Beach) — stages 31-60
            WorldTheme(1, "해변", 0xFF87CEEB.toInt(), 0xFFFFF3E0.toInt(),
                0xFFFFF8E1.toInt(), 0xFFFFE0B2.toInt(),
                intArrayOf(
                    0xFFFF8A65.toInt(), 0xFFFFB74D.toInt(), 0xFF4DD0E1.toInt(),
                    0xFFFFF176.toInt(), 0xFFAED581.toInt(), 0xFFFFAB91.toInt()
                ), "beginner"),

            // 3: 숲 (Forest) — stages 61-90
            WorldTheme(2, "숲", 0xFFC8E6C9.toInt(), 0xFF3E2723.toInt(),
                0xFFA5D6A7.toInt(), 0xFF81C784.toInt(),
                intArrayOf(
                    0xFF66BB6A.toInt(), 0xFF8D6E63.toInt(), 0xFFFFF176.toInt(),
                    0xFFA1887F.toInt(), 0xFF4CAF50.toInt(), 0xFFBCAAA4.toInt()
                ), "intermediate"),

            // 4: 눈 (Snow) — stages 91-120
            WorldTheme(3, "눈", 0xFFE3F2FD.toInt(), 0xFFBBDEFB.toInt(),
                0xFFE1F5FE.toInt(), 0xFFB3E5FC.toInt(),
                intArrayOf(
                    0xFF90CAF9.toInt(), 0xFFB0BEC5.toInt(), 0xFFE0E0E0.toInt(),
                    0xFF80DEEA.toInt(), 0xFFCE93D8.toInt(), 0xFFFFFFFF.toInt()
                ), "intermediate"),

            // 5: 화산 (Volcano) — stages 121-150
            WorldTheme(4, "화산", 0xFF4E342E.toInt(), 0xFFBF360C.toInt(),
                0xFF5D4037.toInt(), 0xFF795548.toInt(),
                intArrayOf(
                    0xFFFF5722.toInt(), 0xFFFF9800.toInt(), 0xFF795548.toInt(),
                    0xFFE64A19.toInt(), 0xFFFFC107.toInt(), 0xFF424242.toInt()
                ), "advanced"),

            // 6: 우주 (Space) — stages 151-180
            WorldTheme(5, "우주", 0xFF1A237E.toInt(), 0xFF000000.toInt(),
                0xFF283593.toInt(), 0xFF3949AB.toInt(),
                intArrayOf(
                    0xFF7C4DFF.toInt(), 0xFF00E5FF.toInt(), 0xFFFF4081.toInt(),
                    0xFF69F0AE.toInt(), 0xFFFFD740.toInt(), 0xFFE040FB.toInt()
                ), "advanced"),

            // 7: 성 (Castle) — stages 181-200
            WorldTheme(6, "성", 0xFF4A148C.toInt(), 0xFF311B92.toInt(),
                0xFF6A1B9A.toInt(), 0xFF8E24AA.toInt(),
                intArrayOf(
                    0xFFAB47BC.toInt(), 0xFFFFD700.toInt(), 0xFFE0E0E0.toInt(),
                    0xFFEF5350.toInt(), 0xFF7E57C2.toInt(), 0xFFFFF176.toInt()
                ), "advanced")
        )

        fun forStage(stage: Int): WorldTheme {
            require(stage >= 1) { "stage must be >= 1 (got $stage)" }
            val index = when {
                stage <= 30  -> 0
                stage <= 60  -> 1
                stage <= 90  -> 2
                stage <= 120 -> 3
                stage <= 150 -> 4
                stage <= 180 -> 5
                else         -> 6
            }
            return THEMES[index]
        }

        /** Endless mode: cycles all 7 themes via [generatedStageId] % 7. */
        fun forEndlessStage(generatedStageId: Int): WorldTheme {
            require(generatedStageId >= 1) { "generatedStageId must be >= 1 (got $generatedStageId)" }
            return THEMES[generatedStageId % THEMES.size]
        }

        fun all(): List<WorldTheme> = THEMES
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorldTheme) return false
        return worldIndex == other.worldIndex
    }

    override fun hashCode(): Int = worldIndex
}
