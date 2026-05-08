package com.meowrescue.game.data

/** Difficulty tier for Cat Launch mode. Drives the coin multiplier shown on the launch-difficulty-picker screen. */
enum class Difficulty(val coinMultiplier: Float) {
    EASY(1.0f),
    NORMAL(1.5f),
    HARD(2.0f)
}
