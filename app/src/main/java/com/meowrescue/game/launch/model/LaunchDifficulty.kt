package com.meowrescue.game.launch.model

enum class LaunchDifficulty(
    val offset: Int,
    val coinMultiplier: Float,
    val label: String,
    val description: String
) {
    EASY(0, 1.0f, "쉬움", "나무·유리 위주, 적 소수"),
    NORMAL(25, 1.5f, "보통", "돌 재료 등장, 다양한 구조물"),
    HARD(50, 2.0f, "어려움", "모든 재료·구조물, 적 다수")
}
