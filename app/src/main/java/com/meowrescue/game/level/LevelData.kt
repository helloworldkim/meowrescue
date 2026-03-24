package com.meowrescue.game.level

data class LevelData(
    val levelId: Int,
    val name: String,
    val difficulty: String,
    val maxPins: Int,
    val stars: StarThresholds,
    val balls: List<BallData>,
    val cats: List<CatData>,
    val pins: List<PinData>,
    val obstacles: List<ObstacleData>,
    val platforms: List<PlatformData>
)

data class StarThresholds(
    val one: Int,
    val two: Int,
    val three: Int
)

data class BallData(
    val type: String,
    val x: Float,
    val y: Float
)

data class CatData(
    val x: Float,
    val y: Float,
    val catId: String
)

data class PinData(
    val type: String,
    val x: Float,
    val y: Float
)

data class ObstacleData(
    val type: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class PlatformData(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val angle: Float
)
