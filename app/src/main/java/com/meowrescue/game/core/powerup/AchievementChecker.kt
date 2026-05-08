package com.meowrescue.game.core.powerup

/**
 * Fan-in hook that [PowerUpService] calls after incrementing the power-up use counter.
 *
 * Decouples [PowerUpService] from [ProgressionManager] so the power-up package
 * can be unit-tested without the full achievement system wired in.
 *
 * Production wiring: pass a lambda that delegates to
 * `ProgressionManager.checkAchievements(context)` where context includes the
 * updated power-up use count.
 */
fun interface AchievementChecker {
    suspend fun onPowerUpUsed()
}
