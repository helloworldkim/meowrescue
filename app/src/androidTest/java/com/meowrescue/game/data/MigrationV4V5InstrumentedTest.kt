package com.meowrescue.game.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Story 010 instrumented migration tests for v4→v5 using MigrationTestHelper.
 *
 * Creates a real v4 SQLite database on-device, runs MIGRATION_4_5, and validates
 * the resulting schema against Room's generated hash. This exercises the actual
 * DROP/CREATE SQL path that Robolectric inMemoryDatabaseBuilder skips.
 *
 * See coding-standards.md §"Room Migration Test Standard" for why both layers are required.
 *
 * Usage:
 *   ./gradlew :app:connectedDebugAndroidTest --tests "com.meowrescue.game.data.MigrationV4V5InstrumentedTest"
 */
@RunWith(AndroidJUnit4::class)
class MigrationV4V5InstrumentedTest {

    companion object {
        private const val TEST_DB = "migration_v4_v5_test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrationV4_to_V5_schemaValid() {
        helper.createDatabase(TEST_DB, 4).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
        db.close()
    }

    @Test
    fun migrationV4_to_V5_selectedCosmeticsAcceptsInsert() {
        helper.createDatabase(TEST_DB, 4).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
        db.execSQL("INSERT INTO selected_cosmetics (catId, cosmeticId) VALUES (1, 42)")
        val cursor = db.query("SELECT cosmeticId FROM selected_cosmetics WHERE catId = 1")
        cursor.use { assertEquals(1, it.count) }
        db.close()
    }

    @Test
    fun migrationV4_to_V5_themeUnlocksAcceptsInsert() {
        helper.createDatabase(TEST_DB, 4).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
        db.execSQL("INSERT INTO theme_unlocks (themeId, purchasedAt) VALUES ('ocean', 9999)")
        val cursor = db.query("SELECT purchasedAt FROM theme_unlocks WHERE themeId = 'ocean'")
        cursor.use { assertEquals(1, it.count) }
        db.close()
    }

    @Test
    fun migrationChain_v3_to_v5_schemaValid() {
        helper.createDatabase(TEST_DB, 3).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_3_4, MIGRATION_4_5)
        db.close()
    }
}
