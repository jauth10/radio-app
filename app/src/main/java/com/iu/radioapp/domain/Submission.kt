package com.iu.radioapp.domain

/** [Refused] is the app's own pre-check; [Failure.Rejected] stays the station's answer. */
sealed interface Submission<out T> {

    data class Queued<T>(val value: T) : Submission<T>

    data class Refused(val reason: RefusalReason) : Submission<Nothing>

    data class Failed(val failure: Failure) : Submission<Nothing>
}

enum class RefusalReason {
    TRACK_NOT_BROADCASTABLE,
    NO_SHOW_ON_AIR,
    HOST_UNKNOWN,
}
