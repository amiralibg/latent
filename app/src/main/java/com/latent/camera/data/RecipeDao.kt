package com.latent.camera.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.latent.camera.look.Recipe
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    /** Strip order. This is the order the user swipes through. */
    @Query("SELECT * FROM recipes ORDER BY position ASC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun find(id: String): Recipe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<Recipe>)

    @Update
    suspend fun update(recipe: Recipe)

    @Delete
    suspend fun delete(recipe: Recipe)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM recipes")
    suspend fun nextPosition(): Int

    /**
     * Rewrite the whole ordering in one go. Reordering by writing each row as it
     * moves would leave the strip briefly showing two recipes in one slot.
     */
    @Transaction
    suspend fun reorder(idsInOrder: List<String>) {
        idsInOrder.forEachIndexed { index, id -> setPosition(id, index) }
    }

    @Query("UPDATE recipes SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)
}
