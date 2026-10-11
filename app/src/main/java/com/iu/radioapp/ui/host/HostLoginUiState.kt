package com.iu.radioapp.ui.host

import com.iu.radioapp.domain.Failure

/** Empty is the form before any code was typed; submitting is only possible from Content, Error and Offline. */
sealed interface HostLoginUiState {

    data object Empty : HostLoginUiState

    data class Content(val code: String) : HostLoginUiState

    data class Loading(val code: String) : HostLoginUiState

    data class Offline(val code: String) : HostLoginUiState

    /** Unauthorized is an invalid code, Rejected the lockout after too many attempts. */
    data class Error(val code: String, val failure: Failure) : HostLoginUiState
}
