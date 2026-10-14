package com.iu.radioapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.interactor.SongRequestInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackSearchViewModel @Inject constructor(
    private val requests: SongRequestInteractor,
) : ViewModel() {

    private val state = MutableStateFlow<TrackSearchUiState>(TrackSearchUiState.Empty(query = "", searchedFor = null))
    val uiState: StateFlow<TrackSearchUiState> = state.asStateFlow()

    private var search: Job? = null

    fun onQueryChange(query: String) {
        state.update { current ->
            when (current) {
                is TrackSearchUiState.Loading -> current.copy(query = query)
                is TrackSearchUiState.Content -> current.copy(query = query)
                is TrackSearchUiState.Empty -> current.copy(query = query)
                is TrackSearchUiState.Offline -> current.copy(query = query)
                is TrackSearchUiState.Error -> current.copy(query = query)
            }
        }
    }

    // A blank query never reaches the archive; the interactor answers it with an empty list (AK 3.1).
    fun search() {
        val query = state.value.query
        search?.cancel()
        state.value = TrackSearchUiState.Loading(query)
        search = viewModelScope.launch {
            val outcome = requests.searchTracks(query)
            state.value = when (outcome) {
                is Outcome.Success -> if (outcome.value.isEmpty()) {
                    TrackSearchUiState.Empty(query, searchedFor = query.trim().ifEmpty { null })
                } else {
                    TrackSearchUiState.Content(query, outcome.value)
                }
                is Outcome.Error -> when (outcome.failure) {
                    Failure.Connection -> TrackSearchUiState.Offline(query)
                    else -> TrackSearchUiState.Error(query, outcome.failure)
                }
            }
        }
    }
}
