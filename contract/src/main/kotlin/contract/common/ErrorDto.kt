package contract.common

import kotlinx.serialization.Serializable

/**
 * Shared error WIRE FORMAT for all systems (S1-S4).
 *
 * Important: ErrorDto is ONLY the transport format over the wire. It
 * deliberately does NOT travel up to the repository. The data source
 * translates an HTTP error immediately into one of the four app error
 * classes (in app/data/remote). `reason` and `retryable` then end up in
 * the business-rejection error class.
 *
 * Reason for the split: if ErrorDto (with HTTP semantics) traveled upward,
 * the question "which error class is this?" would have to be answered twice.
 */
@Serializable
data class ErrorDto(
    val reason: String,
    val retryable: Boolean,
)
