package com.latent.camera.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.latent.camera.gl.OffscreenRenderer
import com.latent.camera.look.Recipe
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Where the time between the shutter and the saved file actually goes, measured on
 * the device rather than guessed at.
 *
 * Not an assertion of a budget — the numbers differ by an order of magnitude across
 * hardware. This exists so that "capture feels slow" can be answered with a stage
 * breakdown instead of an optimisation guess.
 */
@RunWith(AndroidJUnit4::class)
class CaptureBudgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reportStageTimings() {
        val jpeg = syntheticFrame(SOURCE_WIDTH, SOURCE_HEIGHT)
        Log.i(TAG, "source frame ${SOURCE_WIDTH}x$SOURCE_HEIGHT, ${jpeg.size / 1024}KB")

        val renderer = OffscreenRenderer(context.assets)
        try {
            repeat(RUNS) { run ->
                var t = SystemClock.elapsedRealtime()
                val decoded = BitmapFactory.decodeByteArray(
                    jpeg, 0, jpeg.size,
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                )
                val decodeMs = SystemClock.elapsedRealtime() - t

                t = SystemClock.elapsedRealtime()
                val graded = renderer.render(decoded, rotationDegrees = 90, recipe = Recipe.Default)
                val gradeMs = SystemClock.elapsedRealtime() - t
                decoded.recycle()

                val encodeMs = LongArray(QUALITIES.size)
                val sizes = IntArray(QUALITIES.size)
                QUALITIES.forEachIndexed { i, quality ->
                    t = SystemClock.elapsedRealtime()
                    val bytes = ByteArrayOutputStream().use { out ->
                        graded.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        out.toByteArray()
                    }
                    encodeMs[i] = SystemClock.elapsedRealtime() - t
                    sizes[i] = bytes.size
                }

                Log.i(
                    TAG,
                    "run $run: decode=${decodeMs}ms grade=${gradeMs}ms " +
                        QUALITIES.indices.joinToString(" ") {
                            "q${QUALITIES[it]}=${encodeMs[it]}ms/${sizes[it] / 1024}KB"
                        } +
                        " out=${graded.width}x${graded.height}",
                )
                graded.recycle()
            }
        } finally {
            renderer.release()
        }
    }

    /** Noise-heavy, so it encodes at a realistic size rather than a flat-field best case. */
    private fun syntheticFrame(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(7)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val base = (x * 255 / width)
                val noise = random.nextInt(64) - 32
                val v = (base + noise).coerceIn(0, 255)
                row[x] = 0xFF shl 24 or (v shl 16) or ((255 - v) shl 8) or ((v + y) and 0xFF)
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private companion object {
        const val TAG = "CaptureBudget"

        /** What the phone actually captures: a 12.5MP 4:3 frame. */
        const val SOURCE_WIDTH = 4080
        const val SOURCE_HEIGHT = 3060

        val QUALITIES = intArrayOf(96, 92, 88)
        const val RUNS = 3
    }
}
