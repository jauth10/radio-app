package com.iu.radioapp.repository

import com.iu.radioapp.data.local.OutboxDao
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.data.local.SongRequestDao
import com.iu.radioapp.data.local.toDomain
import com.iu.radioapp.data.local.toEntity
import com.iu.radioapp.data.remote.s3requests.RequestsDataSource
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.OutboxEntry
import com.iu.radioapp.domain.SongRequest
import com.iu.radioapp.repository.mapping.toCreateDto
import com.iu.radioapp.repository.mapping.toDomain
import contract.common.RadioJson
import contract.s3requests.CreateSongRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Clock

class SongRequestRepository @Inject constructor(
    private val requests: RequestsDataSource,
    private val outboxDao: OutboxDao,
    private val songRequestDao: SongRequestDao,
    private val clock: Clock,
) {

    suspend fun enqueue(request: SongRequest, displayName: String?): Outcome<Unit> {
        val entry = OutboxEntity(
            idempotencyKey = request.idempotencyKey,
            operation = OperationType.SONG_REQUEST,
            payload = RadioJson.encodeToString(request.toCreateDto(displayName)),
            attempts = 0,
            lastAttemptAt = null,
            status = DeliveryStatus.OPEN,
            rejectionReason = null,
        )
        outboxDao.enqueueSongRequest(entry, request.toEntity())
        return Outcome.Success(Unit)
    }

    /** Returns the first technical failure, so the worker knows a retry is due. */
    suspend fun deliverOpen(): Outcome<Unit> {
        var technicalFailure: Failure? = null
        for (entry in outboxDao.openEntries().filter { it.operation == OperationType.SONG_REQUEST }) {
            val dto = RadioJson.decodeFromString<CreateSongRequestDto>(entry.payload)
            when (val outcome = requests.submitRequest(entry.idempotencyKey, dto)) {
                is Outcome.Success -> {
                    val row = checkNotNull(songRequestDao.findRequest(entry.idempotencyKey))
                    outboxDao.bookDeliverySuccess(
                        entryId = entry.id,
                        request = row.copy(
                            requestId = outcome.value.requestId,
                            status = outcome.value.status.toDomain(),
                            scheduledBroadcast = outcome.value.scheduledBroadcast,
                        ),
                    )
                }
                is Outcome.Error -> when (val failure = outcome.failure) {
                    // Never re-sent on its own, whatever retryable says.
                    is Failure.Rejected -> outboxDao.bookRejection(entry.id, failure.reason)
                    else -> {
                        outboxDao.recordFailedAttempt(entry, clock.now())
                        technicalFailure = technicalFailure ?: failure
                    }
                }
            }
            // Without a connection the remaining entries would only burn attempts.
            if (technicalFailure is Failure.Connection) break
        }
        return technicalFailure?.let { Outcome.Error(it) } ?: Outcome.Success(Unit)
    }

    suspend fun retry(idempotencyKey: String): Outcome<Unit> {
        outboxDao.reopenIfFailed(idempotencyKey)
        return Outcome.Success(Unit)
    }

    suspend fun refreshStatuses(listenerId: String): Outcome<Unit> {
        val delivered = songRequestDao.observeRequests(listenerId).first().filter { it.requestId != null }
        for (row in delivered) {
            when (val outcome = requests.getRequestStatus(checkNotNull(row.requestId))) {
                is Outcome.Success -> songRequestDao.upsertAll(
                    listOf(
                        row.copy(
                            status = outcome.value.status.toDomain(),
                            rejectionReason = outcome.value.reason,
                            scheduledBroadcast = outcome.value.scheduledBroadcast,
                        )
                    )
                )
                is Outcome.Error -> return outcome
            }
        }
        return Outcome.Success(Unit)
    }

    fun observeRequests(listenerId: String): Flow<List<SongRequest>> =
        songRequestDao.observeRequests(listenerId).map { rows -> rows.map { it.toDomain() } }

    fun observeRequest(idempotencyKey: String): Flow<SongRequest?> =
        songRequestDao.observeRequest(idempotencyKey).map { it?.toDomain() }

    fun observeDeliveries(): Flow<List<OutboxEntry>> =
        outboxDao.observeAll().map { entries ->
            entries.filter { it.operation == OperationType.SONG_REQUEST }.map { it.toDomain() }
        }
}
