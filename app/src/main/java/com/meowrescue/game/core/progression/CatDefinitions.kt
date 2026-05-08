package com.meowrescue.game.core.progression

/** A single cat entry: identity, unlock milestone, and Cat Launch ability. */
data class CatDefinition(
    val id: Int,
    val name: String,
    val requiredStage: Int,
    val ability: String
)

/** Compile-time catalogue of all 13 cats, ordered by unlock stage. */
object CatDefinitions {
    val ALL: List<CatDefinition> = listOf(
        CatDefinition(1,  "나비",   1,   "Normal"),
        CatDefinition(2,  "봄이",   15,  "Normal"),
        CatDefinition(3,  "여름",   30,  "Normal"),
        CatDefinition(4,  "가을",   45,  "Redirect"),
        CatDefinition(5,  "겨울",   60,  "Redirect"),
        CatDefinition(6,  "솜이",   75,  "Split"),
        CatDefinition(7,  "꽃이",   90,  "Split"),
        CatDefinition(8,  "하늘",   110, "Explosive"),
        CatDefinition(9,  "바다",   130, "Explosive"),
        CatDefinition(10, "무지개", 150, "Charge"),
        CatDefinition(11, "보석",   170, "Charge"),
        CatDefinition(12, "왕자",   185, "Explosive"),
        CatDefinition(13, "공주",   200, "Charge")
    )
}
