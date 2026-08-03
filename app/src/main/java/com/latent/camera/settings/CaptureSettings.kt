package com.latent.camera.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.captureDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "capture")

/**
 * What a shutter press leaves on disk.
 *
 * The processed 1:1 frame is written in every mode — that is the app. What varies is
 * how much of the source survives alongside it, and therefore whether the shot can be
 * re-graded later. [BwOnly] is the one mode that throws that away, which is why it is
 * not the default and why the gallery marks those shots as final.
 */
enum class SaveMode {
    /** Graded frame alone. Nothing to re-grade from afterwards. */
    BwOnly,

    /** The default: graded frame plus the untouched full-frame colour original. */
    BwAndOriginal,

    /**
     * Adds a DNG from the sensor. Only offered where the camera reports
     * `OUTPUT_FORMAT_RAW_JPEG`; see [com.latent.camera.camera.CameraCapabilities].
     */
    BwOriginalAndDng,
    ;

    val keepsOriginal: Boolean get() = this != BwOnly

    val wantsDng: Boolean get() = this == BwOriginalAndDng
}

data class CaptureSettingsState(
    val saveMode: SaveMode = SaveMode.BwAndOriginal,
    /**
     * Export-time only. A border is a framing decision about one share, not something
     * to bake into the frame the app just shot.
     */
    val exportBorder: Boolean = false,
)

class CaptureSettings(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val store = context.applicationContext.captureDataStore

    val state: StateFlow<CaptureSettingsState> = store.data
        .map { prefs ->
            CaptureSettingsState(
                saveMode = prefs[KEY_SAVE_MODE]
                    ?.let { runCatching { SaveMode.valueOf(it) }.getOrNull() }
                    ?: SaveMode.BwAndOriginal,
                exportBorder = prefs[KEY_EXPORT_BORDER] ?: false,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, CaptureSettingsState())

    fun setSaveMode(mode: SaveMode) = write { it.copy(saveMode = mode) }

    fun setExportBorder(enabled: Boolean) = write { it.copy(exportBorder = enabled) }

    private fun write(transform: (CaptureSettingsState) -> CaptureSettingsState) {
        val next = transform(state.value)
        scope.launch {
            store.edit { prefs ->
                prefs[KEY_SAVE_MODE] = next.saveMode.name
                prefs[KEY_EXPORT_BORDER] = next.exportBorder
            }
        }
    }

    private companion object {
        val KEY_SAVE_MODE = stringPreferencesKey("save_mode")
        val KEY_EXPORT_BORDER = booleanPreferencesKey("export_border")
    }
}
