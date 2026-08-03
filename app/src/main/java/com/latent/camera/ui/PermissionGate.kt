package com.latent.camera.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * What a screen needs before it can do its job.
 *
 * Split because the two are genuinely different asks: shooting needs the camera, and
 * looking at what you already shot does not. The launcher shortcut opens straight
 * onto the contact sheet, and raising a camera dialog at someone who wanted to browse
 * their frames is both rude and unnecessary.
 */
enum class AccessPurpose {
    Viewfinder,

    /**
     * Reading the app's own frames back. Nothing is required on Q and above — MediaStore
     * hands an app its own media without permission — so this gate is invisible there
     * and only becomes real on the legacy storage path.
     */
    Library,
}

/**
 * Asks for whatever [purpose] needs and shows [content] once granted.
 *
 * A user who has already granted goes straight through with no interstitial; the
 * system dialog is raised immediately on the first cold start rather than behind a
 * "get started" screen. Rationale is only shown after a refusal, when it means something.
 */
@Composable
fun PermissionGate(
    purpose: AccessPurpose = AccessPurpose.Viewfinder,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val required = remember(purpose) { requiredPermissions(purpose) }

    var granted by remember { mutableStateOf(context.hasAll(required)) }
    var refused by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        granted = context.hasAll(required)
        if (!granted) {
            refused = true
            // Once the system stops offering the rationale, the dialog will never
            // appear again and the only route left is app settings.
            permanentlyDenied = activity != null && required.none {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(required)
    }

    // Permissions can change while we are backgrounded — in settings, or by the system.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.hasAll(required)
                if (granted) {
                    refused = false
                    permanentlyDenied = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        granted -> content()
        refused -> RationaleScreen(
            purpose = purpose,
            permanentlyDenied = permanentlyDenied,
            onRetry = {
                if (permanentlyDenied) context.openAppSettings() else launcher.launch(required)
            },
        )
        // Request in flight: stay black rather than flashing a screen the user will
        // never read.
        else -> Column(Modifier.fillMaxSize()) {}
    }
}

@Composable
private fun RationaleScreen(
    purpose: AccessPurpose,
    permanentlyDenied: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (purpose) {
                AccessPurpose.Viewfinder -> "Latent is a camera"
                AccessPurpose.Library -> "Latent keeps your frames on the device"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            text = rationale(purpose, permanentlyDenied),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(
                when {
                    permanentlyDenied -> "Open settings"
                    purpose == AccessPurpose.Viewfinder -> "Allow camera"
                    else -> "Allow storage"
                },
            )
        }
    }
}

private fun rationale(purpose: AccessPurpose, permanentlyDenied: Boolean): String =
    when (purpose) {
        AccessPurpose.Viewfinder -> if (permanentlyDenied) {
            "Camera access is turned off for Latent. Open settings to allow it. " +
                "Nothing you shoot leaves the device."
        } else {
            "It needs the camera to show a viewfinder, and storage to save what you " +
                "shoot. Nothing leaves the device — there is no account and no network."
        }

        AccessPurpose.Library -> if (permanentlyDenied) {
            "Storage access is turned off for Latent, so it cannot read back the " +
                "frames it saved. Open settings to allow it."
        } else {
            "It needs storage access to read back the frames it saved. Nothing " +
                "leaves the device."
        }
    }

private fun requiredPermissions(purpose: AccessPurpose): Array<String> = buildList {
    if (purpose == AccessPurpose.Viewfinder) {
        add(android.Manifest.permission.CAMERA)
    }
    // Pre-Q there is no scoped storage, so both writing captures and reading them
    // back need the legacy permission. Q and above grant an app its own media.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}.toTypedArray()

private fun Context.hasAll(permissions: Array<String>): Boolean = permissions.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
