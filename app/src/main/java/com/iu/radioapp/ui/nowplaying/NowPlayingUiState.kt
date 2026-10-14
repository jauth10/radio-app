package com.iu.radioapp.ui.nowplaying

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.NowPlaying
import com.iu.radioapp.domain.Playback
import kotlin.time.Instant

/** The history comes from Room and is part of every state but Loading, so it stays visible offline. */
sealed interface NowPlayingUiState {

    data object Loading : NowPlayingUiState

    data class Content(val nowPlaying: NowPlaying, val history: List<Playback>) : NowPlayingUiState

    /** Talk segment: not a failure, just no music right now. */
    data class Empty(val history: List<Playback>) : NowPlayingUiState

    /** No connection; [cached] is null when nothing was ever stored. */
    data class Offline(val cached: CachedTrack?, val history: List<Playback>) : NowPlayingUiState

    data class Error(val failure: Failure, val cached: CachedTrack?, val history: List<Playback>) : NowPlayingUiState
}

/** [isStale] is the interactor's verdict on the five minute rule. */
data class CachedTrack(
    val nowPlaying: NowPlaying,
    val fetchedAt: Instant,
    val isStale: Boolean,
)
