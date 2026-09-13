package com.iu.radioapp.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Read cache for track master data.
 *
 * Reads are Flows so the screen follows the table instead of asking it: a
 * refreshed row reaches the UI as a new emission, which is the same mechanism
 * that makes the optimistic outbox display work.
 *
 * Writes are upserts. The same track arrives from S1 and from S2 with different
 * fields known, and the caller decides what the merged row looks like - the DAO
 * only stores it.
 */
@Dao
interface TrackCacheDao {

    @Upsert
    suspend fun upsert(track: TrackCacheEntity)

    @Upsert
    suspend fun upsertAll(tracks: List<TrackCacheEntity>)

    @Query("SELECT * FROM track_cache WHERE track_id = :trackId")
    fun observeTrack(trackId: String): Flow<TrackCacheEntity?>

    @Query("SELECT * FROM track_cache ORDER BY title ASC")
    fun observeTracks(): Flow<List<TrackCacheEntity>>

    @Query("SELECT * FROM track_cache WHERE track_id = :trackId")
    suspend fun findTrack(trackId: String): TrackCacheEntity?

    @Query("DELETE FROM track_cache")
    suspend fun deleteAll()
}
