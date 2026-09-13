package com.iu.radioapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The listener's own song requests.
 *
 * [insert] aborts on conflict rather than replacing: a second row under the same
 * idempotency key would mean the same request was queued twice, and silently
 * overwriting it would hide that bug instead of surfacing it.
 *
 * Booking a successful delivery is not here but in OutboxDao - it changes this
 * table and the outbox in one transaction, and a transaction belongs to one DAO.
 */
@Dao
interface SongRequestDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(request: SongRequestEntity)

    /** Writes back the overview endpoint, which may know rows this device does not. */
    @Upsert
    suspend fun upsertAll(requests: List<SongRequestEntity>)

    @Query("SELECT * FROM song_request WHERE listener_id = :listenerId ORDER BY created_at DESC")
    fun observeRequests(listenerId: String): Flow<List<SongRequestEntity>>

    @Query("SELECT * FROM song_request WHERE idempotency_key = :idempotencyKey")
    fun observeRequest(idempotencyKey: String): Flow<SongRequestEntity?>

    @Query("SELECT * FROM song_request WHERE idempotency_key = :idempotencyKey")
    suspend fun findRequest(idempotencyKey: String): SongRequestEntity?

    @Query("DELETE FROM song_request")
    suspend fun deleteAll()
}
