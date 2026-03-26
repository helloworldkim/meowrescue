package com.meowrescue.game.generator

import com.meowrescue.game.model.Enemy
import com.meowrescue.game.model.EnemyTemplate
import com.meowrescue.game.model.GridState
import java.util.Random

object StageGenerator {

    data class Stage(
        val grid: GridState,
        val enemies: List<Enemy>
    )

    /**
     * Generate a complete stage: grid + enemy composition.
     */
    fun generateStage(chapter: Int, stage: Int, random: Random = Random()): Stage {
        val gridConstraints = DifficultyScaler.getGridConstraints(chapter, stage)
        val enemyStats = DifficultyScaler.getEnemyStats(chapter, stage)

        val grid = GridGenerator.generateGrid(
            width = gridConstraints.width,
            height = gridConstraints.height,
            allowedTypes = gridConstraints.allowedTypes,
            random = random
        )

        val enemies = generateEnemies(enemyStats, random)

        return Stage(grid = grid, enemies = enemies)
    }

    private fun generateEnemies(
        stats: DifficultyScaler.EnemyStatRange,
        random: Random
    ): List<Enemy> {
        if (stats.isBoss) {
            val bossTemplate = EnemyTemplate.BOSSES[random.nextInt(EnemyTemplate.BOSSES.size)]
            return listOf(
                EnemyTemplate.createEnemy(bossTemplate, stats.hpScale, stats.attackScale)
            )
        }

        val templates = EnemyTemplate.getTemplatesForChapter(stats.chapter)
        return (0 until stats.count).mapIndexed { index, _ ->
            val template = templates[random.nextInt(templates.size)]
            EnemyTemplate.createEnemy(template, stats.hpScale, stats.attackScale, index)
        }
    }
}
