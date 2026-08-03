package com.latent.camera.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Capture provenance is the one thing in this database that cannot be regenerated: a
 * JPEG on disk does not say which frame it came from. A migration that quietly drops
 * the captures table would take every future re-grade with it, so the migration is
 * asserted rather than assumed.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LatentDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsCapturesAndAddsRecipes() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO captures
                    (stem, output_uri, source_uri, recipe_name, captured_at,
                     rotation_degrees, source_width, source_height)
                VALUES
                    ('LATENT_20260726_120000_000', 'content://out/1', 'content://src/1',
                     'Neutral', 1785000000000, 90, 4080, 3060)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            *LatentDatabase.MIGRATIONS,
        )

        migrated.query("SELECT source_uri, rotation_degrees FROM captures").use { cursor ->
            assertTrue("The capture row did not survive the migration", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals("content://src/1", cursor.getString(0))
            assertEquals(90, cursor.getInt(1))
        }

        // Present and writable, which is more than the schema validator checks.
        migrated.execSQL(
            """
            INSERT INTO recipes
                (id, name, position, lift, gamma, gain, contrast, clarity, clarityRadius,
                 halation, halationThreshold, halationRadius, grain, grainSize, toning,
                 vignette, mix_r, mix_g, mix_b)
            VALUES ('t', 'Test', 0, 0, 1, 1, 0, 0, 0.02, 0, 0.75, 0.05, 0, 1.5, 0, 0,
                    0.2126, 0.7152, 0.0722)
            """.trimIndent(),
        )
        migrated.query("SELECT name FROM recipes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test", cursor.getString(0))
        }
        migrated.close()
    }

    /**
     * Phase 5 adds the DNG URI and the re-grade back-reference. Both are nullable, so
     * the interesting assertion is not that the columns exist — the schema validator
     * covers that — but that rows written before Phase 5 still carry their pairing
     * and still report themselves as re-gradable.
     */
    @Test
    fun migrate2To3_keepsPairingAndAddsPhase5Columns() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, *LatentDatabase.MIGRATIONS).use { db ->
            db.execSQL(
                """
                INSERT INTO captures
                    (stem, output_uri, source_uri, recipe_name, captured_at,
                     rotation_degrees, source_width, source_height)
                VALUES
                    ('LATENT_20260726_120000_000', 'content://out/1', 'content://src/1',
                     'Neutral', 1785000000000, 90, 4080, 3060)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            *LatentDatabase.MIGRATIONS,
        )

        migrated.query("SELECT source_uri, dng_uri, regraded_from FROM captures").use { cursor ->
            assertTrue("The capture row did not survive the migration", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals("content://src/1", cursor.getString(0))
            assertTrue("A pre-Phase-5 row cannot have had a DNG", cursor.isNull(1))
            assertTrue("A pre-Phase-5 row cannot have been re-graded", cursor.isNull(2))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
