package com.latent.camera.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pairing that makes re-grade possible, asserted at the table.
 *
 * Re-grade is defined as re-rendering from the stored original, so every guarantee it
 * offers rests on these rows: which output came from which frame, and — once a frame
 * has been rendered more than once — how many outputs are still relying on that
 * original being on disk. Provenance cannot be reconstructed after the fact, so a
 * regression here is not recoverable by re-scanning anything.
 */
@RunWith(AndroidJUnit4::class)
class CaptureProvenanceTest {

    private lateinit var database: LatentDatabase
    private lateinit var dao: CaptureDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // In memory: this is about the schema's semantics, not about the disk.
        database = Room.inMemoryDatabaseBuilder(context, LatentDatabase::class.java).build()
        dao = database.captures()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aShutterCaptureRecordsItsOriginal() = runBlocking {
        dao.insert(capture(stem = SHOT, sourceUri = SOURCE))

        val stored = dao.find(SHOT)
        assertEquals(SOURCE, stored?.sourceUri)
        assertNull("A shutter capture was not re-graded from anything", stored?.regradedFrom)
        assertTrue(stored?.canRegrade == true)
    }

    @Test
    fun aCaptureSavedWithoutItsOriginalCannotBeRegraded() = runBlocking {
        dao.insert(capture(stem = SHOT, sourceUri = null))
        assertFalse(dao.find(SHOT)?.canRegrade == true)
    }

    /**
     * A re-grade is a new row, not an edit. The earlier render stays exactly as it
     * was, because the original is untouched and both are equally valid outputs of it.
     */
    @Test
    fun aRegradeAddsARowAndLeavesTheEarlierRenderAlone() = runBlocking {
        dao.insert(capture(stem = SHOT, sourceUri = SOURCE, recipeName = "Neutral"))
        dao.insert(
            capture(
                stem = REGRADE,
                sourceUri = SOURCE,
                recipeName = "Red Sky",
                regradedFrom = SHOT,
            ),
        )

        assertEquals(2, dao.observeAll().first().size)

        val original = dao.find(SHOT)
        assertEquals("Neutral", original?.recipeName)
        assertEquals("content://out/$SHOT", original?.outputUri)

        val regrade = dao.find(REGRADE)
        assertEquals("Red Sky", regrade?.recipeName)
        assertEquals(SHOT, regrade?.regradedFrom)
        assertEquals("Both renders come from the same frame", SOURCE, regrade?.sourceUri)
        assertTrue("A re-grade can itself be re-graded", regrade?.canRegrade == true)
    }

    // ------------------------------------------------------------------ shared originals

    /**
     * The query the gallery's delete depends on.
     *
     * A re-grade and the shot it came from point at one file. Deleting either render
     * must not take that file with it while the other still needs it — which is the
     * difference between removing a picture and quietly making another one
     * unrenderable forever.
     */
    @Test
    fun aSharedOriginalIsReportedAsStillInUse() = runBlocking {
        dao.insert(capture(stem = SHOT, sourceUri = SOURCE))
        dao.insert(capture(stem = REGRADE, sourceUri = SOURCE, regradedFrom = SHOT))

        assertEquals(1, dao.countSharingSource(SOURCE, excludingStem = SHOT))
        assertEquals(1, dao.countSharingSource(SOURCE, excludingStem = REGRADE))
    }

    @Test
    fun anUnsharedOriginalIsReportedAsFreeToDelete() = runBlocking {
        dao.insert(capture(stem = SHOT, sourceUri = SOURCE))

        assertEquals(0, dao.countSharingSource(SOURCE, excludingStem = SHOT))
    }

    /**
     * Deleting one of a pair releases the original for the survivor's eventual delete
     * — otherwise the file would outlive every row that referenced it.
     */
    @Test
    fun deletingOneOfAPairFreesTheOriginalForTheOther() = runBlocking {
        dao.insert(capture(stem = SHOT, sourceUri = SOURCE))
        dao.insert(capture(stem = REGRADE, sourceUri = SOURCE, regradedFrom = SHOT))

        dao.delete(REGRADE)

        assertEquals(0, dao.countSharingSource(SOURCE, excludingStem = SHOT))
    }

    /** Two separate shots must never be read as sharing anything. */
    @Test
    fun unrelatedCapturesDoNotCountAsSharing() = runBlocking {
        dao.insert(capture(stem = SHOT, sourceUri = SOURCE))
        dao.insert(capture(stem = "LATENT_OTHER", sourceUri = "content://src/other"))

        assertEquals(0, dao.countSharingSource(SOURCE, excludingStem = SHOT))
    }

    // ------------------------------------------------------------------ ordering

    /** Newest first is the shape the contact sheet and the shutter thumbnail want. */
    @Test
    fun capturesComeBackNewestFirst() = runBlocking {
        dao.insert(capture(stem = "LATENT_OLD", capturedAt = 1_000L))
        dao.insert(capture(stem = "LATENT_NEW", capturedAt = 3_000L))
        dao.insert(capture(stem = "LATENT_MID", capturedAt = 2_000L))

        assertEquals(
            listOf("LATENT_NEW", "LATENT_MID", "LATENT_OLD"),
            dao.observeAll().first().map { it.stem },
        )
        assertEquals("LATENT_NEW", dao.observeLatest().first()?.stem)
    }

    @Test
    fun theLatestIsNullBeforeAnythingIsShot() = runBlocking {
        assertNull(dao.observeLatest().first())
    }

    private fun capture(
        stem: String = SHOT,
        sourceUri: String? = SOURCE,
        dngUri: String? = null,
        recipeName: String = "Neutral",
        capturedAt: Long = 1_785_000_000_000L,
        regradedFrom: String? = null,
    ) = CaptureRecord(
        stem = stem,
        outputUri = "content://out/$stem",
        sourceUri = sourceUri,
        dngUri = dngUri,
        recipeName = recipeName,
        capturedAt = capturedAt,
        rotationDegrees = 90,
        sourceWidth = 4080,
        sourceHeight = 3060,
        regradedFrom = regradedFrom,
    )

    private companion object {
        const val SHOT = "LATENT_20260801_143000_123"
        const val REGRADE = "LATENT_20260801_150000_456"
        const val SOURCE = "content://src/1"
    }
}
