package com.meowrescue.game.core.progression

import com.meowrescue.game.core.progression.AchievementDef.Category

/**
 * Master registry of all 32 achievements.
 *
 * Implements: design/gdd/progression-system.md — Achievement System
 *
 * Two rewards differ from the original GDD table per ADR-0005 R2 amendment
 * (shipping v1.6.0 balance pass):
 *   - clear_200 = 100  (GDD had 200)
 *   - endless_50 = 90  (GDD had 100)
 *
 * TOTAL_REWARD == 2180 is enforced at class-load time via [init].
 *
 * Check order within [checkAchievements]: Progress → Mastery → Gameplay →
 * Launch → Collection → Economy → Endless. Within each category, ascending
 * threshold order.
 */
object AchievementDefs {

    val ALL: List<AchievementDef> = listOf(

        // ── Progress (9) — subtotal: 510 ─────────────────────────────────────
        AchievementDef("clear_1",   20,  Category.PROGRESS)  { it.maxCompleted >= 1   },
        AchievementDef("clear_10",  30,  Category.PROGRESS)  { it.maxCompleted >= 10  },
        AchievementDef("clear_30",  50,  Category.PROGRESS)  { it.maxCompleted >= 30  },
        AchievementDef("clear_60",  50,  Category.PROGRESS)  { it.maxCompleted >= 60  },
        AchievementDef("clear_90",  50,  Category.PROGRESS)  { it.maxCompleted >= 90  },
        AchievementDef("clear_120", 50,  Category.PROGRESS)  { it.maxCompleted >= 120 },
        AchievementDef("clear_150", 80,  Category.PROGRESS)  { it.maxCompleted >= 150 },
        AchievementDef("clear_180", 80,  Category.PROGRESS)  { it.maxCompleted >= 180 },
        AchievementDef("clear_200", 100, Category.PROGRESS)  { it.maxCompleted >= 200 }, // 100 not 200 (ADR-0005 R2)

        // ── Mastery (4) — subtotal: 760 ───────────────────────────────────────
        AchievementDef("star3_10",  30,  Category.MASTERY)   { it.threeStarCount >= 10  },
        AchievementDef("star3_50",  80,  Category.MASTERY)   { it.threeStarCount >= 50  },
        AchievementDef("star3_100", 150, Category.MASTERY)   { it.threeStarCount >= 100 },
        AchievementDef("star3_200", 500, Category.MASTERY)   { it.threeStarCount >= 200 },

        // ── Gameplay (5) — subtotal: 200 ──────────────────────────────────────
        AchievementDef("optimal_clear", 20, Category.GAMEPLAY) { it.moves <= it.optimalMoves },
        AchievementDef("two_move",      50, Category.GAMEPLAY) { it.moves <= 2 },
        AchievementDef("no_undo",       20, Category.GAMEPLAY) { !it.undoUsed && it.stars >= 3 },
        AchievementDef("speed_10s",     30, Category.GAMEPLAY) { it.timeMs in 1..10_000 },
        AchievementDef("speed_5s",      80, Category.GAMEPLAY) { it.timeMs in 1..5_000  },

        // ── Launch (3) — subtotal: 110 ────────────────────────────────────────
        AchievementDef("launch_10",   30, Category.LAUNCH) { it.completedLaunchCount >= 10 },
        AchievementDef("launch_1cat", 50, Category.LAUNCH) { it.catsUsed == 1 },
        AchievementDef("launch_tnt",  30, Category.LAUNCH) { it.tntExplosions >= 3 },

        // ── Collection (5) — subtotal: 380 ───────────────────────────────────
        AchievementDef("cat_3",   20,  Category.COLLECTION) { it.unlockedCatCount >= 3  },
        AchievementDef("cat_5",   30,  Category.COLLECTION) { it.unlockedCatCount >= 5  },
        AchievementDef("cat_7",   50,  Category.COLLECTION) { it.unlockedCatCount >= 7  },
        AchievementDef("cat_10",  80,  Category.COLLECTION) { it.unlockedCatCount >= 10 },
        AchievementDef("cat_all", 200, Category.COLLECTION) { it.unlockedCatCount >= 13 },

        // ── Economy (4) — subtotal: 100 ───────────────────────────────────────
        AchievementDef("coins_100",  10, Category.ECONOMY) { it.totalCoinsEarned >= 100  },
        AchievementDef("coins_1000", 50, Category.ECONOMY) { it.totalCoinsEarned >= 1000 },
        AchievementDef("powerup_1",  10, Category.ECONOMY) { it.powerUpUseCount >= 1  },
        AchievementDef("powerup_10", 30, Category.ECONOMY) { it.powerUpUseCount >= 10 },

        // ── Endless (2) — subtotal: 120 ───────────────────────────────────────
        AchievementDef("endless_10", 30, Category.ENDLESS) { it.endlessCount >= 10 },
        AchievementDef("endless_50", 90, Category.ENDLESS) { it.endlessCount >= 50 }, // 90 not 100 (ADR-0005 R2)
    )

    /** Lifetime coin payout if a player unlocks every achievement. */
    val TOTAL_REWARD: Int = ALL.sumOf { it.coinReward }

    init {
        require(TOTAL_REWARD == 2180) {
            "Achievement reward total is $TOTAL_REWARD but must be 2180. " +
                "Check AchievementDefs.ALL for a misconfigured coinReward."
        }
    }
}
