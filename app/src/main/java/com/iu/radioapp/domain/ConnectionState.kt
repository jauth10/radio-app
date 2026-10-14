package com.iu.radioapp.domain

/** How the host view currently receives ratings; shown to the host as is. */
sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val failedAttempts: Int) : ConnectionState
    data object Polling : ConnectionState
}
