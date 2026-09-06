package com.latent.camera.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Decoding for the contact sheet and the viewer.
 *
 * There is no image-loading library here on purpose. The app reads a handful of its
 * own square JPEGs out of MediaStore and nothing else — no network, no signatures, no
 * transformations — and a cache plus a sample-size calculation is the whole job.
 */
object Thumbnails {

    /**
     * Sized in bytes rather than entries because a contact-sheet cell and a full
     * frame differ by two orders of magnitude, and an entry count would either starve
     * the grid or hold two full-resolution frames it does not need.
     */
    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun cacheBytes(): Int {
        val available = Runtime.getRuntime().maxMemory()
        return (available / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    suspend fun load(context: Context, uri: Uri, targetPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = "$uri@$targetPx"
            cache.get(key)?.let { return@withContext it }

            val bitmap = decode(context, uri, targetPx) ?: return@withContext null
            cache.put(key, bitmap)
            bitmap
        }

    /**
     * Warm the cache for frames the user is about to look at — the pager's neighbours.
     * Hits return from `load` without decoding, so this costs nothing for frames
     * already seen and a couple of background decodes otherwise. Results are
     * discarded: the cache is the delivery mechanism, which is what makes this safe
     * to cancel mid-swipe — a restarted prefetch just hits the entries the first one
     * managed to write.
     */
    suspend fun prefetch(context: Context, uris: List<Uri>, targetPx: Int) {
        if (uris.isEmpty()) return
        coroutineScope {
            uris.map { uri -> async { load(context, uri, targetPx) } }.awaitAll()
        }
    }

    private fun decode(context: Context, uri: Uri, targetPx: Int): Bitmap? = try {
        // Q+ hands back a thumbnail MediaStore has usually already generated, which
        // for a grid of squares is far cheaper than decoding the frame ourselves.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetPx <= THUMBNAIL_LIMIT_PX) {
            context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
        } else {
            decodeSampled(context, uri, targetPx)
        }
    } catch (e: Exception) {
        // A missing thumbnail is not a missing file: fall back before giving up, so a
        // frame the system has not indexed yet still appears.
        runCatching { decodeSampled(context, uri, targetPx) }.getOrNull()
    }

    private fun decodeSampled(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetPx) sampleSize *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    fun evict(uri: Uri) {
        // Keys carry their target size, so a deleted frame has to be swept by prefix.
        val prefix = "$uri@"
        cache.snapshot().keys
            .filter { it.startsWith(prefix) }
            .forEach(cache::remove)
    }

    /** Above this, `loadThumbnail` is upscaling something it stored small. */
    private const val THUMBNAIL_LIMIT_PX = 512
}

/**
 * A decoded frame, or null while it is still being read. Keyed on the URI and the
 * requested size so scrolling back to a cell does not decode it a second time.
 */
@Composable
fun rememberThumbnail(uri: Uri?, targetPx: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri, targetPx) {
        value = if (uri == null) {
            null
        } else {
            Thumbnails.load(context, uri, targetPx)?.asImageBitmap()
        }
    }
}
