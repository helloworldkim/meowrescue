package com.meowrescue.game.model

data class Cat(
    val catId: String,
    val name: String,
    val spriteResId: Int,
    val rarity: CatRarity = CatRarity.COMMON,
    val buffType: BuffType = BuffType.ATTACK_BOOST
)

enum class CatRarity { COMMON, RARE, LEGENDARY }
