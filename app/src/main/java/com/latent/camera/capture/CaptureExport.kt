package com.latent.camera.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Getting a frame out of the app.
 *
 * Sharing the MediaStore URI directly is the honest default — the receiving app gets
 * the exact file on disk, no copy and no re-encode. The bordered variant cannot work
 * that way, since it is a different image, so it is rendered into the cache and handed
 * over through a FileProvider instead.
 */
object CaptureExport {

    /**
     * Border width as a fraction of the frame. Wide enough to read as a deliberate
     * mount rather than a rendering artefact once Instagram has scaled it down.
     */
    private const val BORDER_FRACTION = 0.055f

    private const val JPEG_QUALITY = 95

    private const val CACHE_DIRECTORY = "shared"

    fun shareIntent(context: Context, uri: Uri, withBorder: Boolean): Intent? {
        val shared = if (withBorder) bordered(context, uri) ?: uri else uri
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, shared)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Render [uri] onto white, square, and return a shareable URI for the copy.
     *
     * The border is added at export and never at capture: it is a decision about one
     * post, and baking it into the saved frame would put a mount on the negative.
     */
    private fun bordered(context: Context, uri: Uri): Uri? = try {
        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null

        val margin = (source.width * BORDER_FRACTION).toInt()
        val side = source.width + margin * 2
        val mounted = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(mounted).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, margin.toFloat(), margin.toFloat(), null)
        }
        source.recycle()

        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        // One name, overwritten each time: this is a hand-off buffer, not a library,
        // and a cache directory that grows by a few megabytes per share is a leak.
        val file = File(directory, "latent_share.jpg")
        file.outputStream().use { mounted.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        mounted.recycle()

        FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
    } catch (e: Exception) {
        Log.e(TAG, "Could not render the bordered export", e)
        null
    }

    private const val TAG = "CaptureExport"
}
