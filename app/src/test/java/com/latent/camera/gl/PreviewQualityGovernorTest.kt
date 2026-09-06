package com.latent.camera.gl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview may shed grain octaves under load, but never nervously: degrading
 * takes a sustained stall and recovering takes a sustained healthy run. The file
 * always renders full detail, so none of this affects what gets saved.
 */
class PreviewQualityGovernorTest {

    private fun governor() = PreviewQualityGovernor(
        slowThresholdNs = 24_000_000L,
        slowFramesToDegrade = 30,
        healthyThresholdNs = 14_000_000L,
        healthyFramesToRestore = 120,
    )

    @Test
    fun fullDetailByDefault() {
        assertFalse(governor().reduced)
    }

    @Test
    fun healthyFramesNeverDegrade() {
        val governor = governor()
        repeat(600) { governor.sample(8_000_000L) }
        assertFalse(governor.reduced)
    }

    @Test
    fun aSustainedStallDegrades() {
        val governor = governor()
        repeat(29) { governor.sample(30_000_000L) }
        assertFalse("29 slow frames must not degrade yet", governor.reduced)
        governor.sample(30_000_000L)
        assertTrue(governor.reduced)
    }

    @Test
    fun anInterruptedStallDoesNotDegrade() {
        // 29 slow, one healthy, 29 slow: never 30 consecutive, never degraded.
        val governor = governor()
        repeat(29) { governor.sample(30_000_000L) }
        governor.sample(8_000_000L)
        repeat(29) { governor.sample(30_000_000L) }
        assertFalse(governor.reduced)
    }

    @Test
    fun recoveryTakesASustainedHealthyRun() {
        val governor = governor()
        repeat(30) { governor.sample(30_000_000L) }
        assertTrue(governor.reduced)
        repeat(119) { governor.sample(8_000_000L) }
        assertTrue("119 healthy frames must not restore yet", governor.reduced)
        governor.sample(8_000_000L)
        assertFalse(governor.reduced)
    }

    @Test
    fun aSlowFrameResetsRecovery() {
        val governor = governor()
        repeat(30) { governor.sample(30_000_000L) }
        repeat(119) { governor.sample(8_000_000L) }
        governor.sample(30_000_000L)
        repeat(119) { governor.sample(8_000_000L) }
        assertTrue("one slow frame must restart the healthy count", governor.reduced)
    }

    @Test
    fun borderlineFramesCountAsSlow() {
        val governor = governor()
        repeat(30) { governor.sample(24_000_000L) }
        assertTrue(governor.reduced)
    }
}
