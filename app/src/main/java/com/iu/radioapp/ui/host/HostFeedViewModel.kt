package com.iu.radioapp.ui.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.HostFeed
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.interactor.HostInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HostFeedViewModel @Inject constructor(
    private val hosts: HostInteractor,
) : ViewModel() {

    private val reloads = MutableStateFlow(0)

    private val feed = reloads.flatMapLatest { hosts.observeHostFeed() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    // Re-checks the show on air while the view is visible, so talk segments and stale data hide the numbers.
    private val showCheck = flow {
        emit(null)
        while (true) {
            emit(hosts.getCurrentAggregate())
            delay(REFRESH_INTERVAL)
        }
    }

    val access: StateFlow<HostAccess> = feed
        .map { feed ->
            when (feed) {
                HostFeed.SignedOut -> HostAccess.SignedOut(sessionExpired = false)
                HostFeed.SessionExpired -> HostAccess.SignedOut(sessionExpired = true)
                is HostFeed.Active -> HostAccess.SignedIn
                is HostFeed.Unavailable -> HostAccess.Unavailable(feed.failure)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HostAccess.Checking)

    val uiState: StateFlow<HostFeedUiState> = combine(feed, showCheck, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HostFeedUiState.Loading)

    fun reload() {
        reloads.value++
    }

    fun signOut() {
        viewModelScope.launch { hosts.endSession() }
    }

    private fun toUiState(feed: HostFeed, check: Outcome<RatingAggregate?>?): HostFeedUiState {
        if (feed !is HostFeed.Active || check == null) return HostFeedUiState.Loading
        val view = HostFeedView(
            ratings = feed.ratings,
            connection = feed.connection,
            showKnown = feed.showId != null,
            numbers = null,
            talkSegment = false,
        )
        return when (check) {
            is Outcome.Error -> when (check.failure) {
                Failure.Connection -> HostFeedUiState.Offline(view)
                else -> HostFeedUiState.Error(check.failure, view)
            }
            is Outcome.Success -> {
                val checked = check.value
                val shown = if (checked == null) {
                    view.copy(talkSegment = true)
                } else {
                    view.copy(numbers = feed.aggregate ?: checked)
                }
                if (shown.ratings.isEmpty()) HostFeedUiState.Empty(shown) else HostFeedUiState.Content(shown)
            }
        }
    }

    companion object {
        val REFRESH_INTERVAL = 10.seconds
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
