package com.iu.radioapp.domain

/**
 * Result of an operation that can fail: either a value or one of the four error
 * classes.
 *
 * Every data source returns this type. Failures are values, not exceptions, for
 * two reasons agreed in RAD-1: Kotlin has no checked exceptions, so a thrown
 * error is invisible in the signature; and a blanket catch inside a suspend
 * function swallows CancellationException.
 *
 * [Error] is deliberately Outcome<Nothing> so it fits wherever an Outcome<T> is
 * expected, whatever T happens to be.
 */
sealed interface Outcome<out T> {

    /** The operation succeeded and produced [value]. */
    data class Success<T>(val value: T) : Outcome<T>

    /** The operation failed with [failure]. */
    data class Error(val failure: Failure) : Outcome<Nothing>
}
