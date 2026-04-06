package com.meowrescue.game.minigame

import org.jbox2d.dynamics.Body

data class ObstacleBody(
    val body: Body,
    val material: ObstacleMaterial,
    var hp: Int,
    val widthM: Float,
    val heightM: Float
)
