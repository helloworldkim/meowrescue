package com.meowrescue.game.minigame

import org.jbox2d.dynamics.Body

data class EnemyBody(
    val body: Body,
    var hp: Int = 20,
    val radiusM: Float
)
