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
    private var sfxCatRescue = 0
    private var sfxLevelClear = 0
    private var sfxLevelFail = 0
    private var sfxStarEarn = 0
    private var sfxButtonTap = 0
    private var sfxCageDestroy = 0
    private var sfxBlockMatch = 0
    private var sfxCascade = 0
    private var sfxAttackHit = 0
    private var sfxEnemyAttack = 0
    private var sfxHeal = 0

    // BGM resource mapping
    private val bgmMap = mapOf(
        "menu" to R.raw.bgm_menu,
        "tutorial" to R.raw.bgm_tutorial,
        "beginner" to R.raw.bgm_beginner,
        "intermediate" to R.raw.bgm_intermediate,
        "advanced" to R.raw.bgm_advanced,
        "battle_normal" to R.raw.bgm_beginner,
        "battle_boss" to R.raw.bgm_advanced
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
        sfxCatRescue = sp.load(ctx, R.raw.sfx_cat_rescue, 1)
        sfxLevelClear = sp.load(ctx, R.raw.sfx_level_clear, 1)
        sfxLevelFail = sp.load(ctx, R.raw.sfx_level_fail, 1)
        sfxStarEarn = sp.load(ctx, R.raw.sfx_star_earn, 1)
        sfxButtonTap = sp.load(ctx, R.raw.sfx_button_tap, 1)
        sfxCageDestroy = sp.load(ctx, R.raw.sfx_cage_destroy, 1)
        sfxBlockMatch = sp.load(ctx, R.raw.sfx_block_match, 1)
        sfxCascade = sp.load(ctx, R.raw.sfx_cascade, 1)
        sfxAttackHit = sp.load(ctx, R.raw.sfx_attack_hit, 1)
        sfxEnemyAttack = sp.load(ctx, R.raw.sfx_enemy_attack, 1)
        sfxHeal = sp.load(ctx, R.raw.sfx_heal, 1)

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

    fun playCatRescue() = playSfx(sfxCatRescue)
    fun playLevelClear() = playSfx(sfxLevelClear)
    fun playLevelFail() = playSfx(sfxLevelFail)
    fun playStarEarn() = playSfx(sfxStarEarn)
    fun playButtonTap() = playSfx(sfxButtonTap)
    fun playCageDestroy() = playSfx(sfxCageDestroy)
    fun playBlockMatch() = playSfx(sfxBlockMatch)
    fun playCascade() = playSfx(sfxCascade)
    fun playAttackHit() = playSfx(sfxAttackHit)
    fun playEnemyAttack() = playSfx(sfxEnemyAttack)
    fun playHeal() = playSfx(sfxHeal)

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
