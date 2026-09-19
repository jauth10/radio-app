@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.iu.radioapp.domain.DeliveryStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The delivery queue for song requests and ratings.
 *
 * Both writes of this app take this path even with a working connection, so that
 * there is one delivery mechanism instead of two - and one place where retries,
 * the attempt counter and the idempotency key live.
 */
@Dao
abstract class OutboxDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entry: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    abstract fun observeAll(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE status = :status ORDER BY id ASC")
    abstract fun observeByStatus(status: DeliveryStatus): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE id = :entryId")
    abstract suspend fun findEntry(entryId: Long): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE idempotency_key = :idempotencyKey")
    abstract suspend fun findByIdempotencyKey(idempotencyKey: String): OutboxEntity?

    suspend fun openEntries(): List<OutboxEntity> = entriesWithStatus(DeliveryStatus.OPEN)

    @Query("SELECT * FROM outbox WHERE status = :status ORDER BY id ASC")
    protected abstract suspend fun entriesWithStatus(status: DeliveryStatus): List<OutboxEntity>

    /**
     * Records one unsuccessful attempt.
     *
     * The caller passes the new [attempts] value and the resulting [status]
     * instead of letting SQL count up, because the decision "five attempts, then
     * FAILED" is a rule of the delivery, not of the table.
     */
    @Query(
        "UPDATE outbox SET attempts = :attempts, last_attempt_at = :attemptedAt, status = :status WHERE id = :entryId"
    )
    abstract suspend fun recordAttempt(
        entryId: Long,
        attempts: Int,
        attemptedAt: Instant,
        status: DeliveryStatus,
    )

    /**
     * A business rejection. Terminal on purpose: a rejected write is never sent
     * again on its own, not even when the station reported retryable = true.
     */
    suspend fun markRejected(entryId: Long) = setStatus(entryId, DeliveryStatus.REJECTED)


    @Transaction
    open suspend fun bookDeliverySuccess(entryId: Long, request: SongRequestEntity) {
        val entry = requireNotNull(findEntry(entryId)) {
            "outbox entry $entryId does not exist"
        }
        require(entry.idempotencyKey == request.idempotencyKey) {
            "outbox entry carries ${entry.idempotencyKey}, request is ${request.idempotencyKey}"
        }
        setStatus(entryId, DeliveryStatus.DELIVERED)
        check(updateRequest(request) == 1) {
            "no song_request row for ${request.idempotencyKey}"
        }
    }

    @Query("UPDATE outbox SET status = :status WHERE id = :entryId")
    protected abstract suspend fun setStatus(entryId: Long, status: DeliveryStatus)

    /** Returns the number of rows changed - zero means there was nothing to update. */
    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateRequest(request: SongRequestEntity): Int

    @Query("DELETE FROM outbox")
    abstract suspend fun deleteAll()
}
