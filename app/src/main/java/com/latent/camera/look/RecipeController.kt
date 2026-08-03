package com.latent.camera.look

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The recipe list and which one is loaded, for the strip above the shutter.
 *
 * Slider drags have to reach the preview on the next frame but must not write to
 * Room on every pixel of movement, so an edit lives in [draft] immediately and is
 * persisted on a short debounce. [active] prefers the draft, which is what makes
 * dragging a slider feel connected to the viewfinder.
 */
class RecipeController(
    private val store: RecipeStore,
    private val scope: CoroutineScope,
) {

    val recipes: StateFlow<List<Recipe>> =
        store.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val activeId = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow<Recipe?>(null)

    val active: StateFlow<Recipe> =
        combine(recipes, activeId, draft) { list, id, edited ->
            edited
                ?: list.firstOrNull { it.id == id }
                ?: list.firstOrNull()
                ?: Recipe.Default
        }.stateIn(scope, SharingStarted.Eagerly, Recipe.Default)

    private var saveJob: Job? = null

    init {
        scope.launch { store.seedIfEmpty() }
    }

    fun select(id: String) {
        if (id == activeId.value) return
        commit()
        activeId.value = id
    }

    fun selectAt(index: Int) {
        recipes.value.getOrNull(index)?.let { select(it.id) }
    }

    fun indexOfActive(): Int {
        val id = active.value.id
        return recipes.value.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }

    /** A live edit. Reaches the preview at once, reaches Room shortly. */
    fun edit(recipe: Recipe) {
        draft.value = recipe
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            store.save(recipe)
            // Leave the draft in place: clearing it here would flicker back to the
            // stored value for the frame before Room re-emits.
        }
    }

    /** Flush any pending edit. Called when the editing sheet closes. */
    fun commit() {
        saveJob?.cancel()
        val edited = draft.value ?: return
        draft.value = null
        scope.launch { store.save(edited) }
    }

    fun reset() {
        val current = active.value
        draft.value = null
        saveJob?.cancel()
        scope.launch { store.reset(current) }
    }

    fun rename(name: String) {
        val current = active.value
        commit()
        scope.launch { store.rename(current, name) }
    }

    fun duplicate() {
        val current = active.value
        commit()
        scope.launch { activeId.value = store.duplicate(current).id }
    }

    fun create() {
        commit()
        scope.launch { activeId.value = store.create("Recipe").id }
    }

    /** Returns false when this is the last recipe, which cannot be removed. */
    fun delete(onRefused: () -> Unit) {
        val current = active.value
        scope.launch {
            draft.value = null
            if (!store.delete(current)) onRefused() else activeId.value = null
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val ids = recipes.value.map { it.id }.toMutableList()
        if (fromIndex !in ids.indices || toIndex !in ids.indices) return
        ids.add(toIndex, ids.removeAt(fromIndex))
        scope.launch { store.reorder(ids) }
    }

    private companion object {
        /** Long enough to coalesce a drag, short enough to survive a process death. */
        const val SAVE_DEBOUNCE_MILLIS = 350L
    }
}
