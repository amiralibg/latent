package com.latent.camera.gallery

import android.content.Context
import android.net.Uri
import android.util.Log
import com.latent.camera.capture.CaptureStore
import com.latent.camera.data.CaptureDao
import com.latent.camera.data.CaptureRecord
import com.latent.camera.ui.gallery.Thumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The contact sheet's view of what the app has shot.
 *
 * It reads the captures table rather than MediaStore, which is what makes the gallery
 * "app captures only" without filtering a shared image collection by path — the rows
 * exist precisely because this app wrote the files.
 */
class GalleryController(
    context: Context,
    private val dao: CaptureDao,
    private val scope: CoroutineScope,
) {

    private val appContext = context.applicationContext

    val captures: StateFlow<List<CaptureRecord>> =
        dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The newest frame, for the thumbnail beside the shutter. */
    val latest: StateFlow<CaptureRecord?> =
        dao.observeLatest().stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Delete a shot: the graded frame always, and the original and DNG underneath it
     * only when nothing else still needs them.
     *
     * That condition is the whole subtlety here. A re-grade is a second row pointing
     * at the *same* untouched original, so deleting either render naively would take
     * the source out from under the other one — leaving a row that shows a frame the
     * app can no longer re-render and, once that row is deleted too, an orphaned file
     * nothing references.
     *
     * The row goes last, so a delete interrupted halfway leaves something still
     * pointing at whatever survived rather than orphaning it.
     */
    fun delete(record: CaptureRecord) {
        scope.launch {
            val sourceShared = record.sourceUri
                ?.let { dao.countSharingSource(it, record.stem) > 0 }
                ?: false

            val doomed = buildList {
                add(record.outputUri)
                if (!sourceShared) {
                    record.sourceUri?.let(::add)
                    record.dngUri?.let(::add)
                }
            }

            withContext(Dispatchers.IO) {
                doomed.map(Uri::parse).forEach { uri ->
                    if (!CaptureStore.delete(appContext, uri)) {
                        Log.w(TAG, "Could not delete $uri")
                    }
                    Thumbnails.evict(uri)
                }
            }
            dao.delete(record.stem)
        }
    }

    private companion object {
        const val TAG = "GalleryController"
    }
}
