package com.meowrescue.game.launch.model

import org.jbox2d.dynamics.Body

data class ProjectileBody(
    val body: Body,
    val catId: Int,
    val ability: CatAbility,
    val radiusM: Float,
    var abilityUsed: Boolean = false,
    var penetrateCount: Int = 0,
    val penetratedBodies: MutableSet<Body> = mutableSetOf()
)
