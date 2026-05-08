package com.meowrescue.game.core.progression

import com.meowrescue.game.data.IGameRepository
import com.meowrescue.game.puzzle.model.WorldTheme

/**
 * Resolves the effective world theme for a stage, applying cosmetic override when set.
 *
 * Base theme is derived from [WorldTheme.forStage] (ADR-0018 / TR-prog-012).
 * Override is stored in SharedPreferences as `selected_theme_id` (ADR-0009).
 *
 * Usage — call once per stage load from Presentation:
 * ```
 * val theme = ThemeResolver(repository).forStage(stageId)
 * puzzleView.setTheme(theme)
 * ```
 */
class ThemeResolver(private val repository: IGameRepository) {

    /**
     * Returns the effective [WorldTheme] for [stageId].
     *
     * If the player has selected a cosmetic override (non-null [IGameRepository.getSelectedThemeId]),
     * that theme's [WorldTheme.worldIndex] is used; falls back to progression-based theme
     * if the stored index is invalid.
     */
    fun forStage(stageId: Int): WorldTheme {
        val override = repository.getSelectedThemeId()
        if (override != null) {
            val index = override.toIntOrNull()
            if (index != null) {
                val all = WorldTheme.all()
                if (index in all.indices) return all[index]
            }
        }
        return WorldTheme.forStage(stageId)
    }

    /** Clears the cosmetic override; subsequent calls return progression-based theme. */
    fun clearOverride() = repository.setSelectedThemeId(null)

    /** Persists [worldIndex] as the cosmetic theme override. */
    fun setOverride(worldIndex: Int) = repository.setSelectedThemeId(worldIndex.toString())
}
