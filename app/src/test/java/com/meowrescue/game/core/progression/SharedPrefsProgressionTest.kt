package com.meowrescue.game.core.progression

import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.data.FakeGameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SharedPrefs delegation methods on [ProgressionManager].
 *
 * Implements story-004: SharedPrefs persistence — endless count/best, tutorial, selected cat.
 * Implements: design/gdd/progression-system.md — TR-prog-008, TR-prog-009
 *
 * No Android framework — runs on plain JVM via FakeGameRepository.
 */
class SharedPrefsProgressionTest {

    private fun makeManager(): ProgressionManager {
        val repo = FakeGameRepository()
        return ProgressionManager(repo, EconomyManager(repo))
    }

    // ── Endless count ─────────────────────────────────────────────────────

    // AC: default 0 on fresh install
    @Test
    fun test_getEndlessCount_freshInstall_returnsZero() {
        assertEquals(0, makeManager().getEndlessCount())
    }

    // AC: setEndlessCount persists value readable by getEndlessCount
    @Test
    fun test_setEndlessCount_persistsAndReadBack() {
        val pm = makeManager()
        pm.setEndlessCount(5)
        assertEquals(5, pm.getEndlessCount())
    }

    // ── Endless best ──────────────────────────────────────────────────────

    // AC: default 0 on fresh install
    @Test
    fun test_getEndlessBest_freshInstall_returnsZero() {
        assertEquals(0, makeManager().getEndlessBest())
    }

    // AC: updateEndlessBest — higher score updates stored value
    @Test
    fun test_updateEndlessBest_higherScore_updates() {
        val pm = makeManager()
        pm.updateEndlessBest(100)
        pm.updateEndlessBest(150)
        assertEquals(150, pm.getEndlessBest())
    }

    // AC: updateEndlessBest — lower score is a no-op
    @Test
    fun test_updateEndlessBest_lowerScore_noOp() {
        val pm = makeManager()
        pm.updateEndlessBest(100)
        pm.updateEndlessBest(80)
        assertEquals(100, pm.getEndlessBest())
    }

    // AC: updateEndlessBest — equal score (tie) is a no-op
    @Test
    fun test_updateEndlessBest_tieScore_noOp() {
        val pm = makeManager()
        pm.updateEndlessBest(100)
        pm.updateEndlessBest(100)
        assertEquals(100, pm.getEndlessBest())
    }

    // ── Tutorial ──────────────────────────────────────────────────────────

    // AC: default false on fresh install
    @Test
    fun test_isTutorialCompleted_freshInstall_returnsFalse() {
        assertFalse(makeManager().isTutorialCompleted())
    }

    // AC: setTutorialCompleted persists value
    @Test
    fun test_setTutorialCompleted_persistsTrue() {
        val pm = makeManager()
        pm.setTutorialCompleted(true)
        assertTrue(pm.isTutorialCompleted())
    }

    // ── Selected cat ──────────────────────────────────────────────────────

    // AC: setSelectedCatId round-trip
    @Test
    fun test_setSelectedCatId_roundTrip() {
        val pm = makeManager()
        pm.setSelectedCatId(7)
        assertEquals(7, pm.getSelectedCatId())
    }
}
