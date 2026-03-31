package com.meowrescue.game.minigame

import java.util.Random
import kotlin.math.ceil

data class StageConfig(
    val stageId: Int,
    val seed: Long,
    val catCount: Int,
    val catIds: List<Int>,
    val enemyCount: Int,
    val structures: List<Structure>,
    val starThresholds: StarThresholds
)

data class Structure(
    val template: StructureTemplate,
    val baseX: Float,
    val baseY: Float,
    val blocks: List<BlockPlacement>,
    val enemyPositions: List<Pair<Float, Float>>
)

data class BlockPlacement(
    val offsetX: Float, val offsetY: Float,
    val width: Float, val height: Float,
    val material: LaunchPhysicsWorld.ObstacleMaterial,
    val angleDeg: Float = 0f
)

data class StarThresholds(
    val threeStar: Int,
    val twoStar: Int,
    val oneStar: Int
)

enum class StructureTemplate {
    TOWER, ARCH, PYRAMID, BRIDGE, FORTRESS
}

class LaunchStageGenerator {

    companion object {
        private const val GROUND_HEIGHT = 0.5f
        private const val STRUCTURE_X_MIN = 5.0f
        private const val STRUCTURE_X_MAX = 9.0f
    }

    private data class DifficultyParams(
        val catCount: Int,
        val enemyCount: Int,
        val maxStructures: Int,
        val materials: List<LaunchPhysicsWorld.ObstacleMaterial>,
        val templates: List<StructureTemplate>
    )

    private fun getDifficultyParams(stageId: Int): DifficultyParams {
        return when {
            stageId <= 10 -> DifficultyParams(
                catCount = 3,
                enemyCount = 2,
                maxStructures = 2,
                materials = listOf(
                    LaunchPhysicsWorld.ObstacleMaterial.WOOD,
                    LaunchPhysicsWorld.ObstacleMaterial.GLASS
                ),
                templates = listOf(StructureTemplate.TOWER, StructureTemplate.ARCH)
            )
            stageId <= 25 -> DifficultyParams(
                catCount = 3,
                enemyCount = 3,
                maxStructures = 3,
                materials = listOf(
                    LaunchPhysicsWorld.ObstacleMaterial.WOOD,
                    LaunchPhysicsWorld.ObstacleMaterial.GLASS
                ),
                templates = listOf(StructureTemplate.TOWER, StructureTemplate.ARCH, StructureTemplate.PYRAMID)
            )
            stageId <= 50 -> DifficultyParams(
                catCount = 4,
                enemyCount = 4,
                maxStructures = 3,
                materials = listOf(
                    LaunchPhysicsWorld.ObstacleMaterial.WOOD,
                    LaunchPhysicsWorld.ObstacleMaterial.GLASS,
                    LaunchPhysicsWorld.ObstacleMaterial.STONE
                ),
                templates = listOf(
                    StructureTemplate.TOWER, StructureTemplate.ARCH,
                    StructureTemplate.PYRAMID, StructureTemplate.BRIDGE
                )
            )
            stageId <= 100 -> DifficultyParams(
                catCount = 4,
                enemyCount = 5,
                maxStructures = 4,
                materials = LaunchPhysicsWorld.ObstacleMaterial.values().toList(),
                templates = StructureTemplate.values().toList()
            )
            else -> DifficultyParams(
                catCount = 5,
                enemyCount = 6,
                maxStructures = 5,
                materials = LaunchPhysicsWorld.ObstacleMaterial.values().toList(),
                templates = StructureTemplate.values().toList()
            )
        }
    }

    fun generate(stageId: Int, unlockedCatIds: List<Int>): StageConfig {
        val seed = stageId.toLong() * 7919L
        val rng = Random(seed)
        val params = getDifficultyParams(stageId)

        val catIds = selectCatIds(rng, unlockedCatIds, params.catCount)
        val structures = buildStructures(rng, params)
        val structures_with_enemies = distributeEnemies(rng, structures, params.enemyCount)

        val threeStar = ceil(params.catCount * 0.4).toInt()
        val twoStar = ceil(params.catCount * 0.7).toInt()
        val starThresholds = StarThresholds(
            threeStar = threeStar,
            twoStar = twoStar,
            oneStar = params.catCount
        )

        return StageConfig(
            stageId = stageId,
            seed = seed,
            catCount = params.catCount,
            catIds = catIds,
            enemyCount = params.enemyCount,
            structures = structures_with_enemies,
            starThresholds = starThresholds
        )
    }

    private fun selectCatIds(rng: Random, unlockedCatIds: List<Int>, catCount: Int): List<Int> {
        if (unlockedCatIds.isEmpty()) return List(catCount) { 0 }

        val result = mutableListOf<Int>()
        val available = unlockedCatIds.toMutableList()

        if (available.size >= catCount) {
            val shuffled = available.shuffled(rng)
            result.addAll(shuffled.take(catCount))
        } else {
            repeat(catCount) {
                result.add(available[rng.nextInt(available.size)])
            }
        }
        return result
    }

    private fun buildStructures(
        rng: Random,
        params: DifficultyParams
    ): List<Structure> {
        val structureCount = if (params.maxStructures == 1) 1
            else 1 + rng.nextInt(params.maxStructures)

        val xPositions = generateXPositions(rng, structureCount)

        return xPositions.mapIndexed { _, x ->
            val template = params.templates[rng.nextInt(params.templates.size)]
            buildStructure(rng, template, x, GROUND_HEIGHT, params.materials)
        }
    }

    private fun generateXPositions(rng: Random, count: Int): List<Float> {
        val range = STRUCTURE_X_MAX - STRUCTURE_X_MIN
        val step = range / count
        return List(count) { i ->
            STRUCTURE_X_MIN + step * i + rng.nextFloat() * step * 0.5f
        }
    }

    private fun buildStructure(
        rng: Random,
        template: StructureTemplate,
        baseX: Float,
        baseY: Float,
        materials: List<LaunchPhysicsWorld.ObstacleMaterial>
    ): Structure {
        val blocks = when (template) {
            StructureTemplate.TOWER -> buildTower(rng, materials)
            StructureTemplate.ARCH -> buildArch(rng, materials)
            StructureTemplate.PYRAMID -> buildPyramid(rng, materials)
            StructureTemplate.BRIDGE -> buildBridge(rng, materials)
            StructureTemplate.FORTRESS -> buildFortress(rng, materials)
        }
        return Structure(
            template = template,
            baseX = baseX,
            baseY = baseY,
            blocks = blocks,
            enemyPositions = emptyList()
        )
    }

    private fun randomMaterial(rng: Random, materials: List<LaunchPhysicsWorld.ObstacleMaterial>): LaunchPhysicsWorld.ObstacleMaterial {
        return materials[rng.nextInt(materials.size)]
    }

    private fun buildTower(
        rng: Random,
        materials: List<LaunchPhysicsWorld.ObstacleMaterial>
    ): List<BlockPlacement> {
        val blockCount = 3 + rng.nextInt(3)
        val blockWidth = 0.8f
        val blockHeight = 0.4f
        return List(blockCount) { i ->
            BlockPlacement(
                offsetX = 0f,
                offsetY = i * blockHeight,
                width = blockWidth,
                height = blockHeight,
                material = randomMaterial(rng, materials)
            )
        }
    }

    private fun buildArch(
        rng: Random,
        materials: List<LaunchPhysicsWorld.ObstacleMaterial>
    ): List<BlockPlacement> {
        val pillarHeight = 1.2f
        val pillarWidth = 0.3f
        val capWidth = 1.2f
        val capHeight = 0.3f
        val spacing = capWidth - pillarWidth

        return listOf(
            BlockPlacement(
                offsetX = 0f,
                offsetY = 0f,
                width = pillarWidth,
                height = pillarHeight,
                material = randomMaterial(rng, materials)
            ),
            BlockPlacement(
                offsetX = spacing,
                offsetY = 0f,
                width = pillarWidth,
                height = pillarHeight,
                material = randomMaterial(rng, materials)
            ),
            BlockPlacement(
                offsetX = spacing / 2f,
                offsetY = pillarHeight,
                width = capWidth,
                height = capHeight,
                material = randomMaterial(rng, materials)
            )
        )
    }

    private fun buildPyramid(
        rng: Random,
        materials: List<LaunchPhysicsWorld.ObstacleMaterial>
    ): List<BlockPlacement> {
        val blockWidth = 0.6f
        val blockHeight = 0.3f
        val blocks = mutableListOf<BlockPlacement>()

        val rows = listOf(3, 2, 1)
        var currentY = 0f
        for (rowCount in rows) {
            val rowWidth = rowCount * blockWidth
            val startX = -rowWidth / 2f + blockWidth / 2f
            for (col in 0 until rowCount) {
                blocks.add(
                    BlockPlacement(
                        offsetX = startX + col * blockWidth,
                        offsetY = currentY,
                        width = blockWidth,
                        height = blockHeight,
                        material = randomMaterial(rng, materials)
                    )
                )
            }
            currentY += blockHeight
        }
        return blocks
    }

    private fun buildBridge(
        rng: Random,
        materials: List<LaunchPhysicsWorld.ObstacleMaterial>
    ): List<BlockPlacement> {
        val pillarWidth = 0.3f
        val pillarHeight = 0.8f
        val spanWidth = 2.0f
        val spanHeight = 0.2f
        val spacing = 1.5f

        return listOf(
            BlockPlacement(
                offsetX = 0f,
                offsetY = 0f,
                width = pillarWidth,
                height = pillarHeight,
                material = randomMaterial(rng, materials)
            ),
            BlockPlacement(
                offsetX = spacing,
                offsetY = 0f,
                width = pillarWidth,
                height = pillarHeight,
                material = randomMaterial(rng, materials)
            ),
            BlockPlacement(
                offsetX = spacing / 2f,
                offsetY = pillarHeight,
                width = spanWidth,
                height = spanHeight,
                material = randomMaterial(rng, materials)
            )
        )
    }

    private fun buildFortress(
        rng: Random,
        materials: List<LaunchPhysicsWorld.ObstacleMaterial>
    ): List<BlockPlacement> {
        val wallThickness = 0.2f
        val wallWidth = 1.5f
        val wallHeight = 1.0f

        return listOf(
            // Bottom wall
            BlockPlacement(
                offsetX = 0f,
                offsetY = 0f,
                width = wallWidth,
                height = wallThickness,
                material = randomMaterial(rng, materials)
            ),
            // Left wall
            BlockPlacement(
                offsetX = -wallWidth / 2f + wallThickness / 2f,
                offsetY = wallHeight / 2f,
                width = wallThickness,
                height = wallHeight,
                material = randomMaterial(rng, materials)
            ),
            // Right wall
            BlockPlacement(
                offsetX = wallWidth / 2f - wallThickness / 2f,
                offsetY = wallHeight / 2f,
                width = wallThickness,
                height = wallHeight,
                material = randomMaterial(rng, materials)
            ),
            // Top wall
            BlockPlacement(
                offsetX = 0f,
                offsetY = wallHeight,
                width = wallWidth,
                height = wallThickness,
                material = randomMaterial(rng, materials)
            )
        )
    }

    private fun distributeEnemies(
        rng: Random,
        structures: List<Structure>,
        enemyCount: Int
    ): List<Structure> {
        if (structures.isEmpty()) return structures

        val enemyAssignments = Array(structures.size) { mutableListOf<Pair<Float, Float>>() }

        repeat(enemyCount) { i ->
            val structureIndex = i % structures.size
            val structure = structures[structureIndex]
            val enemyPos = getEnemyPosition(structure)
            enemyAssignments[structureIndex].add(enemyPos)
        }

        return structures.mapIndexed { index, structure ->
            structure.copy(enemyPositions = enemyAssignments[index])
        }
    }

    private fun getEnemyPosition(structure: Structure): Pair<Float, Float> {
        return when (structure.template) {
            StructureTemplate.TOWER -> {
                val topBlock = structure.blocks.maxByOrNull { it.offsetY }
                val x = topBlock?.offsetX ?: 0f
                val y = if (topBlock != null) topBlock.offsetY + topBlock.height else 0f
                Pair(x, y)
            }
            StructureTemplate.ARCH -> {
                val cap = structure.blocks.maxByOrNull { it.offsetY }
                val x = cap?.offsetX ?: 0f
                val y = if (cap != null) cap.offsetY + cap.height else 0f
                Pair(x, y)
            }
            StructureTemplate.PYRAMID -> {
                val topBlock = structure.blocks.maxByOrNull { it.offsetY }
                val x = topBlock?.offsetX ?: 0f
                val y = if (topBlock != null) topBlock.offsetY + topBlock.height else 0f
                Pair(x, y)
            }
            StructureTemplate.BRIDGE -> {
                // Enemy underneath the span
                val span = structure.blocks.maxByOrNull { it.offsetY }
                val x = span?.offsetX ?: 0f
                val y = if (span != null) span.offsetY - 0.3f else 0f
                Pair(x, y)
            }
            StructureTemplate.FORTRESS -> {
                // Enemy inside the box
                Pair(0f, 0.4f)
            }
        }
    }
}
