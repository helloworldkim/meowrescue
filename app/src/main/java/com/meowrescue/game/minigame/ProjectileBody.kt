package com.meowrescue.game.minigame

import org.jbox2d.dynamics.Body

data class ProjectileBody(
    val body: Body,
    val catId: Int,
    val ability: CatAbility,
    val radiusM: Float,
    var abilityUsed: Boolean = false,
    var penetrateCount: Int = 0
)
