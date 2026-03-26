package com.meowrescue.game.model

import com.meowrescue.game.model.util.Vector2D

data class Cat(
    var position: Vector2D,
    val catId: String,
    val cageId: String = "",
    var isRescued: Boolean = false
)
