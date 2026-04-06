package com.meowrescue.game.launch.stage

import com.meowrescue.game.launch.model.*
import java.util.Random
import kotlin.math.ceil

class LaunchStageGenerator {

    companion object {
        private const val GROUND_HEIGHT = 0.5f
        private const val STRUCTURE_X_MIN = 8.0f
        private const val STRUCTURE_X_MAX = 19.0f
    }

    private data class DifficultyParams(
        val catCount: Int,
        val enemyCount: Int,
        val maxStructures: Int,
        val materials: List<ObstacleMaterial>,
        val templates: List<StructureTemplate>,
        val tntChance: Float = 0f
    )

    private val nonTntMaterials = ObstacleMaterial.values()
        .filter { it != ObstacleMaterial.TNT }

    private fun getDifficultyParams(stageId: Int): DifficultyParams {
        return when {
            stageId <= 10 -> DifficultyParams(
                catCount = 3,
                enemyCount = 2,
                maxStructures = 2,
                materials = listOf(
                    ObstacleMaterial.WOOD,
                    ObstacleMaterial.GLASS
                ),
                templates = listOf(StructureTemplate.TOWER, StructureTemplate.ARCH)
            )
            stageId <= 25 -> DifficultyParams(
                catCount = 4,
                enemyCount = 3,
                maxStructures = 3,
                materials = listOf(
                    ObstacleMaterial.WOOD,
                    ObstacleMaterial.GLASS
                ),
                templates = listOf(
                    StructureTemplate.TOWER, StructureTemplate.ARCH,
                    StructureTemplate.PYRAMID, StructureTemplate.TALL_TOWER
                )
            )
            stageId <= 50 -> DifficultyParams(
                catCount = 4,
                enemyCount = 5,
                maxStructures = 3,
                materials = listOf(
                    ObstacleMaterial.WOOD,
                    ObstacleMaterial.GLASS,
                    ObstacleMaterial.STONE
                ),
                templates = listOf(
                    StructureTemplate.TOWER, StructureTemplate.ARCH,
                    StructureTemplate.PYRAMID, StructureTemplate.BRIDGE,
                    StructureTemplate.TALL_TOWER, StructureTemplate.DOUBLE_ARCH,
                    StructureTemplate.PLATFORM_STACK
                ),
                tntChance = 0.1f
            )
            stageId <= 100 -> DifficultyParams(
                catCount = 5,
                enemyCount = 6,
                maxStructures = 4,
                materials = nonTntMaterials,
                templates = StructureTemplate.values().toList(),
                tntChance = 0.15f
            )
            else -> DifficultyParams(
                catCount = 6,
                enemyCount = 8,
                maxStructures = 5,
                materials = nonTntMaterials,
                templates = StructureTemplate.values().toList(),
                tntChance = 0.2f
            )
        }
    }

    fun generate(stageId: Int, unlockedCatIds: List<Int>, difficultyOffset: Int = 0): StageConfig {
        val seed = stageId.toLong() * 7919L
        val rng = Random(seed)
        val effectiveDifficulty = stageId + difficultyOffset
        val params = getDifficultyParams(effectiveDifficulty)

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
            val structure = buildStructure(rng, template, x, GROUND_HEIGHT, params.materials)
            if (params.tntChance > 0f) {
                structure.copy(blocks = insertTntBlocks(rng, structure.blocks, params.tntChance))
            } else {
                structure
            }
        }
    }

    private fun insertTntBlocks(
        rng: Random,
        blocks: List<BlockPlacement>,
        tntChance: Float
    ): List<BlockPlacement> {
        return blocks.map { block ->
            // Don't convert bottom blocks to TNT (structural base)
            if (block.offsetY > 0.01f && rng.nextFloat() < tntChance) {
                block.copy(material = ObstacleMaterial.TNT)
            } else {
                block
            }
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
        materials: List<ObstacleMaterial>
    ): Structure {
        val blocks = when (template) {
            StructureTemplate.TOWER -> buildTower(rng, materials)
            StructureTemplate.ARCH -> buildArch(rng, materials)
            StructureTemplate.PYRAMID -> buildPyramid(rng, materials)
            StructureTemplate.BRIDGE -> buildBridge(rng, materials)
            StructureTemplate.FORTRESS -> buildFortress(rng, materials)
            StructureTemplate.TALL_TOWER -> buildTallTower(rng, materials)
            StructureTemplate.CASTLE -> buildCastle(rng, materials)
            StructureTemplate.DOUBLE_ARCH -> buildDoubleArch(rng, materials)
            StructureTemplate.PLATFORM_STACK -> buildPlatformStack(rng, materials)
            StructureTemplate.L_SHAPE -> buildLShape(rng, materials)
        }
        return Structure(
            template = template,
            baseX = baseX,
            baseY = baseY,
            blocks = blocks,
            enemyPositions = emptyList()
        )
    }

    private fun randomMaterial(rng: Random, materials: List<ObstacleMaterial>): ObstacleMaterial {
        return materials[rng.nextInt(materials.size)]
    }

    private fun buildTower(
        rng: Random,
        materials: List<ObstacleMaterial>
    ): List<BlockPlacement> {
        val blockCount = 5 + rng.nextInt(4) // 5-8 blocks (was 3-5)
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
        materials: List<ObstacleMaterial>
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
        materials: List<ObstacleMaterial>
    ): List<BlockPlacement> {
        val blockWidth = 0.6f
        val blockHeight = 0.3f
        val blocks = mutableListOf<BlockPlacement>()

        val rows = listOf(4, 3, 2, 1) // wider base (was 3,2,1)
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
        materials: List<ObstacleMaterial>
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
        materials: List<ObstacleMaterial>
    ): List<BlockPlacement> {
        val wallThickness = 0.2f
        val wallWidth = 1.8f    // wider (was 1.5)
        val wallHeight = 1.5f   // taller (was 1.0)

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
                offsetY = wallThickness,
                width = wallThickness,
                height = wallHeight,
                material = randomMaterial(rng, materials)
            ),
            // Right wall
            BlockPlacement(
                offsetX = wallWidth / 2f - wallThickness / 2f,
                offsetY = wallThickness,
                width = wallThickness,
                height = wallHeight,
                material = randomMaterial(rng, materials)
            ),
            // Top wall
            BlockPlacement(
                offsetX = 0f,
                offsetY = wallThickness + wallHeight,
                width = wallWidth,
                height = wallThickness,
                material = randomMaterial(rng, materials)
            )
        )
    }

    private fun buildTallTower(
        rng: Random,
        materials: List<ObstacleMaterial>
    ): List<BlockPlacement> {
        val blocks = mutableListOf<BlockPlacement>()
        // Wide base
        blocks.add(BlockPlacement(0f, 0f, 1.0f, 0.3f, randomMaterial(rng, materials)))
        var y = 0.3f
        val layers = 5 + rng.nextInt(3) // 5-7 alternating layers
        for (i in 0 until layers) {
            val wide = i % 2 == 0
            val w = if (wide) 0.8f else 0.5f
            val h = if (wide) 0.4f else 0.5f
            blocks.add(BlockPlacement(0f, y, w, h, randomMaterial(rng, materials)))
            y += h
        }
        // Cap
        blocks.add(BlockPlacement(0f, y, 0.6f, 0.2f, randomMaterial(rng, materials)))
        return blocks
    }

    private fun buildCastle(
        rng: Random,
        materials: List<ObstacleMaterial>
    ): List<BlockPlacement> {
        val blocks = mutableListOf<BlockPlacement>()
        val wallThick = 0.2f
        val floorH = 0.9f
        val slabH = 0.2f
        val totalW = 2.0f
        val wallSpacing = 0.9f // center of outer walls from structure center

        for (floor in 0 until 2) {
            val y0 = floor * (floorH + slabH)
            // Horizontal slab
            blocks.add(BlockPlacement(0f, y0, totalW, slabH, randomMaterial(rng, materials)))
            // Left wall
            blocks.add(BlockPlacement(-wallSpacing, y0 + slabH, wallThick, floorH, randomMaterial(rng, materials)))
            // Middle wall
            blocks.add(BlockPlacement(0f, y0 + slabH, wallThick, floorH, randomMaterial(rng, materials)))
            // Right wall
            blocks.add(BlockPlacement(wallSpacing, y0 + slabH, wallThick, floorH, randomMaterial(rng, materials)))
        }
        // Roof
        blocks.add(BlockPlacement(0f, 2 * (floorH + slabH), totalW, slabH, randomMaterial(rng, materials)))
        return blocks
    }

    private fun buildDoubleArch(
        rng: Random,
        materials: List<ObstacleMaterial>
    ): List<BlockPlacement> {
        val pillarH = 1.2f
        val pillarW = 0.3f
        val capW = 1.0f
        val capH = 0.3f
        val spacing = 1.0f

        return listOf(
            // Left pillar
            BlockPlacement(-spacing, 0f, pillarW, pillarH, randomMaterial(rng, materials)),
            // Middle pillar
            BlockPlacement(0f, 0f, pillarW, pillarH, randomMaterial(rng, materials)),
            // Right pillar
            BlockPlacement(spacing, 0f, pillarW, pillarH, randomMaterial(rng, materials)),
            // Left cap
            BlockPlacement(-spacing / 2f, pillarH, capW, capH, randomMaterial(rng, materials)),
            // Right cap
            BlockPlacement(spacing / 2f, pillarH, capW, capH, randomMaterial(rng, materials))
        )
    }

    private fun buildPlatformStack(
        rng: Random,
        materials: List<ObstacleMaterial>
    ): List<BlockPlacement> {
        val blocks = mutableListOf<BlockPlacement>()
        val pillarW = 0.2f
        // 3 platforms: (width, thickness, pillarHeight)
        val platforms = listOf(
            Triple(2.0f, 0.15f, 0.8f),
            Triple(1.6f, 0.15f, 0.7f),
            Triple(1.2f, 0.15f, 0.6f)
        )
        var y = 0f
        for ((platW, platH, pilH) in platforms) {
            val pillarSpacing = platW / 2f - pillarW / 2f
            blocks.add(BlockPlacement(-pillarSpacing, y, pillarW, pilH, randomMaterial(rng, materials)))
            blocks.add(BlockPlacement(pillarSpacing, y, pillarW, pilH, randomMaterial(rng, materials)))
            y += pilH
            blocks.add(BlockPlacement(0f, y, platW, platH, randomMaterial(rng, materials)))
            y += platH
        }
        return blocks
    }

    private fun buildLShape(
        rng: Random,
        materials: List<ObstacleMaterial>
    ): List<BlockPlacement> {
        val bw = 0.6f
        val bh = 0.4f
        return listOf(
            // Bottom row (3 wide)
            BlockPlacement(-bw, 0f, bw, bh, randomMaterial(rng, materials)),
            BlockPlacement(0f, 0f, bw, bh, randomMaterial(rng, materials)),
            BlockPlacement(bw, 0f, bw, bh, randomMaterial(rng, materials)),
            // Second row (left only)
            BlockPlacement(-bw, bh, bw, bh, randomMaterial(rng, materials)),
            // Third row (left only)
            BlockPlacement(-bw, bh * 2, bw, bh, randomMaterial(rng, materials)),
            // Cap
            BlockPlacement(-bw, bh * 3, bw, bh * 0.5f, randomMaterial(rng, materials))
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
            val enemyIndex = enemyAssignments[structureIndex].size
            val enemyPos = getEnemyPosition(structure, enemyIndex)
            enemyAssignments[structureIndex].add(enemyPos)
        }

        return structures.mapIndexed { index, structure ->
            structure.copy(enemyPositions = enemyAssignments[index])
        }
    }

    private fun getEnemyPosition(structure: Structure, enemyIndex: Int): Pair<Float, Float> {
        // 같은 구조물에 복수 적 배치 시 좌우로 분산 (겹침 방지)
        val xSpread = when (enemyIndex) {
            0 -> 0f
            else -> {
                val side = if (enemyIndex % 2 == 1) 1f else -1f
                val magnitude = ((enemyIndex + 1) / 2) * 0.6f
                side * magnitude
            }
        }

        return when (structure.template) {
            StructureTemplate.TOWER, StructureTemplate.PYRAMID -> {
                val topBlock = structure.blocks.maxByOrNull { it.offsetY }
                val x = (topBlock?.offsetX ?: 0f) + xSpread
                val y = if (topBlock != null) topBlock.offsetY + topBlock.height else 0f
                Pair(x, y)
            }
            StructureTemplate.ARCH -> {
                val cap = structure.blocks.maxByOrNull { it.offsetY }
                val x = (cap?.offsetX ?: 0f) + xSpread
                val y = if (cap != null) cap.offsetY + cap.height else 0f
                Pair(x, y)
            }
            StructureTemplate.BRIDGE -> {
                // Under the bridge span
                val span = structure.blocks.maxByOrNull { it.offsetY }
                val x = (span?.offsetX ?: 0f) + xSpread
                val y = if (span != null) span.offsetY - 0.3f else 0f
                Pair(x, y)
            }
            StructureTemplate.FORTRESS -> {
                // Inside the fortress
                Pair(0f + xSpread, 0.5f)
            }
            StructureTemplate.TALL_TOWER -> {
                val topBlock = structure.blocks.maxByOrNull { it.offsetY }
                val x = (topBlock?.offsetX ?: 0f) + xSpread
                val y = if (topBlock != null) topBlock.offsetY + topBlock.height else 0f
                Pair(x, y)
            }
            StructureTemplate.CASTLE -> {
                // Inside rooms: alternate between floors and sides
                val floorH = 0.9f
                val slabH = 0.2f
                val roomCenterY = when {
                    enemyIndex < 2 -> slabH + floorH * 0.5f         // first floor
                    else -> (floorH + slabH) + slabH + floorH * 0.5f // second floor
                }
                val side = if (enemyIndex % 2 == 0) -0.45f else 0.45f
                Pair(side, roomCenterY)
            }
            StructureTemplate.DOUBLE_ARCH -> {
                // Under arches — no xSpread to avoid pillar overlap
                val side = if (enemyIndex % 2 == 0) -0.5f else 0.5f
                Pair(side, 0.6f)
            }
            StructureTemplate.PLATFORM_STACK -> {
                // On each platform — clamp to stay inside pillars
                val platformYs = floatArrayOf(0.95f, 1.80f, 2.55f)
                val platformMaxX = floatArrayOf(0.65f, 0.45f, 0.25f)
                val idx = enemyIndex % platformYs.size
                val ex = (xSpread * 0.5f).coerceIn(-platformMaxX[idx], platformMaxX[idx])
                Pair(ex, platformYs[idx])
            }
            StructureTemplate.L_SHAPE -> {
                // On top of bottom row, in the corner of the L
                Pair(0.3f + xSpread * 0.3f, 0.45f)
            }
        }
    }
}
