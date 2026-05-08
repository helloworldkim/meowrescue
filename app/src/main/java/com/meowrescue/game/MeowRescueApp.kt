package com.meowrescue.game

import android.app.Application
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.AppDatabase
import com.meowrescue.game.data.GameRepository

class MeowRescueApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pre-initialize Room DB singleton on app start
        AppDatabase.getInstance(this)
        // Initialize AdMob SDK — pass repository so ad counter persists via IGameRepository
        AdManager.initialize(this, GameRepository(this))
    }
}
