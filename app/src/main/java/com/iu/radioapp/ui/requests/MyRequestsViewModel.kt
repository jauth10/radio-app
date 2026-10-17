package com.iu.radioapp.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.interactor.SongRequestInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyRequestsViewModel @Inject constructor(
    private val requests: SongRequestInteractor,
) : ViewModel() {

    private val lastRefresh = MutableStateFlow<Outcome<Unit>?>(null)
    private val reloads = MutableStateFlow(0)

    // Status changes arrive through the Room flow, not as return values.
    val uiState: StateFlow<MyRequestsUiState> =
        combine(reloads.flatMapLatest { requests.observeRequests() }, lastRefresh) { rows, refresh ->
            val list = when (rows) {
                is Outcome.Success -> rows.value
                is Outcome.Error -> return@combine MyRequestsUiState.Error(rows.failure, emptyList())
            }
            val failure = (refresh as? Outcome.Error)?.failure
            when {
                failure == Failure.Connection -> MyRequestsUiState.Offline(list)
                failure != null -> MyRequestsUiState.Error(failure, list)
                list.isEmpty() -> MyRequestsUiState.Empty
                else -> MyRequestsUiState.Content(list)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MyRequestsUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        reloads.value++
        viewModelScope.launch { lastRefresh.value = requests.refreshStatuses() }
    }

    fun retry(idempotencyKey: String) {
        viewModelScope.launch { requests.retry(idempotencyKey) }
    }

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
