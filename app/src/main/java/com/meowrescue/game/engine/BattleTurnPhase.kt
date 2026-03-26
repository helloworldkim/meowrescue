package com.meowrescue.game.engine

enum class BattleTurnPhase {
    PLAYER_INPUT,
    SWAP_BACK,
    MATCHING,
    CASCADING,
    PLAYER_ATTACK,
    ENEMY_ATTACK,
    NO_MOVES,
    VICTORY,
    DEFEAT
}
