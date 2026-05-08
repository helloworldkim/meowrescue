package com.meowrescue.game.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProgress::class,
        LaunchProgress::class,
        PlayerStats::class,
        Achievement::class,
        CosmeticPurchase::class,
        SelectedCosmetic::class,
        ThemeUnlock::class,
    ],
    version = 5,
    exportSchema = true
)
internal abstract class AppDatabase : RoomDatabase() {

    abstract fun userProgressDao(): UserProgressDao
    abstract fun launchProgressDao(): LaunchProgressDao
    abstract fun playerStatsDao(): PlayerStatsDao
    abstract fun achievementDao(): AchievementDao
    abstract fun cosmeticDao(): CosmeticDao
    abstract fun selectedCosmeticDao(): SelectedCosmeticDao
    abstract fun themeDao(): ThemeDao

    companion object {
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS launch_progress (
                        stageId INTEGER NOT NULL PRIMARY KEY,
                        stars INTEGER NOT NULL,
                        completed INTEGER NOT NULL,
                        bestScore INTEGER NOT NULL
                    )
                """)
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add bestScore column to user_progress
                db.execSQL("ALTER TABLE user_progress ADD COLUMN bestScore INTEGER NOT NULL DEFAULT 0")

                // Create player_stats table (coins, economy)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
                        coins INTEGER NOT NULL DEFAULT 0,
                        totalCoinsEarned INTEGER NOT NULL DEFAULT 0
                    )
                """)
                // Insert default row
                db.execSQL("INSERT OR IGNORE INTO player_stats (id, coins, totalCoinsEarned) VALUES (1, 0, 0)")

                // Create achievements table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        achievementId TEXT NOT NULL PRIMARY KEY,
                        unlocked INTEGER NOT NULL DEFAULT 0,
                        unlockedAt TEXT NOT NULL DEFAULT ''
                    )
                """)
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meow_rescue_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }

        internal fun closeForTesting() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
