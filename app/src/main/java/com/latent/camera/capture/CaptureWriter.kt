package com.latent.camera.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.latent.camera.data.CaptureDao
import com.latent.camera.data.CaptureRecord
import com.latent.camera.gl.OffscreenRenderer
import com.latent.camera.look.Recipe
import com.latent.camera.settings.SaveMode
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * Everything that happens to a frame after the shutter: the untouched original goes
 * to disk first, then the same bytes are graded through the offscreen path and the
 * 1:1 output is written with the shooting data carried across and the pair recorded.
 *
 * Thread confined, because it owns an [OffscreenRenderer] and therefore an EGL
 * context. Construct it on the capture-processing thread and only call it there.
 * None of this is on the capture path — the shutter has already returned.
 */
internal class CaptureWriter(
    private val context: Context,
    private val dao: CaptureDao,
) {

    private val renderer = OffscreenRenderer(context.assets)

    /**
     * The ordinary shutter path: full-resolution JPEG in memory, nothing on disk yet.
     *
     * @param jpeg the frame exactly as ImageCapture produced it
     * @return the URI of the graded output, or null if it could not be written
     */
    suspend fun save(
        jpeg: ByteArray,
        recipe: Recipe,
        capturedAt: Long,
        saveMode: SaveMode,
    ): Uri? {
        val stem = CaptureNaming.stem(Date(capturedAt))

        // The original goes down first and is never rewritten. If anything below
        // fails the frame still exists at full quality and can be graded later.
        val sourceUri = if (saveMode.keepsOriginal) {
            CaptureStore.write(
                context = context,
                fileName = CaptureNaming.originalFileName(stem),
                bytes = jpeg,
                inOriginals = true,
            )
        } else {
            null
        }

        return grade(
            jpeg = jpeg,
            recipe = recipe,
            stem = stem,
            capturedAt = capturedAt,
            sourceUri = sourceUri,
            dngUri = null,
            regradedFrom = null,
        )
    }

    /**
     * The DNG path. CameraX has already written both the DNG and the untouched JPEG
     * itself — the two-file `takePicture` overload is the only way to get a DNG — so
     * the source is read back off disk rather than handed over in memory.
     *
     * That read-back only happens in DNG mode, which is the slow mode by nature.
     * Paying it here is what keeps the ordinary shutter path allocation-free.
     */
    suspend fun saveFromDisk(
        sourceUri: Uri,
        dngUri: Uri?,
        recipe: Recipe,
        capturedAt: Long,
    ): Uri? {
        val stem = CaptureNaming.stem(Date(capturedAt))
        val jpeg = readBytes(sourceUri) ?: run {
            Log.e(TAG, "Could not read back the saved frame at $sourceUri")
            return null
        }
        return grade(
            jpeg = jpeg,
            recipe = recipe,
            stem = stem,
            capturedAt = capturedAt,
            sourceUri = sourceUri,
            dngUri = dngUri,
            regradedFrom = null,
        )
    }

    /**
     * Render an existing capture again under a different recipe.
     *
     * This reads the stored original, not the previous output — a re-grade is a fresh
     * render of the frame the sensor gave up, so grading is never compounded and the
     * result is exactly what the shutter would have produced under this recipe. It
     * writes a new file and a new row; the earlier render is left alone.
     *
     * @return the URI of the new output, or null if the source is gone
     */
    suspend fun regrade(record: CaptureRecord, recipe: Recipe, at: Long): Uri? {
        val sourceUri = record.sourceUri?.let(Uri::parse) ?: return null
        val jpeg = readBytes(sourceUri) ?: run {
            Log.e(TAG, "Original for ${record.stem} is gone; cannot re-grade")
            return null
        }
        return grade(
            jpeg = jpeg,
            recipe = recipe,
            stem = CaptureNaming.stem(Date(at)),
            capturedAt = at,
            // The new render points at the same original, so it can be re-graded too.
            sourceUri = sourceUri,
            dngUri = record.dngUri?.let(Uri::parse),
            regradedFrom = record.stem,
        )
    }

    /**
     * The one grading path. Every route into this class — shutter, DNG, re-grade —
     * lands here, so there is a single place where a frame becomes an output file and
     * a row, and no route can quietly acquire its own semantics.
     */
    private suspend fun grade(
        jpeg: ByteArray,
        recipe: Recipe,
        stem: String,
        capturedAt: Long,
        sourceUri: Uri?,
        dngUri: Uri?,
        regradedFrom: String?,
    ): Uri? {
        val trace = Trace()
        val rotation = CaptureExif.rotationDegrees(jpeg)

        val source = decode(jpeg) ?: run {
            Log.e(TAG, "Could not decode the captured frame")
            return null
        }
        val sourceWidth = source.width
        val sourceHeight = source.height
        trace.mark("decode")

        val graded = try {
            renderer.render(source, rotation, recipe)
        } finally {
            source.recycle()
        }
        trace.mark("grade")

        val encoded = ByteArrayOutputStream(graded.byteCount / 4).use { stream ->
            graded.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }
        val side = graded.width
        graded.recycle()
        trace.mark("encode")

        val outputUri = CaptureStore.write(
            context = context,
            fileName = CaptureNaming.outputFileName(stem),
            bytes = encoded,
            inOriginals = false,
        ) ?: return null
        trace.mark("write")

        try {
            CaptureExif.writeOutputAttributes(context, outputUri, jpeg, recipe, side)
        } catch (e: Exception) {
            // A frame with thin EXIF is still a frame. Losing it over metadata is not.
            Log.w(TAG, "Could not write EXIF onto $outputUri", e)
        }
        trace.mark("exif")

        dao.insert(
            CaptureRecord(
                stem = stem,
                outputUri = outputUri.toString(),
                sourceUri = sourceUri?.toString(),
                dngUri = dngUri?.toString(),
                recipeName = recipe.name,
                capturedAt = capturedAt,
                rotationDegrees = rotation,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                regradedFrom = regradedFrom,
            ),
        )
        trace.mark("record")
        trace.report("${sourceWidth}x$sourceHeight -> ${side}x$side")
        return outputUri
    }

    private fun readBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        Log.e(TAG, "Could not read $uri", e)
        null
    }

    /**
     * Per-stage timings for a single frame. Kept in the build rather than added when
     * something feels slow: this pipeline is the one place where a change can quietly
     * cost half a second, and "shutter to next frame" is a stated invariant.
     */
    private class Trace {
        private val start = SystemClock.elapsedRealtime()
        private var last = start
        private val stages = StringBuilder()

        fun mark(name: String) {
            val now = SystemClock.elapsedRealtime()
            stages.append(' ').append(name).append('=').append(now - last).append("ms")
            last = now
        }

        fun report(detail: String) {
            Log.d(TAG, "capture $detail total=${last - start}ms:$stages")
        }
    }

    /**
     * Decode at full resolution, subsampling only when the frame is wider than the
     * driver will texture. Nothing here touches the saved original — this bitmap is
     * a working copy, and the file on disk keeps every pixel the sensor gave up.
     */
    private fun decode(jpeg: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val limit = renderer.maxOutputSide()
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > limit || bounds.outHeight / sampleSize > limit
        ) {
            sampleSize *= 2
        }
        if (sampleSize > 1) {
            Log.i(TAG, "Frame exceeds the ${limit}px texture limit; decoding at 1/$sampleSize")
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize
        }
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
    }

    fun release() = renderer.release()

    private companion object {
        const val TAG = "CaptureWriter"

        /**
         * High enough that grain and halation survive the encode. Measured on a
         * 3060x3060 frame, 96 costs ~160ms and 3.7MB against ~120ms and 2.4MB at 92,
         * for no visible difference on a monochrome image — a third of the file and
         * a quarter of the encode for nothing.
         */
        const val JPEG_QUALITY = 92
    }
}
