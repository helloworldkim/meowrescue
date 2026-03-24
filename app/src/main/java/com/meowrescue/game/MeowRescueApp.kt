package com.meowrescue.game

import android.app.Application
import com.meowrescue.game.data.AppDatabase

class MeowRescueApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pre-initialize Room DB singleton on app start
        AppDatabase.getInstance(this)
    }
}
