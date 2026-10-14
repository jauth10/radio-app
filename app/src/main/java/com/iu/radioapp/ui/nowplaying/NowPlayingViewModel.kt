package com.iu.radioapp.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.NowPlayingState
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.Playback
import com.iu.radioapp.interactor.TrackInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val tracks: TrackInteractor,
) : ViewModel() {

    private val reloads = MutableStateFlow(0)

    // Polls while the screen is collecting; a reload restarts the loop and shows Loading again.
    private val current = reloads.flatMapLatest {
        flow {
            emit(null)
            while (true) {
                emit(tracks.getNowPlaying())
                delay(REFRESH_INTERVAL)
            }
        }
    }

    val uiState: StateFlow<NowPlayingUiState> =
        combine(current, tracks.observeHistory()) { outcome, history -> toUiState(outcome, history) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NowPlayingUiState.Loading)

    init {
        refreshHistory()
    }

    fun reload() {
        reloads.value++
        refreshHistory()
    }

    // The history flow shows what Room has; a failed refresh leaves it as it is.
    private fun refreshHistory() {
        viewModelScope.launch { tracks.refreshHistory() }
    }

    private fun toUiState(outcome: Outcome<NowPlayingState>?, history: List<Playback>): NowPlayingUiState =
        when (outcome) {
            null -> NowPlayingUiState.Loading
            is Outcome.Error -> when (outcome.failure) {
                Failure.Connection -> NowPlayingUiState.Offline(cached = null, history = history)
                else -> NowPlayingUiState.Error(outcome.failure, cached = null, history = history)
            }
            is Outcome.Success -> when (val state = outcome.value) {
                is NowPlayingState.Live -> NowPlayingUiState.Content(state.nowPlaying, history)
                NowPlayingState.TalkSegment -> NowPlayingUiState.Empty(history)
                is NowPlayingState.Cached -> {
                    val cached = CachedTrack(state.nowPlaying, state.fetchedAt, state.isStale)
                    when (state.cause) {
                        Failure.Connection -> NowPlayingUiState.Offline(cached, history)
                        else -> NowPlayingUiState.Error(state.cause, cached, history)
                    }
                }
            }
        }

    companion object {
        // AK 1.7 asks for a track change within 15 seconds.
        val REFRESH_INTERVAL = 10.seconds
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
