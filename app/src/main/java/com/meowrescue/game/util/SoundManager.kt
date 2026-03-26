package com.meowrescue.game.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.meowrescue.game.R

object SoundManager {

    private var soundPool: SoundPool? = null
    private var mediaPlayer: MediaPlayer? = null
    private var context: Context? = null
    private var initialized = false
    private var soundEnabled = true

    // SFX IDs (loaded into SoundPool)
    private var sfxPinRemove = 0
    private var sfxBallBounce = 0
    private var sfxBallDestroy = 0
    private var sfxBombExplode = 0
    private var sfxCatRescue = 0
    private var sfxTeleport = 0
    private var sfxSwitchToggle = 0
    private var sfxLevelClear = 0
    private var sfxLevelFail = 0
    private var sfxStarEarn = 0
    private var sfxButtonTap = 0
    private var sfxCageDestroy = 0

    // BGM resource mapping
    private val bgmMap = mapOf(
        "menu" to R.raw.bgm_menu,
        "tutorial" to R.raw.bgm_tutorial,
        "beginner" to R.raw.bgm_beginner,
        "intermediate" to R.raw.bgm_intermediate,
        "advanced" to R.raw.bgm_advanced
    )
    private var currentBgmKey: String? = null

    fun init(ctx: Context) {
        if (initialized) return
        context = ctx.applicationContext

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load SFX
        val sp = soundPool!!
        sfxPinRemove = sp.load(ctx, R.raw.sfx_pin_remove, 1)
        sfxBallBounce = sp.load(ctx, R.raw.sfx_ball_bounce, 1)
        sfxBallDestroy = sp.load(ctx, R.raw.sfx_ball_destroy, 1)
        sfxBombExplode = sp.load(ctx, R.raw.sfx_bomb_explode, 1)
        sfxCatRescue = sp.load(ctx, R.raw.sfx_cat_rescue, 1)
        sfxTeleport = sp.load(ctx, R.raw.sfx_teleport, 1)
        sfxSwitchToggle = sp.load(ctx, R.raw.sfx_switch_toggle, 1)
        sfxLevelClear = sp.load(ctx, R.raw.sfx_level_clear, 1)
        sfxLevelFail = sp.load(ctx, R.raw.sfx_level_fail, 1)
        sfxStarEarn = sp.load(ctx, R.raw.sfx_star_earn, 1)
        sfxButtonTap = sp.load(ctx, R.raw.sfx_button_tap, 1)
        sfxCageDestroy = sp.load(ctx, R.raw.sfx_cage_destroy, 1)

        initialized = true
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        if (!enabled) {
            stopBgm()
        }
    }

    // --- SFX playback ---

    private fun playSfx(id: Int, volume: Float = 1f) {
        if (!soundEnabled || id == 0) return
        soundPool?.play(id, volume, volume, 1, 0, 1f)
    }

    fun playPinRemove() = playSfx(sfxPinRemove)
    fun playBallBounce() = playSfx(sfxBallBounce, 0.6f)
    fun playBallDestroy() = playSfx(sfxBallDestroy)
    fun playBombExplode() = playSfx(sfxBombExplode)
    fun playCatRescue() = playSfx(sfxCatRescue)
    fun playTeleport() = playSfx(sfxTeleport)
    fun playSwitchToggle() = playSfx(sfxSwitchToggle)
    fun playLevelClear() = playSfx(sfxLevelClear)
    fun playLevelFail() = playSfx(sfxLevelFail)
    fun playStarEarn() = playSfx(sfxStarEarn)
    fun playButtonTap() = playSfx(sfxButtonTap)
    fun playCageDestroy() = playSfx(sfxCageDestroy)

    // --- BGM playback ---

    fun playBgm(key: String) {
        if (!soundEnabled) return
        val ctx = context ?: return
        val resId = bgmMap[key.lowercase()] ?: return

        // Don't restart if same BGM is already playing
        try {
            if (currentBgmKey == key && mediaPlayer?.isPlaying == true) return
        } catch (_: IllegalStateException) { /* fall through to restart */ }

        stopBgm()
        mediaPlayer = MediaPlayer.create(ctx, resId)?.apply {
            isLooping = true
            setVolume(0.5f, 0.5f)
            start()
        }
        currentBgmKey = key
    }

    fun stopBgm() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: IllegalStateException) { /* already released or error state */ }
        mediaPlayer = null
        currentBgmKey = null
    }

    fun pauseBgm() {
        try {
            mediaPlayer?.takeIf { it.isPlaying }?.pause()
        } catch (_: IllegalStateException) { /* ignore */ }
    }

    fun resumeBgm() {
        if (!soundEnabled) return
        try {
            mediaPlayer?.start()
        } catch (_: IllegalStateException) { /* ignore */ }
    }

    fun release() {
        stopBgm()
        soundPool?.release()
        soundPool = null
        initialized = false
        context = null
    }
}
