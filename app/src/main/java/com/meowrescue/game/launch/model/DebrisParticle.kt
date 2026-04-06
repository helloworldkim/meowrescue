package com.meowrescue.game.launch.model

data class DebrisParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var rotSpeed: Float,
    var life: Float,
    val material: ObstacleMaterial
)
