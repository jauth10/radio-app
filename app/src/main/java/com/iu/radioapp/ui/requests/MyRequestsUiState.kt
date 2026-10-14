package com.iu.radioapp.ui.requests

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.SongRequestWithDelivery

/** The list comes from Room and stays visible when the status refresh fails. */
sealed interface MyRequestsUiState {

    data object Loading : MyRequestsUiState

    data class Content(val requests: List<SongRequestWithDelivery>) : MyRequestsUiState

    data object Empty : MyRequestsUiState

    data class Offline(val requests: List<SongRequestWithDelivery>) : MyRequestsUiState

    data class Error(val failure: Failure, val requests: List<SongRequestWithDelivery>) : MyRequestsUiState
}
