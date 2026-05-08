package com.meowrescue.game.core.progression

import com.meowrescue.game.data.FakeGameRepository
import com.meowrescue.game.puzzle.model.WorldTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WorldThemeUnlockTest {

    private lateinit var repo: FakeGameRepository
    private lateinit var resolver: ThemeResolver

    @Before
    fun setUp() {
        repo = FakeGameRepository()
        resolver = ThemeResolver(repo)
    }

    // ── forStage boundary values (GDD acceptance criterion 17) ────────────

    @Test fun `stage 1 → index 0 (Garden)`()   = assertEquals(0, WorldTheme.forStage(1).worldIndex)
    @Test fun `stage 30 → index 0 (Garden)`()  = assertEquals(0, WorldTheme.forStage(30).worldIndex)
    @Test fun `stage 31 → index 1 (Beach)`()   = assertEquals(1, WorldTheme.forStage(31).worldIndex)
    @Test fun `stage 60 → index 1 (Beach)`()   = assertEquals(1, WorldTheme.forStage(60).worldIndex)
    @Test fun `stage 61 → index 2 (Forest)`()  = assertEquals(2, WorldTheme.forStage(61).worldIndex)
    @Test fun `stage 90 → index 2 (Forest)`()  = assertEquals(2, WorldTheme.forStage(90).worldIndex)
    @Test fun `stage 91 → index 3 (Snow)`()    = assertEquals(3, WorldTheme.forStage(91).worldIndex)
    @Test fun `stage 120 → index 3 (Snow)`()   = assertEquals(3, WorldTheme.forStage(120).worldIndex)
    @Test fun `stage 121 → index 4 (Volcano)`()= assertEquals(4, WorldTheme.forStage(121).worldIndex)
    @Test fun `stage 150 → index 4 (Volcano)`()= assertEquals(4, WorldTheme.forStage(150).worldIndex)
    @Test fun `stage 151 → index 5 (Space)`()  = assertEquals(5, WorldTheme.forStage(151).worldIndex)
    @Test fun `stage 180 → index 5 (Space)`()  = assertEquals(5, WorldTheme.forStage(180).worldIndex)
    @Test fun `stage 181 → index 6 (Castle)`() = assertEquals(6, WorldTheme.forStage(181).worldIndex)
    @Test fun `stage 200 → index 6 (Castle)`() = assertEquals(6, WorldTheme.forStage(200).worldIndex)

    // ── Invalid input guards ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `forStage(0) throws`() { WorldTheme.forStage(0) }

    @Test(expected = IllegalArgumentException::class)
    fun `forStage(-1) throws`() { WorldTheme.forStage(-1) }

    @Test(expected = IllegalArgumentException::class)
    fun `forEndlessStage(0) throws`() { WorldTheme.forEndlessStage(0) }

    // ── Endless mode cycling ──────────────────────────────────────────────

    @Test fun `endless stage 7 wraps to index 0`()   = assertEquals(0, WorldTheme.forEndlessStage(7).worldIndex)
    @Test fun `endless stage 131 mod 7 equals 5`()    = assertEquals(5, WorldTheme.forEndlessStage(131).worldIndex)
    @Test fun `endless stage 1 → index 1`()           = assertEquals(1, WorldTheme.forEndlessStage(1).worldIndex)
    @Test fun `endless stage 8 → index 1`()           = assertEquals(1, WorldTheme.forEndlessStage(8).worldIndex)

    // ── ThemeResolver: progression-based (no override) ────────────────────

    @Test
    fun `no override returns progression theme`() {
        assertNull(repo.getSelectedThemeId())
        val theme = resolver.forStage(31)
        assertEquals(1, theme.worldIndex) // Beach
    }

    // ── ThemeResolver: cosmetic override persists ─────────────────────────

    @Test
    fun `setOverride persists worldIndex and forStage returns it`() {
        resolver.setOverride(4) // Volcano override
        assertEquals("4", repo.getSelectedThemeId())
        assertEquals(4, resolver.forStage(1).worldIndex) // stage 1 normally Garden, but override=4
    }

    @Test
    fun `clearOverride restores progression theme`() {
        resolver.setOverride(4)
        resolver.clearOverride()
        assertNull(repo.getSelectedThemeId())
        assertEquals(0, resolver.forStage(1).worldIndex) // back to Garden
    }

    @Test
    fun `invalid override string falls back to progression theme`() {
        repo.setSelectedThemeId("notAnInt")
        assertEquals(0, resolver.forStage(1).worldIndex) // falls back to Garden
    }
}
