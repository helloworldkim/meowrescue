package com.meowrescue.game.engine

enum class BattleTurnPhase {
    PLAYER_INPUT,
    MATCHING,
    CASCADING,
    PLAYER_ATTACK,
    ENEMY_ATTACK,
    VICTORY,
    DEFEAT
}
