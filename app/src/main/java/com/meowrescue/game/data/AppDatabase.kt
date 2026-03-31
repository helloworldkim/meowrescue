package com.meowrescue.game.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey val levelId: Int,
    val stars: Int,
    val completed: Boolean,
    val catUnlocked: String?
)

@Entity(tableName = "launch_progress")
data class LaunchProgress(
    @PrimaryKey val stageId: Int,
    val stars: Int,
    val completed: Boolean,
    val bestScore: Int
)

@Database(entities = [UserProgress::class, LaunchProgress::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProgressDao(): UserProgressDao
    abstract fun launchProgressDao(): LaunchProgressDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meow_rescue_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
