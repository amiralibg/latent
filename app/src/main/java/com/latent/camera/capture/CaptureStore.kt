package com.latent.camera.capture

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The album everything the app writes lands in. */
const val ALBUM_NAME = "Latent"

/**
 * Untouched originals go one level down. They are kept so grading stays
 * re-derivable, not to be browsed: interleaving them with the finished frames would
 * double every roll in the system gallery.
 */
const val ORIGINALS_SUBDIRECTORY = "Originals"

private const val MIME_JPEG = "image/jpeg"

/** What MediaStore calls a DNG. `image/dng` is not a registered type. */
private const val MIME_DNG = "image/x-adobe-dng"

/**
 * Writes capture bytes into MediaStore and returns the URI they landed at.
 *
 * The app hands over finished JPEG bytes rather than letting CameraX write the file,
 * because the original has to be stored byte for byte — re-encoding it to save it
 * would quietly cost a generation of quality and rewrite its EXIF.
 */
object CaptureStore {

    fun write(context: Context, fileName: String, bytes: ByteArray, inOriginals: Boolean): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeScoped(context, fileName, bytes, inOriginals)
        } else {
            writeLegacy(context, fileName, bytes, inOriginals)
        }

    private fun writeScoped(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        inOriginals: Boolean,
    ): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath(inOriginals))
            // Hide the row until the bytes are actually there, so nothing indexes a
            // half-written frame.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw java.io.IOException("No output stream for $uri")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun writeLegacy(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        inOriginals: Boolean,
    ): Uri? {
        @Suppress("DEPRECATION")
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val directory = File(pictures, albumPath(inOriginals))
        if (!directory.exists() && !directory.mkdirs()) {
            throw java.io.IOException("Could not create $directory")
        }
        val file = File(directory, fileName)
        FileOutputStream(file).use { it.write(bytes) }

        // Pre-Q there is no insert-then-write path, so the scanner is what turns the
        // file into a MediaStore row — and the row is what provenance records.
        var scanned: Uri? = null
        val latch = CountDownLatch(1)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(MIME_JPEG),
        ) { _, uri ->
            scanned = uri
            latch.countDown()
        }
        latch.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return scanned ?: Uri.fromFile(file)
    }

    /** The collection every capture lands in. */
    val collection: Uri get() = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    /**
     * Row values for a file CameraX will write itself. Only the DNG path uses this:
     * a DNG can only be produced by the two-file `takePicture` overload, which insists
     * on writing to disk rather than handing bytes back, so CameraX has to be told
     * where the row goes. [write] stays the path for everything the app encodes.
     */
    fun pendingValues(fileName: String, inOriginals: Boolean, dng: Boolean): ContentValues =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, if (dng) MIME_DNG else MIME_JPEG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath(inOriginals))
            }
        }

    /** The file name behind a URI, used to tell the DNG and the JPEG results apart. */
    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /**
     * Remove a file the app wrote. Returns false where the delete was refused, which
     * on Q+ means the row is not ours — deleting someone else's media needs a user
     * consent dialog this app has no reason to raise.
     */
    fun delete(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.delete(uri, null, null) > 0
    } catch (e: Exception) {
        false
    }

    private fun albumPath(inOriginals: Boolean): String =
        if (inOriginals) "$ALBUM_NAME/$ORIGINALS_SUBDIRECTORY" else ALBUM_NAME

    private fun relativePath(inOriginals: Boolean): String =
        Environment.DIRECTORY_PICTURES + "/" + albumPath(inOriginals)

    private const val SCAN_TIMEOUT_SECONDS = 5L
}

/**
 * Capture file names. The stem is shared between a processed frame and its untouched
 * original so the two stay legible as a pair on disk, independently of the Room row
 * that formally links them.
 */
object CaptureNaming {

    private val stampFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun stem(at: Date = Date()): String = "LATENT_" + stampFormat.format(at)

    fun outputFileName(stem: String): String = "$stem.jpg"

    fun originalFileName(stem: String): String = "${stem}_src.jpg"

    fun dngFileName(stem: String): String = "${stem}_src.dng"

    /** True for the untouched JPEG of a pair, told apart from its DNG by extension. */
    fun isOriginalJpeg(fileName: String): Boolean = fileName.endsWith("_src.jpg", true)
}
