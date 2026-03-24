package com.meowrescue.game.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey val levelId: Int,
    val stars: Int,
    val completed: Boolean,
    val catUnlocked: String?
)

@Database(entities = [UserProgress::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProgressDao(): UserProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meow_rescue_db"
                )
                    .allowMainThreadQueries() // MVP: acceptable for small dataset
                    .build().also { INSTANCE = it }
            }
        }
    }
}
