package com.latent.camera

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.latent.camera.camera.CameraController
import com.latent.camera.data.LatentDatabase
import com.latent.camera.gallery.GalleryController
import com.latent.camera.look.RecipeController
import com.latent.camera.look.RecipeStore
import com.latent.camera.settings.AidsSettings
import com.latent.camera.settings.CaptureSettings
import com.latent.camera.ui.LatentApp
import com.latent.camera.ui.theme.LatentTheme

/**
 * The whole app. Cold start goes straight to the viewfinder: no splash, no menu.
 */
class MainActivity : ComponentActivity() {

    private val cameraController by lazy { CameraController(applicationContext) }

    private val recipeController by lazy {
        RecipeController(
            store = RecipeStore(LatentDatabase.get(applicationContext).recipes()),
            scope = lifecycleScope,
        )
    }

    private val aidsSettings by lazy {
        AidsSettings(applicationContext, lifecycleScope)
    }

    private val captureSettings by lazy {
        CaptureSettings(applicationContext, lifecycleScope)
    }

    private val galleryController by lazy {
        GalleryController(
            context = applicationContext,
            dao = LatentDatabase.get(applicationContext).captures(),
            scope = lifecycleScope,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Framing a shot takes longer than the screen timeout is willing to wait.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Kick the camera provider while Compose inflates: bind() awaits the same
        // future, so the service handshake overlaps first-frame work for free.
        cameraController.warmUp()

        val startOnContactSheet = intent?.action == ACTION_CONTACT_SHEET

        setContent {
            LatentTheme {
                // Permissions are asked for per destination inside LatentApp, because
                // the contact sheet does not need the camera and the shortcut can
                // open straight onto it.
                LatentApp(
                    controller = cameraController,
                    recipes = recipeController,
                    aidsSettings = aidsSettings,
                    captureSettings = captureSettings,
                    gallery = galleryController,
                    startOnContactSheet = startOnContactSheet,
                )
            }
        }
    }

    override fun onStop() {
        // A pending slider edit must not be lost to a process death while the app is
        // in the background.
        recipeController.commit()
        super.onStop()
    }

    override fun onDestroy() {
        cameraController.shutdown()
        super.onDestroy()
    }

    private companion object {
        /** The launcher shortcut; see `res/xml/shortcuts.xml`. */
        const val ACTION_CONTACT_SHEET = "com.latent.camera.action.CONTACT_SHEET"
    }
}
