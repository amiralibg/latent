package com.latent.camera.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.latent.camera.camera.CameraController
import com.latent.camera.capture.CaptureExport
import com.latent.camera.data.CaptureRecord
import com.latent.camera.gallery.GalleryController
import com.latent.camera.look.RecipeController
import com.latent.camera.settings.AidsSettings
import com.latent.camera.settings.CaptureSettings
import com.latent.camera.ui.gallery.ContactSheet
import com.latent.camera.ui.gallery.FrameViewer
import com.latent.camera.ui.theme.Motion

/**
 * Where the app is. Three destinations and no back stack worth the name, so this is a
 * sealed state rather than a navigation library — adding one would be more moving
 * parts than the whole thing it navigates.
 */
private sealed interface Destination {
    data object Viewfinder : Destination
    data object ContactSheet : Destination
    data class Frame(val startIndex: Int) : Destination
}

@Composable
fun LatentApp(
    controller: CameraController,
    recipes: RecipeController,
    aidsSettings: AidsSettings,
    captureSettings: CaptureSettings,
    gallery: GalleryController,
    startOnContactSheet: Boolean = false,
) {
    val context = LocalContext.current
    val captures by gallery.captures.collectAsStateWithLifecycle()
    val latest by gallery.latest.collectAsStateWithLifecycle()
    val allRecipes by recipes.recipes.collectAsStateWithLifecycle()
    val settings by captureSettings.state.collectAsStateWithLifecycle()

    var destination by remember {
        mutableStateOf<Destination>(
            if (startOnContactSheet) Destination.ContactSheet else Destination.Viewfinder,
        )
    }

    val share: (CaptureRecord) -> Unit = { record ->
        val intent = CaptureExport.shareIntent(
            context = context,
            uri = Uri.parse(record.outputUri),
            withBorder = settings.exportBorder,
        )
        if (intent != null) {
            context.startActivity(Intent.createChooser(intent, null))
        }
    }

    // The gate is per destination, not around the app. Browsing frames you already
    // shot is not a reason to ask for the camera, and the shortcut opens here.
    //
    // Destinations crossfade rather than slide. A slide would imply the three screens
    // sit beside each other; they do not — the viewfinder is the app and the other two
    // are it looking backwards. The fade is short enough that leaving the viewfinder
    // still feels like putting the camera down rather than closing a document.
    Crossfade(
        targetState = destination,
        animationSpec = tween(220, easing = Motion.Sharp),
        label = "destination",
    ) { current ->
        when (current) {
            Destination.Viewfinder -> PermissionGate(AccessPurpose.Viewfinder) {
                CameraScreen(
                    controller = controller,
                    recipes = recipes,
                    aidsSettings = aidsSettings,
                    captureSettings = captureSettings,
                    latestCaptureUri = latest?.outputUri?.let(Uri::parse),
                    onOpenGallery = { destination = Destination.ContactSheet },
                )
            }

            Destination.ContactSheet -> PermissionGate(AccessPurpose.Library) {
                ContactSheet(
                    captures = captures,
                    onOpen = { record ->
                        val index = captures.indexOfFirst { it.stem == record.stem }
                        destination = Destination.Frame(index.coerceAtLeast(0))
                    },
                    onBack = { destination = Destination.Viewfinder },
                )
            }

            is Destination.Frame -> PermissionGate(AccessPurpose.Library) {
                FrameViewer(
                    captures = captures,
                    startIndex = current.startIndex,
                    recipes = allRecipes,
                    onRegrade = controller::regrade,
                    onShare = share,
                    onDelete = gallery::delete,
                    onBack = { destination = Destination.ContactSheet },
                )
            }
        }
    }
}
