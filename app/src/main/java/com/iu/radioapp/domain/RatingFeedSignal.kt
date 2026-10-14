package com.iu.radioapp.domain

/** Which path a rating took to the host view; CATCH_UP ratings were loaded on opening and say nothing about latency. */
enum class FeedSource {
    EVENT_CHANNEL,
    POLLING,
    CATCH_UP,
}

data class ReceivedRating(
    val rating: RatingEvent,
    val source: FeedSource,
)

/** One stream for event channel and polling alike; [SessionRejected] is always the last signal. */
sealed interface RatingFeedSignal {
    data class RatingReceived(val received: ReceivedRating) : RatingFeedSignal
    data class AggregateUpdated(val aggregate: RatingAggregate) : RatingFeedSignal
    data class StateChanged(val state: ConnectionState) : RatingFeedSignal
    data object SessionRejected : RatingFeedSignal
}
