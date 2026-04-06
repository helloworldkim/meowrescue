package com.meowrescue.game.launch.model

data class StageConfig(
    val stageId: Int,
    val seed: Long,
    val catCount: Int,
    val catIds: List<Int>,
    val enemyCount: Int,
    val structures: List<Structure>,
    val starThresholds: StarThresholds
)
