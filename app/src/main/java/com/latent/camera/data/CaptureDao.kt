package com.latent.camera.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CaptureRecord)

    /** Newest first — the shape the Phase 5 contact sheet wants. */
    @Query("SELECT * FROM captures ORDER BY captured_at DESC")
    fun observeAll(): Flow<List<CaptureRecord>>

    @Query("SELECT * FROM captures ORDER BY captured_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CaptureRecord>

    @Query("SELECT * FROM captures WHERE output_uri = :outputUri LIMIT 1")
    suspend fun findByOutputUri(outputUri: String): CaptureRecord?

    @Query("SELECT * FROM captures WHERE stem = :stem LIMIT 1")
    suspend fun find(stem: String): CaptureRecord?

    /** The newest frame, for the gallery thumbnail beside the shutter. */
    @Query("SELECT * FROM captures ORDER BY captured_at DESC LIMIT 1")
    fun observeLatest(): Flow<CaptureRecord?>

    /**
     * How many other rows still point at this original. A re-grade shares the source
     * of the shot it came from, so a delete has to ask this before removing the file
     * underneath both of them.
     */
    @Query(
        "SELECT COUNT(*) FROM captures WHERE source_uri = :sourceUri AND stem != :excludingStem",
    )
    suspend fun countSharingSource(sourceUri: String, excludingStem: String): Int

    @Query("DELETE FROM captures WHERE stem = :stem")
    suspend fun delete(stem: String)
}
