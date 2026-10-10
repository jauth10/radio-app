package com.iu.radioapp.ui.search

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Track

/** [query] is the text in the field; every state carries it so the field survives each transition. */
sealed interface TrackSearchUiState {
    val query: String

    data class Loading(override val query: String) : TrackSearchUiState

    data class Content(override val query: String, val hits: List<Track>) : TrackSearchUiState

    /** No hits; [searchedFor] is null before the first search. */
    data class Empty(override val query: String, val searchedFor: String?) : TrackSearchUiState

    data class Offline(override val query: String) : TrackSearchUiState

    data class Error(override val query: String, val failure: Failure) : TrackSearchUiState
}
