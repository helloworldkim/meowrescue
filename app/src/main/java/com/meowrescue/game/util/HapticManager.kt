package com.meowrescue.game.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticManager {

    private var vibrator: Vibrator? = null
    private var hapticEnabled = true

    fun init(ctx: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        hapticEnabled = enabled
    }

    /** Short tick for block movement. */
    fun vibrateBlockMove() {
        if (!hapticEnabled) return
        vibrate(18L, VibrationEffect.EFFECT_TICK)
    }

    /** Medium pulse for stage clear. */
    fun vibrateStageClear() {
        if (!hapticEnabled) return
        vibratePattern(longArrayOf(0, 60, 40, 80))
    }

    /** Light click for UI buttons. */
    fun vibrateButtonTap() {
        if (!hapticEnabled) return
        vibrate(12L, VibrationEffect.EFFECT_CLICK)
    }

    private fun vibrate(ms: Long, predefinedEffect: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(predefinedEffect))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }

    fun release() {
        vibrator = null
    }
}
