package com.iu.radioapp.ui.request

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.domain.Track

sealed interface SongRequestUiState {

    data object Loading : SongRequestUiState

    data class Content(val form: RequestForm) : SongRequestUiState

    /** Nothing to request: the app itself refuses this track (AK 3.3). */
    data class Empty(val track: Track, val refusal: RefusalReason) : SongRequestUiState

    /** The archive is unreachable; the request can still be queued and the station decides (AK 3.4). */
    data class Offline(val form: RequestForm) : SongRequestUiState

    /** [form] stays usable after a server fault and is null when the archive rejected the track itself. */
    data class Error(val failure: Failure, val form: RequestForm?) : SongRequestUiState
}

/** [submitFailure] is a technical failure or rejection returned by the submission itself. */
data class RequestForm(
    val track: Track,
    val message: String,
    val displayName: String,
    val submitting: Boolean = false,
    val queued: Boolean = false,
    val submitFailure: Failure? = null,
)
