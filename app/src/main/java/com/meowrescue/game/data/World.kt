package com.meowrescue.game.data

/**
 * The 7 themed worlds in Meow Rescue (progression-system.md § World Structure).
 * Each world spans 30 stages: Garden = 1-30, Beach = 31-60, …, Castle = 171-200.
 *
 * Resolves OQ-PM9 (pause-menu.md) and stage-select.md Data Requirements
 * dependency on `worldFromStage()`.
 */
enum class World(
    val index: Int,
    val nameKr: String,
    val nameEn: String
) {
    GARDEN(1, "정원", "Garden"),
    BEACH(2, "해변", "Beach"),
    FOREST(3, "숲", "Forest"),
    SNOW(4, "눈", "Snow"),
    VOLCANO(5, "화산", "Volcano"),
    SPACE(6, "우주", "Space"),
    CASTLE(7, "성", "Castle");

    companion object {
        /**
         * Returns the [World] that contains [stageId] (1–210).
         * Stages beyond 210 clamp to [CASTLE].
         * Stages below 1 clamp to [GARDEN].
         */
        fun fromStage(stageId: Int): World =
            entries.getOrElse(((stageId - 1) / 30).coerceIn(0, 6)) { GARDEN }
    }
}
