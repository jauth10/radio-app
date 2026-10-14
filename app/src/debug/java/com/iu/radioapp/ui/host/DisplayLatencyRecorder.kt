package com.iu.radioapp.ui.host

import com.iu.radioapp.domain.FeedSource
import com.iu.radioapp.domain.ReceivedRating
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Writes one CSV line per rating on its first drawing: ratingId,feedSource,serverReceivedAtMs,displayedAtMs,deltaMs,visibleOnArrival. */
class DisplayLatencyRecorder(
    private val clock: Clock,
    private val write: (String) -> Unit,
) : LatencyRecorder {

    private val listedAt = mutableMapOf<String, Instant>()
    private val recorded = mutableSetOf<String>()

    @Synchronized
    override fun onListed(ratings: List<ReceivedRating>) {
        val now = clock.now()
        ratings.forEach { listedAt.getOrPut(it.rating.ratingId) { now } }
    }

    @Synchronized
    override fun onDisplayed(received: ReceivedRating) {
        if (received.source == FeedSource.CATCH_UP) return
        val ratingId = received.rating.ratingId
        if (!recorded.add(ratingId)) return
        val displayedAt = clock.now()
        val serverReceivedAt = received.rating.serverReceivedAt
        // A row drawn well after it was listed was scrolled out of view when it arrived.
        val visibleOnArrival = displayedAt - (listedAt[ratingId] ?: displayedAt) <= ARRIVAL_TOLERANCE
        write(
            listOf(
                ratingId,
                received.source,
                serverReceivedAt.toEpochMilliseconds(),
                displayedAt.toEpochMilliseconds(),
                (displayedAt - serverReceivedAt).inWholeMilliseconds,
                visibleOnArrival,
            ).joinToString(",")
        )
    }

    companion object {
        const val TAG = "RadioLatency"
        val ARRIVAL_TOLERANCE = 100.milliseconds
    }
}
