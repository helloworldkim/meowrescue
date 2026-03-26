package com.meowrescue.game.model

import com.meowrescue.game.R

object EnemyTemplate {

    data class Template(
        val id: String,
        val name: String,
        val baseHp: Int,
        val baseAttack: Int,
        val weakness: BlockType?,
        val resistance: BlockType?,
        val spriteResId: Int,
        val attackPattern: List<EnemyAttackType>
    )

    val SLIME = Template(
        id = "slime", name = "Slime",
        baseHp = 30, baseAttack = 5,
        weakness = BlockType.FIRE, resistance = BlockType.WATER,
        spriteResId = R.drawable.enemy_slime,
        attackPattern = listOf(EnemyAttackType.NORMAL, EnemyAttackType.NORMAL, EnemyAttackType.HEAL_SELF)
    )

    val RAT = Template(
        id = "rat", name = "Rat",
        baseHp = 20, baseAttack = 8,
        weakness = BlockType.WATER, resistance = null,
        spriteResId = R.drawable.enemy_rat,
        attackPattern = listOf(EnemyAttackType.NORMAL, EnemyAttackType.HEAVY)
    )

    val CROW = Template(
        id = "crow", name = "Crow",
        baseHp = 25, baseAttack = 10,
        weakness = BlockType.ATTACK, resistance = BlockType.FIRE,
        spriteResId = R.drawable.enemy_crow,
        attackPattern = listOf(EnemyAttackType.NORMAL, EnemyAttackType.NORMAL, EnemyAttackType.HEAVY)
    )

    val SNAKE = Template(
        id = "snake", name = "Snake",
        baseHp = 35, baseAttack = 12,
        weakness = BlockType.FIRE, resistance = BlockType.ATTACK,
        spriteResId = R.drawable.enemy_snake,
        attackPattern = listOf(EnemyAttackType.NORMAL, EnemyAttackType.BUFF_SELF, EnemyAttackType.HEAVY)
    )

    val BOSS_WOLF = Template(
        id = "boss_wolf", name = "Boss Wolf",
        baseHp = 100, baseAttack = 15,
        weakness = null, resistance = null,
        spriteResId = R.drawable.enemy_boss_wolf,
        attackPattern = listOf(EnemyAttackType.NORMAL, EnemyAttackType.HEAVY, EnemyAttackType.NORMAL, EnemyAttackType.HEAL_SELF)
    )

    val ALL = listOf(SLIME, RAT, CROW, SNAKE)
    val BOSSES = listOf(BOSS_WOLF)

    fun getTemplatesForChapter(chapter: Int): List<Template> {
        return when (chapter) {
            1 -> listOf(SLIME, RAT)
            2 -> listOf(SLIME, RAT, CROW)
            else -> ALL
        }
    }

    fun createEnemy(template: Template, hpScale: Float = 1f, attackScale: Float = 1f): Enemy {
        val hp = (template.baseHp * hpScale).toInt()
        return Enemy(
            id = template.id,
            name = template.name,
            maxHp = hp,
            currentHp = hp,
            attack = (template.baseAttack * attackScale).toInt(),
            weakness = template.weakness,
            resistance = template.resistance,
            spriteResId = template.spriteResId,
            attackPattern = template.attackPattern
        )
    }
}
