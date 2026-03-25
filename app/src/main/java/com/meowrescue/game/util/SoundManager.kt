package com.meowrescue.game.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

// NOTE: SoundManager is initialized in MenuActivity but playEffect/playBgm/stopBgm
// are not yet called from game code. Sound integration is planned for Phase 4.
object SoundManager {

    private var soundPool: SoundPool? = null
    private var mediaPlayer: MediaPlayer? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()
        initialized = true
    }

    fun playEffect(effectId: Int) {
        soundPool?.play(effectId, 1f, 1f, 1, 0, 1f)
    }

    fun playBgm() {
        mediaPlayer?.start()
    }

    fun stopBgm() {
        mediaPlayer?.pause()
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        mediaPlayer?.release()
        mediaPlayer = null
        initialized = false
    }
}
