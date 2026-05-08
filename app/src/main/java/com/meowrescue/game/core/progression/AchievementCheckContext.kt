package com.meowrescue.game.core.progression

/**
 * Snapshot of all game state needed to evaluate achievement conditions.
 *
 * Implements: design/gdd/progression-system.md — Achievement Check Engine
 *
 * Constructed by the caller (e.g. game loop or stage-clear handler) from live
 * game state. [ProgressionManager.checkAchievements] overwrites [totalCoinsEarned]
 * with the EconomyManager StateFlow snapshot taken at check-time to guarantee
 * snapshot isolation — the value passed by the caller is intentionally ignored.
 *
 * All counts are cumulative lifetime values unless noted per-run (e.g. [moves],
 * [timeMs], [undoUsed], [catsUsed], [tntExplosions]).
 */
data class AchievementCheckContext(
    /** Highest puzzle stage ever cleared (lifetime max). */
    val maxCompleted: Int,
    /** Number of puzzle stages cleared with exactly 3 stars (lifetime). */
    val threeStarCount: Int,
    /** Number of cats the player has unlocked (lifetime). */
    val unlockedCatCount: Int,
    /**
     * Lifetime coins ever earned. This value is overwritten by the EconomyManager
     * StateFlow snapshot inside [ProgressionManager.checkAchievements] — the
     * caller-supplied value is ignored.
     */
    val totalCoinsEarned: Int,
    /** Lifetime number of times any power-up has been used. */
    val powerUpUseCount: Int,
    /** Lifetime number of Endless mode sessions played. */
    val endlessCount: Int,
    /** Lifetime number of Cat Launch stages completed. */
    val completedLaunchCount: Int,
    /** Stage ID of the current clear (puzzle or launch). */
    val stageId: Int,
    /** Star rating achieved in the current clear (1–3). */
    val stars: Int,
    /** Move count used in the current clear. */
    val moves: Int,
    /** Optimal (minimum) move count for the current stage. */
    val optimalMoves: Int,
    /** Wall-clock time taken to complete the current stage, in milliseconds. */
    val timeMs: Long,
    /** Whether the undo action was used during the current clear. */
    val undoUsed: Boolean,
    /** Number of cats used in the current Cat Launch run (0 for puzzle stages). */
    val catsUsed: Int = 0,
    /** Number of TNT explosions triggered in the current Cat Launch run. */
    val tntExplosions: Int = 0,
    /** True if this clear is an Endless mode session. */
    val isEndless: Boolean = false
)
