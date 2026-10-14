package com.iu.radioapp.interactor

import com.iu.radioapp.domain.ConnectionState
import com.iu.radioapp.domain.HostFeed
import com.iu.radioapp.domain.HostSession
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.domain.RatingFeedSignal
import com.iu.radioapp.domain.SessionCredentials
import com.iu.radioapp.repository.HostRepository
import com.iu.radioapp.repository.ListenerRepository
import com.iu.radioapp.repository.RatingRepository
import com.iu.radioapp.repository.TrackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration

class HostInteractor @Inject constructor(
    private val hosts: HostRepository,
    private val listeners: ListenerRepository,
    private val tracks: TrackRepository,
    private val ratings: RatingRepository,
    private val clock: Clock,
) {

    // The listener id doubles as device id.
    suspend fun login(hostCode: String): Outcome<HostSession> = when (val outcome = listeners.getListener()) {
        is Outcome.Success -> hosts.login(hostCode.trim(), deviceId = outcome.value.listenerId)
        is Outcome.Error -> outcome
    }

    fun observeSessionActive(): Flow<Boolean> = hosts.observeSessionActive()

    suspend fun endSession(): Outcome<Unit> = hosts.endSession()

    // Null during a talk segment: no show, no aggregate.
    suspend fun getCurrentAggregate(): Outcome<RatingAggregate?> {
        val showId = when (val outcome = tracks.getCurrentPlayback()) {
            is Outcome.Success -> {
                // A stale cache may still name the previous show; no numbers beat the wrong ones.
                outcome.value.staleCause(clock.now())?.let { return Outcome.Error(it) }
                outcome.value.value?.show?.showId ?: return Outcome.Success(null)
            }
            is Outcome.Error -> return outcome
        }
        return ratings.getAggregate(showId)
    }

    /** Rebuilding an expired or rejected session means a new login; the listener side is left alone. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHostFeed(): Flow<HostFeed> = flow {
        var expired = false
        emitAll(
            hosts.observeSession().flatMapLatest { session ->
                if (session == null) {
                    flowOf(if (expired) HostFeed.SessionExpired else HostFeed.SignedOut)
                } else {
                    expired = false
                    activeFeed(session) {
                        expired = true
                        hosts.endSession()
                    }
                }
            }
        )
    }.distinctUntilChanged()

    private fun activeFeed(session: SessionCredentials, expire: suspend () -> Unit): Flow<HostFeed> = channelFlow {
        val remaining = session.validUntil?.let { it - clock.now() }
        if (remaining != null && remaining <= Duration.ZERO) {
            expire()
            return@channelFlow
        }
        if (remaining != null) {
            launch {
                delay(remaining)
                expire()
            }
        }
        val showId = currentShowId()
        val initialAggregate = showId?.let { (ratings.getAggregate(it) as? Outcome.Success)?.value }
        var feed = HostFeed.Active(emptyList(), initialAggregate, ConnectionState.Connecting, showId)
        send(feed)
        ratings.observeRatingFeed(session.token, showId, since = session.startedAt ?: clock.now()).collect { signal ->
            feed = when (signal) {
                is RatingFeedSignal.RatingReceived -> feed.copy(
                    ratings = (feed.ratings + signal.received).sortedByDescending { it.rating.serverReceivedAt },
                )
                is RatingFeedSignal.AggregateUpdated -> feed.copy(aggregate = signal.aggregate)
                is RatingFeedSignal.StateChanged -> feed.copy(connection = signal.state)
                RatingFeedSignal.SessionRejected -> {
                    expire()
                    feed
                }
            }
            send(feed)
        }
    }

    // The show at the start; a stale cache names none (same rule as for the aggregate).
    private suspend fun currentShowId(): String? = when (val outcome = tracks.getCurrentPlayback()) {
        is Outcome.Success -> outcome.value.takeUnless { it.isStale(clock.now()) }?.value?.show?.showId
        is Outcome.Error -> null
    }
}
