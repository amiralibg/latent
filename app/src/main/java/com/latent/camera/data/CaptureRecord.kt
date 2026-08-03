package com.latent.camera.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One shot: the processed B&W frame and the untouched original it came from.
 *
 * This pairing is written on every capture from day one because re-grade (Phase 5)
 * is defined as re-rendering from the stored original, and provenance cannot be
 * reconstructed after the fact — a file on disk does not say which frame it came
 * from or what it was graded with.
 */
@Entity(
    tableName = "captures",
    indices = [Index(value = ["captured_at"])],
)
data class CaptureRecord(

    /** Shared stem of both files, e.g. `LATENT_20260726_121500_123`. */
    @PrimaryKey
    @ColumnInfo(name = "stem")
    val stem: String,

    /** MediaStore URI of the graded 1:1 output. */
    @ColumnInfo(name = "output_uri")
    val outputUri: String,

    /**
     * MediaStore URI of the untouched full-frame original. Null only when the user
     * has turned the colour original off (Phase 5), which also means that shot
     * cannot be re-graded.
     */
    @ColumnInfo(name = "source_uri")
    val sourceUri: String?,

    /**
     * MediaStore URI of the sensor DNG, when the save mode asked for one and the
     * camera could produce it. Never read by the app — it exists for whatever the
     * user develops it in later.
     */
    @ColumnInfo(name = "dng_uri")
    val dngUri: String? = null,

    /** Recipe the output was rendered with. Also written into the output's EXIF. */
    @ColumnInfo(name = "recipe_name")
    val recipeName: String,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long,

    /** Rotation baked into the output, so a re-grade can reproduce the framing. */
    @ColumnInfo(name = "rotation_degrees")
    val rotationDegrees: Int,

    @ColumnInfo(name = "source_width")
    val sourceWidth: Int,

    @ColumnInfo(name = "source_height")
    val sourceHeight: Int,

    /**
     * Stem of the capture this was re-graded from, or null when it came off the
     * shutter. A re-grade is a new row rather than an edit of the old one — the
     * original frame is untouched, so both renders remain valid outputs of it.
     */
    @ColumnInfo(name = "regraded_from")
    val regradedFrom: String? = null,
) {
    /** Only a shot that kept its original can be rendered again. */
    val canRegrade: Boolean get() = sourceUri != null
}
