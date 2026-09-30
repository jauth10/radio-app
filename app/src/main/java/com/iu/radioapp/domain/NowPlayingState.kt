package com.iu.radioapp.domain

import kotlin.time.Instant

sealed interface NowPlayingState {

    /** 204: talk segment, nothing to show and not a failure. */
    data object TalkSegment : NowPlayingState

    data class Live(val nowPlaying: NowPlaying) : NowPlayingState

    /** Live read failed; [isStale] is the five minute rule. */
    data class Cached(
        val nowPlaying: NowPlaying,
        val fetchedAt: Instant,
        val isStale: Boolean,
        val cause: Failure,
    ) : NowPlayingState
}
