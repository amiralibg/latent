package com.latent.camera.look

import com.latent.camera.data.RecipeDao
import kotlinx.coroutines.flow.Flow

/**
 * Recipe CRUD, sitting between the UI and Room.
 *
 * Nothing here decides what a recipe *is* — that is [Recipe] and the shader. This
 * only owns the list: what exists, in what order, and seeding the presets the first
 * time the app runs.
 */
class RecipeStore(private val dao: RecipeDao) {

    fun observeAll(): Flow<List<Recipe>> = dao.observeAll()

    /** Idempotent, so it is safe to call on every launch. */
    suspend fun seedIfEmpty() {
        if (dao.count() == 0) dao.insertAll(RecipePresets.seed())
    }

    suspend fun save(recipe: Recipe) = dao.update(recipe)

    suspend fun rename(recipe: Recipe, name: String) =
        dao.update(recipe.copy(name = name.trim().ifEmpty { recipe.name }))

    /**
     * Copy a recipe to the end of the strip. New identity, same look — this is how
     * you try a variation without losing the one you already like.
     */
    suspend fun duplicate(recipe: Recipe): Recipe {
        val copy = recipe.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = nextCopyName(recipe.name),
            position = dao.nextPosition(),
        )
        dao.insertAll(listOf(copy))
        return copy
    }

    suspend fun create(name: String): Recipe {
        val recipe = Recipe(name = name, position = dao.nextPosition())
        dao.insertAll(listOf(recipe))
        return recipe
    }

    /**
     * Deleting the last recipe would leave the strip empty and the camera with no
     * look to shoot with, so it is refused.
     */
    suspend fun delete(recipe: Recipe): Boolean {
        if (dao.count() <= 1) return false
        dao.delete(recipe)
        return true
    }

    suspend fun reorder(idsInOrder: List<String>) = dao.reorder(idsInOrder)

    /** Reset the look but keep the name and the place in the strip. */
    suspend fun reset(recipe: Recipe) =
        dao.update(recipe.withLookOf(Recipe(name = recipe.name)))

    private fun nextCopyName(name: String): String {
        val stem = name.substringBeforeLast(" copy", name)
        return "$stem copy"
    }
}
