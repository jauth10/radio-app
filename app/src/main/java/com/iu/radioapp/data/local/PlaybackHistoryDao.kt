@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime

/**
 * Local playback history, capped at [HISTORY_LIMIT] entries.
 */
@Dao
abstract class PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY started_at DESC")
    abstract fun observeHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY started_at DESC LIMIT 1")
    abstract fun observeLatest(): Flow<PlaybackHistoryEntity?>

    @Query("SELECT COUNT(*) FROM playback_history")
    abstract suspend fun count(): Int

    /**
     * Appends one airing and enforces the limit - the first of the two
     * transactional methods of this app.
     *
     * Both steps have to succeed together: an insert that is not followed by the
     * trim leaves the table one row over the limit, and a trim without its
     * insert deletes an entry for nothing. Room opens the transaction around the
     * whole method body, so a failure in either step rolls back both.
     *
     * REPLACE on the insert is intentional. The primary key is formed from track
     * id and start time, so the same airing read twice is the same row; replacing
     * it keeps re-reading the station's history idempotent.
     */
    @Transaction
    open suspend fun appendAndTrim(entry: PlaybackHistoryEntity) {
        insert(entry)
        trimToLimit(HISTORY_LIMIT)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(entry: PlaybackHistoryEntity)

    /**
     * Keeps the [limit] newest rows and deletes the rest.
     *
     * Deliberately not "delete the single oldest row": that only works if exactly
     * one row was added since the last trim. Keeping the newest n also repairs a
     * table that is over the limit for any other reason, for instance after a
     * batch import of the station's history.
     */
    @Query(
        """
        DELETE FROM playback_history
        WHERE playback_id NOT IN (
            SELECT playback_id FROM playback_history
            ORDER BY started_at DESC, playback_id DESC
            LIMIT :limit
        )
        """
    )
    protected abstract suspend fun trimToLimit(limit: Int)

    @Query("DELETE FROM playback_history")
    abstract suspend fun deleteAll()

    companion object {
        /** Assumption 11: the local history holds at most 50 tracks. */
        const val HISTORY_LIMIT = 50
    }
}
