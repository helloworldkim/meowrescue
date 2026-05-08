package com.meowrescue.game.core.progression

/**
 * Defines a single achievement: its ID, coin reward, category, and unlock condition.
 *
 * Implements: design/gdd/progression-system.md — Achievement System
 *
 * [condition] is evaluated against an [AchievementCheckContext] snapshot. It must be
 * a pure function with no side-effects — [ProgressionManager.checkAchievements] is
 * responsible for all persistence and economy mutations.
 */
data class AchievementDef(
    val id: String,
    val coinReward: Int,
    val category: Category,
    val condition: (AchievementCheckContext) -> Boolean
) {
    enum class Category {
        PROGRESS, MASTERY, GAMEPLAY, LAUNCH, COLLECTION, ECONOMY, ENDLESS
    }
}
