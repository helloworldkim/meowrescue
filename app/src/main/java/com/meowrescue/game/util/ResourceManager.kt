package com.meowrescue.game.util

import android.content.Context

object ResourceManager {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getContext(): Context? = appContext
}
