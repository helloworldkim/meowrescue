package com.meowrescue.game.data

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Story 006 (T-1-8) tests for [SharedPrefsWrapper].
 *
 * Validates all key defaults, round-trip reads/writes, parameter validation,
 * skip-flag isolation, atomic date+today writes, and theme null-removal.
 * Uses Robolectric for a real SharedPreferences backing store without
 * requiring a device.
 *
 * Usage:
 *   ./gradlew :app:testDebugUnitTest --tests com.meowrescue.game.data.SharedPrefsWrapperTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SharedPrefsWrapperTest {

    private lateinit var wrapper: SharedPrefsWrapper
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear before constructing so defaults are exercised from a blank slate.
        // .commit() is acceptable in test setup — never in production code.
        context.getSharedPreferences("meow_rescue", MODE_PRIVATE).edit().clear().commit()
        wrapper = SharedPrefsWrapper(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("meow_rescue", MODE_PRIVATE).edit().clear().commit()
    }

    // ── AC-1: Defaults ────────────────────────────────────────────────────

    @Test
    fun test_getSelectedCatId_default_isOne() {
        assertEquals(1, wrapper.getSelectedCatId())
    }

    @Test
    fun test_isSoundEnabled_default_isTrue() {
        assertTrue(wrapper.isSoundEnabled())
    }

    @Test
    fun test_getEndlessCount_default_isZero() {
        assertEquals(0, wrapper.getEndlessCount())
    }

    @Test
    fun test_getEndlessBest_default_isZero() {
        assertEquals(0, wrapper.getEndlessBest())
    }

    @Test
    fun test_getEndlessCoinDate_default_isEmptyString() {
        assertEquals("", wrapper.getEndlessCoinDate())
    }

    @Test
    fun test_getEndlessCoinToday_default_isZero() {
        assertEquals(0, wrapper.getEndlessCoinToday())
    }

    @Test
    fun test_isTutorialCompleted_default_isFalse() {
        assertFalse(wrapper.isTutorialCompleted())
    }

    @Test
    fun test_getPowerUpUseCount_default_isZero() {
        assertEquals(0, wrapper.getPowerUpUseCount())
    }

    @Test
    fun test_getSelectedThemeId_default_isNull() {
        assertNull(wrapper.getSelectedThemeId())
    }

    @Test
    fun test_isSkipUsed_allWorlds_defaultIsFalse() {
        for (i in 0..6) {
            assertFalse("world $i should default to false", wrapper.isSkipUsed(i))
        }
    }

    // ── AC-2: Round-trip ──────────────────────────────────────────────────

    @Test
    fun test_roundTrip_allKeys_readReturnsWrittenValue() {
        wrapper.setSelectedCatId(7)
        assertEquals(7, wrapper.getSelectedCatId())

        wrapper.setSoundEnabled(false)
        assertFalse(wrapper.isSoundEnabled())

        wrapper.setEndlessCount(42)
        assertEquals(42, wrapper.getEndlessCount())

        // Write-default-value edge case: 0 should still survive a round-trip
        wrapper.setEndlessCount(0)
        assertEquals(0, wrapper.getEndlessCount())

        wrapper.setEndlessBest(999)
        assertEquals(999, wrapper.getEndlessBest())

        wrapper.setEndlessCoinDate("2026-04-19")
        assertEquals("2026-04-19", wrapper.getEndlessCoinDate())

        wrapper.setEndlessCoinToday(75)
        assertEquals(75, wrapper.getEndlessCoinToday())

        wrapper.setTutorialCompleted(true)
        assertTrue(wrapper.isTutorialCompleted())

        wrapper.setSelectedThemeId("neon")
        assertEquals("neon", wrapper.getSelectedThemeId())

        wrapper.setSkipUsed(4)
        assertTrue(wrapper.isSkipUsed(4))
    }

    // ── AC-3: incrementPowerUpUseCount ────────────────────────────────────

    @Test
    fun test_incrementPowerUpUseCount_incrementsByOne() {
        repeat(5) { wrapper.incrementPowerUpUseCount() }
        assertEquals(5, wrapper.getPowerUpUseCount())
    }

    @Test
    fun test_incrementPowerUpUseCount_fromLargeValue_singleCall_incrementsToThousand() {
        // Seed via round-trip setter (no direct prefs access from test)
        repeat(999) { wrapper.incrementPowerUpUseCount() }
        wrapper.incrementPowerUpUseCount()
        assertEquals(1000, wrapper.getPowerUpUseCount())
    }

    @Test
    fun test_incrementPowerUpUseCount_fromLargeValue_fiveCalls_incrementsToFiveAbove() {
        repeat(999) { wrapper.incrementPowerUpUseCount() }
        repeat(5) { wrapper.incrementPowerUpUseCount() }
        assertEquals(1004, wrapper.getPowerUpUseCount())
    }

    // ── AC-4: Parameter validation ────────────────────────────────────────

    @Test
    fun test_isSkipUsed_negativeIndex_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            wrapper.isSkipUsed(-1)
        }
    }

    @Test
    fun test_isSkipUsed_indexSeven_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            wrapper.isSkipUsed(7)
        }
    }

    @Test
    fun test_setSkipUsed_negativeIndex_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            wrapper.setSkipUsed(-1)
        }
    }

    @Test
    fun test_setSkipUsed_indexSeven_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            wrapper.setSkipUsed(7)
        }
    }

    @Test
    fun test_isSkipUsed_boundaryIndices_0and6_succeed() {
        assertFalse(wrapper.isSkipUsed(0))
        assertFalse(wrapper.isSkipUsed(6))
    }

    // ── AC-5: Skip flag isolation ─────────────────────────────────────────

    @Test
    fun test_setSkipUsed_affectsOnlyTargetWorld() {
        wrapper.setSkipUsed(3)

        assertTrue(wrapper.isSkipUsed(3))
        for (i in 0..6) {
            if (i != 3) assertFalse("world $i should still be false after setSkipUsed(3)", wrapper.isSkipUsed(i))
        }
    }

    @Test
    fun test_setSkipUsed_allSeven_eachReadsTrue() {
        for (i in 0..6) wrapper.setSkipUsed(i)
        for (i in 0..6) assertTrue("world $i should be true", wrapper.isSkipUsed(i))
    }

    // ── AC-6: Theme null-removal ──────────────────────────────────────────

    @Test
    fun test_setSelectedThemeId_nonNull_thenRead_returnsValue() {
        wrapper.setSelectedThemeId("neon")
        assertEquals("neon", wrapper.getSelectedThemeId())
    }

    @Test
    fun test_setSelectedThemeId_null_removesKey_subsequentReadReturnsNull() {
        wrapper.setSelectedThemeId("neon")
        assertEquals("neon", wrapper.getSelectedThemeId())

        wrapper.setSelectedThemeId(null)
        assertNull(wrapper.getSelectedThemeId())
    }

    @Test
    fun test_setSelectedThemeId_null_onAlreadyNullState_noOp() {
        // Clean state — key was never written
        wrapper.setSelectedThemeId(null)
        assertNull(wrapper.getSelectedThemeId())
    }

    // ── AC-bonus: setEndlessCoinDateAndToday atomic write ─────────────────

    @Test
    fun test_setEndlessCoinDateAndToday_writesBothKeysAtomically() {
        wrapper.setEndlessCoinDateAndToday("2026-04-19", 120)
        assertEquals("2026-04-19", wrapper.getEndlessCoinDate())
        assertEquals(120, wrapper.getEndlessCoinToday())
    }

    @Test
    fun test_setEndlessCoinDateAndToday_overwritesPreviousValues() {
        wrapper.setEndlessCoinDateAndToday("2026-04-18", 50)
        wrapper.setEndlessCoinDateAndToday("2026-04-19", 10)
        assertEquals("2026-04-19", wrapper.getEndlessCoinDate())
        assertEquals(10, wrapper.getEndlessCoinToday())
    }
}
