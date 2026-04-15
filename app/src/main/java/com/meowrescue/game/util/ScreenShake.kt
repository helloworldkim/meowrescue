package com.meowrescue.game.util

/**
 * Provides screen shake offsets for game-feel ("juice").
 * Call [trigger] with an intensity, then read [offsetX]/[offsetY] each frame.
 * Call [update] every frame to decay the shake.
 */
object ScreenShake {

    enum class Intensity(val amplitude: Float, val durationMs: Long) {
        LIGHT(4f, 100L),
        MEDIUM(8f, 200L),
        HEAVY(14f, 350L)
    }

    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    private var amplitude = 0f
    private var startTime = 0L
    private var durationMs = 0L
    private val rng = java.util.Random()

    @Synchronized
    fun trigger(intensity: Intensity) {
        // Allow stronger shake to override weaker one
        if (intensity.amplitude >= amplitude) {
            amplitude = intensity.amplitude
            durationMs = intensity.durationMs
            startTime = System.currentTimeMillis()
        }
    }

    /** Call every frame to update offsets. */
    @Synchronized
    fun update() {
        if (amplitude <= 0f) {
            offsetX = 0f
            offsetY = 0f
            return
        }
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= durationMs) {
            amplitude = 0f
            offsetX = 0f
            offsetY = 0f
            return
        }
        val decay = 1f - elapsed.toFloat() / durationMs
        val mag = amplitude * decay
        offsetX = (rng.nextFloat() * 2f - 1f) * mag
        offsetY = (rng.nextFloat() * 2f - 1f) * mag
    }

    @Synchronized
    fun reset() {
        amplitude = 0f
        offsetX = 0f
        offsetY = 0f
    }
}
