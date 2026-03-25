package com.meowrescue.game.util

import android.content.Context

// NOTE: ResourceManager is not currently used by any game code.
// It was scaffolded for future global context access but is superseded by
// passing Context directly at call sites. Kept for potential Phase 4 use.
object ResourceManager {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getContext(): Context? = appContext
}
