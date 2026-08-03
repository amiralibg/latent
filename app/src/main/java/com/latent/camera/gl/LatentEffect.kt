package com.latent.camera.gl

import androidx.camera.core.CameraEffect
import androidx.core.util.Consumer

/**
 * The look shader, attached to PREVIEW and nothing else.
 *
 * Never add IMAGE_CAPTURE to the targets. CameraX allows one bound ImageCapture use
 * case, so an effect on that target would make the untouched original unobtainable
 * and break the default save mode. The processed still comes from
 * [OffscreenRenderer] instead.
 */
internal class LatentEffect(
    processor: PreviewProcessor,
    onError: (Throwable) -> Unit,
) : CameraEffect(PREVIEW, processor.executor, processor, Consumer<Throwable> { onError(it) })
