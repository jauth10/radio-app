package com.iu.radioapp.interactor

import com.iu.radioapp.domain.HostSession
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.repository.HostRepository
import com.iu.radioapp.repository.ListenerRepository
import com.iu.radioapp.repository.RatingRepository
import com.iu.radioapp.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class HostInteractor @Inject constructor(
    private val hosts: HostRepository,
    private val listeners: ListenerRepository,
    private val tracks: TrackRepository,
    private val ratings: RatingRepository,
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
            is Outcome.Success -> outcome.value.value?.show?.showId ?: return Outcome.Success(null)
            is Outcome.Error -> return outcome
        }
        return ratings.getAggregate(showId)
    }
}
