package com.iu.radioapp.interactor

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.domain.RequestStatus
import com.iu.radioapp.domain.SongRequest
import com.iu.radioapp.domain.SongRequestWithDelivery
import com.iu.radioapp.domain.Submission
import com.iu.radioapp.domain.Track
import com.iu.radioapp.repository.ArchiveRepository
import com.iu.radioapp.repository.ListenerRepository
import com.iu.radioapp.repository.SongRequestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock

class SongRequestInteractor @Inject constructor(
    private val archive: ArchiveRepository,
    private val requests: SongRequestRepository,
    private val listeners: ListenerRepository,
    private val deliveryScheduler: DeliveryScheduler,
    private val clock: Clock,
) {

    suspend fun searchTracks(query: String): Outcome<List<Track>> =
        if (query.isBlank()) Outcome.Success(emptyList()) else archive.searchTracks(query.trim(), SEARCH_LIMIT)

    suspend fun submitRequest(track: Track, message: String?): Submission<SongRequest> {
        refusalFor(track)?.let { return it }
        val listener = when (val outcome = listeners.getListener()) {
            is Outcome.Success -> outcome.value
            is Outcome.Error -> return Submission.Failed(outcome.failure)
        }
        // Generated before the first send attempt, stable across retries.
        val request = SongRequest(
            idempotencyKey = UUID.randomUUID().toString(),
            requestId = null,
            trackId = track.trackId,
            trackTitle = track.title,
            listenerId = listener.listenerId,
            message = message?.takeIf { it.isNotBlank() },
            createdAt = clock.now(),
            status = RequestStatus.PENDING,
            rejectionReason = null,
            scheduledBroadcast = null,
        )
        when (val outcome = requests.enqueue(request, listener.displayName)) {
            is Outcome.Success -> deliveryScheduler.schedule()
            is Outcome.Error -> return Submission.Failed(outcome.failure)
        }
        return Submission.Queued(request)
    }

    suspend fun retry(idempotencyKey: String): Outcome<Unit> {
        val outcome = requests.retry(idempotencyKey)
        if (outcome is Outcome.Success) deliveryScheduler.schedule()
        return outcome
    }

    suspend fun refreshStatuses(): Outcome<Unit> = when (val outcome = listeners.getListener()) {
        is Outcome.Success -> requests.refreshStatuses(outcome.value.listenerId)
        is Outcome.Error -> outcome
    }

    fun observeRequests(): Flow<List<SongRequestWithDelivery>> = flow {
        val listener = when (val outcome = listeners.getListener()) {
            is Outcome.Success -> outcome.value
            // Without an identity there are no requests to show.
            is Outcome.Error -> {
                emit(emptyList())
                return@flow
            }
        }
        emitAll(
            combine(requests.observeRequests(listener.listenerId), requests.observeDeliveries()) { rows, deliveries ->
                val statusByKey = deliveries.associate { it.idempotencyKey to it.status }
                rows.map { SongRequestWithDelivery(it, statusByKey[it.idempotencyKey]) }
            }
        )
    }

    // Only a definite "no" stops the request; if the archive is unreachable the station decides.
    private suspend fun refusalFor(track: Track): Submission<Nothing>? {
        val broadcastable = track.broadcastable ?: when (val outcome = archive.getTrack(track.trackId)) {
            is Outcome.Success -> outcome.value.broadcastable
            is Outcome.Error -> if (outcome.failure is Failure.Rejected) return Submission.Failed(outcome.failure) else null
        }
        return if (broadcastable == false) Submission.Refused(RefusalReason.TRACK_NOT_BROADCASTABLE) else null
    }

    companion object {
        const val SEARCH_LIMIT = 20
    }
}
