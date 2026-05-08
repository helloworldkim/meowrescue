package com.meowrescue.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldTest {

    // ── Boundary stages ───────────────────────────────────────────────────

    @Test fun stage1_isGarden() = assertEquals(World.GARDEN, World.fromStage(1))
    @Test fun stage30_isGarden() = assertEquals(World.GARDEN, World.fromStage(30))
    @Test fun stage31_isBeach() = assertEquals(World.BEACH, World.fromStage(31))
    @Test fun stage60_isBeach() = assertEquals(World.BEACH, World.fromStage(60))
    @Test fun stage61_isForest() = assertEquals(World.FOREST, World.fromStage(61))
    @Test fun stage90_isForest() = assertEquals(World.FOREST, World.fromStage(90))
    @Test fun stage91_isSnow() = assertEquals(World.SNOW, World.fromStage(91))
    @Test fun stage120_isSnow() = assertEquals(World.SNOW, World.fromStage(120))
    @Test fun stage121_isVolcano() = assertEquals(World.VOLCANO, World.fromStage(121))
    @Test fun stage150_isVolcano() = assertEquals(World.VOLCANO, World.fromStage(150))
    @Test fun stage151_isSpace() = assertEquals(World.SPACE, World.fromStage(151))
    @Test fun stage180_isSpace() = assertEquals(World.SPACE, World.fromStage(180))
    @Test fun stage181_isCastle() = assertEquals(World.CASTLE, World.fromStage(181))
    @Test fun stage200_isCastle() = assertEquals(World.CASTLE, World.fromStage(200))

    // ── Clamping ──────────────────────────────────────────────────────────

    @Test fun stage0_clampsToGarden() = assertEquals(World.GARDEN, World.fromStage(0))
    @Test fun stageNegative_clampsToGarden() = assertEquals(World.GARDEN, World.fromStage(-5))
    @Test fun stage210_clampsToCastle() = assertEquals(World.CASTLE, World.fromStage(210))
    @Test fun stage999_clampsToCastle() = assertEquals(World.CASTLE, World.fromStage(999))

    // ── World metadata ────────────────────────────────────────────────────

    @Test fun worldCount_is7() = assertEquals(7, World.entries.size)

    @Test fun worldIndices_areSequential() {
        World.entries.forEachIndexed { i, w -> assertEquals(i + 1, w.index) }
    }

    @Test fun gardenNames() {
        assertEquals("정원", World.GARDEN.nameKr)
        assertEquals("Garden", World.GARDEN.nameEn)
    }

    @Test fun castleNames() {
        assertEquals("성", World.CASTLE.nameKr)
        assertEquals("Castle", World.CASTLE.nameEn)
    }
}
