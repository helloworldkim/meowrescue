package com.meowrescue.game.minigame

sealed class CatAbility {

    abstract val displayName: String

    data class Normal(
        override val displayName: String = "일반"
    ) : CatAbility()

    data class Explosive(
        override val displayName: String = "폭발",
        val blastRadiusMeters: Float = 2.5f,
        val blastForce: Float = 80f
    ) : CatAbility()

    data class Split(
        override val displayName: String = "분열",
        val fragmentCount: Int = 3,
        val spreadAngleDeg: Float = 30f,
        val fragmentScaleFactor: Float = 0.5f
    ) : CatAbility()

    data class Charge(
        override val displayName: String = "돌진",
        val sizeMultiplier: Float = 2.0f,
        val maxPenetrateCount: Int = 3
    ) : CatAbility()

    data class Redirect(
        override val displayName: String = "방향전환",
        val maxRedirects: Int = 1
    ) : CatAbility()

    companion object {
        fun forCatId(catId: Int): CatAbility = when (catId) {
            1, 2, 3    -> Normal()
            4, 5       -> Redirect()
            6, 7       -> Split()
            8, 9, 12   -> Explosive()
            10, 11, 13 -> Charge()
            else       -> Normal()
        }
    }
}
