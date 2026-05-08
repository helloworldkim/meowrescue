package com.meowrescue.game.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Foundation-layer wrapper for all SharedPreferences access in meow_rescue.
 *
 * Centralises every key constant and typed accessor so no caller outside
 * this package ever opens SharedPreferences directly. All writes use
 * [SharedPreferences.Editor.apply] (async); reads are synchronous and
 * Main-thread safe.
 *
 * Story 006 (T-1-8): retrofit to extract from inline GameRepository calls.
 * ADR-0001 §Data Routing Rules, ADR-0009 §SharedPreferences Keys.
 */
internal class SharedPrefsWrapper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    // ── Cat Collection ────────────────────────────────────────────────────

    /** Returns the selected cat id; defaults to 1 (first cat, never null). */
    fun getSelectedCatId(): Int = prefs.getInt(KEY_SELECTED_CAT, 1)

    /** Persists [id] as the active cat selection. */
    fun setSelectedCatId(id: Int) {
        prefs.edit().putInt(KEY_SELECTED_CAT, id).apply()
    }

    // ── Settings ──────────────────────────────────────────────────────────

    /** Returns true when sound is enabled; defaults to true. */
    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, true)

    /** Persists the sound-enabled [value]. */
    fun setSoundEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
    }

    // ── Endless Mode ──────────────────────────────────────────────────────

    /** Returns total Endless mode play count; defaults to 0. */
    fun getEndlessCount(): Int = prefs.getInt(KEY_ENDLESS_COUNT, 0)

    /** Persists the Endless mode play [value]. */
    fun setEndlessCount(value: Int) {
        prefs.edit().putInt(KEY_ENDLESS_COUNT, value).apply()
    }

    /** Returns the all-time best Endless score; defaults to 0. */
    fun getEndlessBest(): Int = prefs.getInt(KEY_ENDLESS_BEST, 0)

    /** Persists the all-time best Endless score [value]. */
    fun setEndlessBest(value: Int) {
        prefs.edit().putInt(KEY_ENDLESS_BEST, value).apply()
    }

    /** Returns the date string for which [getEndlessCoinToday] was recorded; defaults to "". */
    fun getEndlessCoinDate(): String = prefs.getString(KEY_ENDLESS_COIN_DATE, "") ?: ""

    /** Persists the Endless coin date [value]. */
    fun setEndlessCoinDate(value: String) {
        prefs.edit().putString(KEY_ENDLESS_COIN_DATE, value).apply()
    }

    /** Returns Endless coins earned on the stored date; defaults to 0. */
    fun getEndlessCoinToday(): Int = prefs.getInt(KEY_ENDLESS_COIN_TODAY, 0)

    /** Persists the Endless coins earned today [value]. */
    fun setEndlessCoinToday(value: Int) {
        prefs.edit().putInt(KEY_ENDLESS_COIN_TODAY, value).apply()
    }

    /**
     * Atomically writes both the coin [date] and the daily [today] count in a
     * single editor.apply() call. Use instead of separate setters whenever both
     * values change together (e.g. addEndlessCoins) to preserve consistency.
     */
    fun setEndlessCoinDateAndToday(date: String, today: Int) {
        prefs.edit()
            .putString(KEY_ENDLESS_COIN_DATE, date)
            .putInt(KEY_ENDLESS_COIN_TODAY, today)
            .apply()
    }

    // ── Tutorial ──────────────────────────────────────────────────────────

    /** Returns true once the tutorial has been completed; defaults to false. */
    fun isTutorialCompleted(): Boolean = prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false)

    /** Persists the tutorial-completed [value]. */
    fun setTutorialCompleted(value: Boolean) {
        prefs.edit().putBoolean(KEY_TUTORIAL_COMPLETED, value).apply()
    }

    // ── Power-Up Tracking ─────────────────────────────────────────────────

    /** Returns the lifetime power-up use count; defaults to 0. */
    fun getPowerUpUseCount(): Int = prefs.getInt(KEY_POWERUP_USE_COUNT, 0)

    /**
     * Increments the lifetime power-up use count by one.
     *
     * Safe because all power-up operations are serialised through
     * PowerUpService.apply() (single-Activity, no concurrent calls).
     */
    fun incrementPowerUpUseCount() {
        prefs.edit().putInt(KEY_POWERUP_USE_COUNT, getPowerUpUseCount() + 1).apply()
    }

    // ── Skip per World ────────────────────────────────────────────────────

    /**
     * Returns true if the skip has been used for [worldIndex] (0..6).
     *
     * @throws IllegalArgumentException if [worldIndex] is outside 0..6.
     */
    fun isSkipUsed(worldIndex: Int): Boolean {
        require(worldIndex in 0..6) { "worldIndex out of range: $worldIndex" }
        return prefs.getBoolean(skipKey(worldIndex), false)
    }

    /**
     * Marks the skip as used for [worldIndex] (0..6). Idempotent.
     *
     * @throws IllegalArgumentException if [worldIndex] is outside 0..6.
     */
    fun setSkipUsed(worldIndex: Int) {
        require(worldIndex in 0..6) { "worldIndex out of range: $worldIndex" }
        prefs.edit().putBoolean(skipKey(worldIndex), true).apply()
    }

    private fun skipKey(worldIndex: Int) = "skip_used_world_$worldIndex"

    // ── Ad Counter ────────────────────────────────────────────────────────

    /** Returns stage clears since last interstitial ad; defaults to 0. */
    fun getStagesSinceLastAd(): Int = prefs.getInt(KEY_STAGES_SINCE_AD, 0)

    /** Persists the stages-since-last-ad [count]. */
    fun setStagesSinceLastAd(count: Int) {
        prefs.edit().putInt(KEY_STAGES_SINCE_AD, count).apply()
    }

    // ── Cat Launch Best Scores per Difficulty ─────────────────────────────

    /** Returns the best total score ever achieved at [difficulty]; defaults to 0. */
    fun getLaunchBestScore(difficulty: Difficulty): Int =
        prefs.getInt(launchBestScoreKey(difficulty), 0)

    /** Persists [score] as the best score for [difficulty]. Caller is responsible for promotion logic. */
    fun setLaunchBestScore(difficulty: Difficulty, score: Int) {
        prefs.edit().putInt(launchBestScoreKey(difficulty), score).apply()
    }

    private fun launchBestScoreKey(difficulty: Difficulty) =
        "launch_best_score_${difficulty.name.lowercase()}"

    // ── Theme Selection ───────────────────────────────────────────────────

    /** Returns the selected theme id, or null if none has been chosen. */
    fun getSelectedThemeId(): String? = prefs.getString(KEY_SELECTED_THEME_ID, null)

    /**
     * Persists [value] as the active theme id. When [value] is null the key is
     * removed entirely so [getSelectedThemeId] returns null (not a literal "null"
     * string).
     */
    fun setSelectedThemeId(value: String?) {
        prefs.edit()
            .also { ed ->
                if (value == null) ed.remove(KEY_SELECTED_THEME_ID)
                else ed.putString(KEY_SELECTED_THEME_ID, value)
            }
            .apply()
    }

    // ── Constants ─────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_FILENAME = "meow_rescue"

        private const val KEY_SELECTED_CAT = "selected_cat"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_ENDLESS_COUNT = "endless_count"
        private const val KEY_ENDLESS_BEST = "endless_best"
        private const val KEY_ENDLESS_COIN_DATE = "endless_coin_date"
        private const val KEY_ENDLESS_COIN_TODAY = "endless_coin_today"
        private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        private const val KEY_POWERUP_USE_COUNT = "powerup_use_count"
        private const val KEY_SELECTED_THEME_ID = "selected_theme_id"
        private const val KEY_STAGES_SINCE_AD = "stages_since_last_ad"
    }
}
