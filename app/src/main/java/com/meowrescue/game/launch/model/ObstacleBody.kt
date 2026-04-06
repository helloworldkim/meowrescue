package com.meowrescue.game.launch.model

import org.jbox2d.dynamics.Body

data class ObstacleBody(
    val body: Body,
    val material: ObstacleMaterial,
    var hp: Int,
    val widthM: Float,
    val heightM: Float
)
