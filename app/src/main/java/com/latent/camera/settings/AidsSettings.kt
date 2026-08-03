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

private val Context.aidsDataStore: DataStore<Preferences> by preferencesDataStore(name = "aids")

enum class GridMode {
    Off,
    Thirds,
    Centre,
    Diagonals,
    Golden,
    ;

    fun next(): GridMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

/**
 * Shooting-aid toggles. Persisted in DataStore — not Room, which is for recipes
 * and capture provenance.
 */
data class AidsState(
    val peaking: Boolean = false,
    val zebra: Boolean = false,
    val histogram: Boolean = false,
    val level: Boolean = true,
    val grid: GridMode = GridMode.Thirds,
) {
    /** True when anything is on, for the top bar's aids button. */
    val anyOn: Boolean get() = peaking || zebra || histogram || level || grid != GridMode.Off

    /** True when the preview GL path must run the post-look aids pass. */
    val needsGlOverlay: Boolean get() = peaking || zebra

    /** True when the graded frame must be materialised for sampling. */
    val needsGradedFrame: Boolean get() = needsGlOverlay || histogram
}

class AidsSettings(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val store = context.applicationContext.aidsDataStore

    val state: StateFlow<AidsState> = store.data
        .map { prefs ->
            AidsState(
                peaking = prefs[KEY_PEAKING] ?: false,
                zebra = prefs[KEY_ZEBRA] ?: false,
                histogram = prefs[KEY_HISTOGRAM] ?: false,
                level = prefs[KEY_LEVEL] ?: true,
                grid = prefs[KEY_GRID]?.let { runCatching { GridMode.valueOf(it) }.getOrNull() }
                    ?: GridMode.Thirds,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, AidsState())

    fun setPeaking(enabled: Boolean) = write { it.copy(peaking = enabled) }
    fun setZebra(enabled: Boolean) = write { it.copy(zebra = enabled) }
    fun setHistogram(enabled: Boolean) = write { it.copy(histogram = enabled) }
    fun setLevel(enabled: Boolean) = write { it.copy(level = enabled) }
    fun cycleGrid() = write { it.copy(grid = it.grid.next()) }
    fun setGrid(mode: GridMode) = write { it.copy(grid = mode) }

    private fun write(transform: (AidsState) -> AidsState) {
        val next = transform(state.value)
        scope.launch {
            store.edit { prefs ->
                prefs[KEY_PEAKING] = next.peaking
                prefs[KEY_ZEBRA] = next.zebra
                prefs[KEY_HISTOGRAM] = next.histogram
                prefs[KEY_LEVEL] = next.level
                prefs[KEY_GRID] = next.grid.name
            }
        }
    }

    private companion object {
        val KEY_PEAKING = booleanPreferencesKey("peaking")
        val KEY_ZEBRA = booleanPreferencesKey("zebra")
        val KEY_HISTOGRAM = booleanPreferencesKey("histogram")
        val KEY_LEVEL = booleanPreferencesKey("level")
        val KEY_GRID = stringPreferencesKey("grid")
    }
}
