package com.iu.radioapp.interactor

import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.OutboxEntry
import com.iu.radioapp.domain.Rating
import com.iu.radioapp.domain.RatingContext
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.domain.Submission
import com.iu.radioapp.domain.map
import com.iu.radioapp.repository.ListenerRepository
import com.iu.radioapp.repository.RatingRepository
import com.iu.radioapp.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock

class RatingInteractor @Inject constructor(
    private val tracks: TrackRepository,
    private val ratings: RatingRepository,
    private val listeners: ListenerRepository,
    private val deliveryScheduler: DeliveryScheduler,
    private val clock: Clock,
) {

    // Null during a talk segment: S1 names no show then.
    suspend fun getRatingContext(): Outcome<RatingContext?> =
        tracks.getCurrentPlayback().map { result ->
            result.value?.show?.let { show -> RatingContext(show = show, host = show.host) }
        }

    suspend fun submitRating(target: RatingTarget, value: Int, comment: String?): Submission<Rating> {
        require(value in 1..5) { "rating value must be 1..5, was $value" }
        val context = when (val outcome = getRatingContext()) {
            is Outcome.Success -> outcome.value ?: return Submission.Refused(RefusalReason.NO_SHOW_ON_AIR)
            is Outcome.Error -> return Submission.Failed(outcome.failure)
        }
        val referenceId = when (target) {
            RatingTarget.PLAYLIST -> context.show.showId
            RatingTarget.HOST -> context.host?.hostId ?: return Submission.Refused(RefusalReason.HOST_UNKNOWN)
        }
        val listener = when (val outcome = listeners.getListener()) {
            is Outcome.Success -> outcome.value
            is Outcome.Error -> return Submission.Failed(outcome.failure)
        }
        // Double ratings are the station's call (409), not checked here.
        val rating = Rating(
            idempotencyKey = UUID.randomUUID().toString(),
            ratingId = null,
            target = target,
            referenceId = referenceId,
            value = value,
            comment = comment?.takeIf { it.isNotBlank() },
            createdAt = clock.now(),
            listenerId = listener.listenerId,
        )
        when (val outcome = ratings.enqueue(rating)) {
            is Outcome.Success -> deliveryScheduler.schedule()
            is Outcome.Error -> return Submission.Failed(outcome.failure)
        }
        return Submission.Queued(rating)
    }

    suspend fun retry(idempotencyKey: String): Outcome<Unit> {
        val outcome = ratings.retry(idempotencyKey)
        if (outcome is Outcome.Success) deliveryScheduler.schedule()
        return outcome
    }

    /** Station rejections arrive here as REJECTED with their reason. */
    fun observeDeliveries(): Flow<List<OutboxEntry>> = ratings.observeDeliveries()
}
