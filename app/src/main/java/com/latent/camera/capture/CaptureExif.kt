package com.latent.camera.capture

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.latent.camera.look.Recipe
import java.io.ByteArrayInputStream

/**
 * EXIF handling for the processed output.
 *
 * The untouched original never passes through here — its bytes are written exactly
 * as the sensor produced them, so its EXIF is preserved by not being touched at all.
 * The graded frame is a new JPEG, so its shooting data has to be carried across
 * deliberately.
 */
internal object CaptureExif {

    /** Everything that describes the exposure, the device or where it was taken. */
    private val CARRIED_OVER = arrayOf(
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_ISO_SPEED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_SENSITIVITY_TYPE,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_WHITE_BALANCE,
    )

    /** How far the frame must turn clockwise to be upright, per its own EXIF. */
    fun rotationDegrees(jpeg: ByteArray): Int =
        ByteArrayInputStream(jpeg).use { ExifInterface(it).rotationDegrees }

    /**
     * Copy the shooting data from [sourceJpeg] onto the already-written [outputUri],
     * and stamp the recipe on it.
     */
    fun writeOutputAttributes(
        context: Context,
        outputUri: Uri,
        sourceJpeg: ByteArray,
        recipe: Recipe,
        side: Int,
    ) {
        val source = ByteArrayInputStream(sourceJpeg).use { ExifInterface(it) }

        context.contentResolver.openFileDescriptor(outputUri, "rw")?.use { descriptor ->
            val output = ExifInterface(descriptor.fileDescriptor)
            for (tag in CARRIED_OVER) {
                source.getAttribute(tag)?.let { output.setAttribute(tag, it) }
            }

            // The capture path bakes rotation into the pixels, so carrying the
            // source orientation across would rotate the frame a second time.
            output.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString(),
            )
            output.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, side.toString())
            output.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, side.toString())

            output.setAttribute(ExifInterface.TAG_SOFTWARE, "Latent")
            output.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, recipe.name)
            output.setAttribute(ExifInterface.TAG_USER_COMMENT, recipePayload(recipe))

            output.saveAttributes()
        }
    }

    /**
     * The full look that produced this file, in one parseable line. The recipe name
     * alone says what the grade was called; this says what it *was* — every uniform
     * the shader read — so the frame stays re-derivable even if the recipe is later
     * edited, renamed or deleted. Versioned (`latent/v1`) for the day a stage gains
     * a field.
     */
    fun recipePayload(recipe: Recipe): String = buildString {
        append("latent/v1")
        append(" name=").append(recipe.name.sanitised())
        val mix = recipe.channelMix
        append(" mix=").append(mix.r).append(',').append(mix.g).append(',').append(mix.b)
        append(" lift=").append(recipe.lift)
        append(" gamma=").append(recipe.gamma)
        append(" gain=").append(recipe.gain)
        append(" contrast=").append(recipe.contrast)
        append(" clarity=").append(recipe.clarity)
        append(" clarityRadius=").append(recipe.clarityRadius)
        append(" halation=").append(recipe.halation)
        append(" halationThreshold=").append(recipe.halationThreshold)
        append(" halationRadius=").append(recipe.halationRadius)
        append(" grain=").append(recipe.grain)
        append(" grainSize=").append(recipe.grainSize)
        append(" toning=").append(recipe.toning)
        append(" vignette=").append(recipe.vignette)
    }

    /** Names are free text; the payload is `key=value` pairs split on spaces. */
    private fun String.sanitised(): String =
        replace(Regex("[\\s=;]"), "_").take(MAX_NAME_LENGTH)

    private const val MAX_NAME_LENGTH = 64
}
