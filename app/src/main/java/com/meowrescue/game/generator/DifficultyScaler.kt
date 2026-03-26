package com.meowrescue.game.generator

import com.meowrescue.game.model.BlockType

object DifficultyScaler {

    data class EnemyStatRange(
        val count: Int,
        val hpScale: Float,
        val attackScale: Float,
        val isBoss: Boolean = false
    )

    data class GridConstraints(
        val width: Int,
        val height: Int,
        val allowedTypes: List<BlockType>
    )

    /**
     * Scale enemy stats based on chapter and stage.
     * Stage 10 of each chapter is a boss stage.
     */
    fun getEnemyStats(chapter: Int, stage: Int): EnemyStatRange {
        val isBoss = stage == 10
        val baseScale = 1f + (chapter - 1) * 0.3f + (stage - 1) * 0.05f

        return if (isBoss) {
            EnemyStatRange(
                count = 1,
                hpScale = baseScale * 2f,
                attackScale = baseScale * 1.5f,
                isBoss = true
            )
        } else {
            val enemyCount = when {
                stage <= 3 -> 1
                stage <= 6 -> 2
                else -> 3
            }
            EnemyStatRange(
                count = enemyCount,
                hpScale = baseScale,
                attackScale = baseScale
            )
        }
    }

    /**
     * Grid constraints based on chapter progression.
     * Earlier chapters use fewer block types and smaller grids.
     */
    fun getGridConstraints(chapter: Int, stage: Int): GridConstraints {
        val width: Int
        val height: Int
        val types: List<BlockType>

        when (chapter) {
            1 -> {
                width = 5
                height = 5
                types = listOf(BlockType.ATTACK, BlockType.FIRE, BlockType.HEAL)
            }
            2 -> {
                width = 5
                height = 5
                types = listOf(BlockType.ATTACK, BlockType.FIRE, BlockType.WATER, BlockType.HEAL)
            }
            else -> {
                width = 5
                height = 6
                types = BlockType.MATCHABLE
            }
        }

        return GridConstraints(width, height, types)
    }

    /**
     * Calculate player starting HP for a run based on chapter.
     */
    fun getPlayerStartHp(chapter: Int): Int {
        return 100 + (chapter - 1) * 10
    }
}
