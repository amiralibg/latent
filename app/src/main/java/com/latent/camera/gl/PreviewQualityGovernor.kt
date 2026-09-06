package com.latent.camera.gl

/**
 * Decides when the preview may render the reduced grain field.
 *
 * Grain is the most expensive stage of the look — three octaves of value noise per
 * pixel at 60fps — and the first thing worth shedding when the GL thread falls
 * behind. This class holds the policy and no GL: feed it frame times, read
 * [reduced]. Hysteresis in both directions so the grain does not visibly breathe:
 * degrading takes a sustained stall, recovering takes a sustained healthy run, and
 * any slow frame resets the recovery count.
 *
 * The capture path never consults this. Files always render full detail, which is
 * why the golden test compares full against full regardless of what the preview
 * was doing.
 */
internal class PreviewQualityGovernor(
    private val slowThresholdNs: Long = SLOW_THRESHOLD_NS,
    private val slowFramesToDegrade: Int = SLOW_FRAMES_TO_DEGRADE,
    private val healthyThresholdNs: Long = HEALTHY_THRESHOLD_NS,
    private val healthyFramesToRestore: Int = HEALTHY_FRAMES_TO_RESTORE,
) {

    /** True once degraded, until a sustained healthy run restores full detail. */
    var reduced: Boolean = false
        private set

    private var slowStreak = 0
    private var healthyStreak = 0

    fun sample(frameNs: Long) {
        if (!reduced) {
            slowStreak = if (frameNs >= slowThresholdNs) slowStreak + 1 else 0
            if (slowStreak >= slowFramesToDegrade) {
                reduced = true
                slowStreak = 0
                healthyStreak = 0
            }
            return
        }
        if (frameNs <= healthyThresholdNs) {
            healthyStreak++
            if (healthyStreak >= healthyFramesToRestore) {
                reduced = false
                healthyStreak = 0
                slowStreak = 0
            }
        } else {
            // Not healthy enough to count towards recovery — and a genuinely slow
            // frame means pressure is back, so any accumulated health is discarded.
            healthyStreak = 0
        }
    }

    internal companion object {
        /** A frame that took this long means the GL thread is missing vsync. */
        const val SLOW_THRESHOLD_NS = 24_000_000L
        /** ~half a second of consecutive stalls before shedding load. */
        const val SLOW_FRAMES_TO_DEGRADE = 30
        /** Comfortably inside a 60fps budget with headroom for the aids pass. */
        const val HEALTHY_THRESHOLD_NS = 14_000_000L
        /** ~two seconds of clean frames before trusting full detail again. */
        const val HEALTHY_FRAMES_TO_RESTORE = 120
    }
}
