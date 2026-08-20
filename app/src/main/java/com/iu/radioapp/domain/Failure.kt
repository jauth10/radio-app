package com.iu.radioapp.domain

/**
 * The four error classes the whole app reasons about, agreed in RAD-1.
 *
 * This type lives in the domain package on purpose, not in data/remote: it also
 * covers Room and DataStore errors, and the dependency direction has to point
 * inward. Data sources translate an HTTP status code into one of these classes
 * and never let the wire format (contract's ErrorDto) travel any further up.
 *
 * The mapping from status code to class, and the behaviour each one triggers:
 *
 *   Connection    - no connection, timeout. Reading: serve the cache and mark it
 *                   stale. Writing: keep it in the outbox and let the delivery
 *                   worker retry.
 *   Server        - 500, 503. Retry with growing backoff, at most five attempts.
 *   Rejected      - 400, 409, 422, 429. Never retried, the reason is shown.
 *   Unauthorized  - 401, 403. Re-establish the host session; the listener path
 *                   is unaffected.
 */
sealed interface Failure {

    /** No connection or a timeout. Carries no detail: there is nothing to show. */
    data object Connection : Failure

    /** Server-side fault, 500 or 503. Retryable with growing backoff. */
    data object Server : Failure

    /**
     * The request was understood and refused on business grounds (400, 409, 422, 429).
     *
     * [reason] and [retryable] come from the server's error body, but arrive here as
     * plain String and Boolean - the wire type stays behind in the data source. A
     * rejection is never re-sent silently, even when [retryable] is true; that flag
     * says whether a later attempt could succeed at all, not that one should happen
     * on its own.
     */
    data class Rejected(
        val reason: String,
        val retryable: Boolean,
    ) : Failure

    /** Missing or expired credentials, 401 or 403. */
    data object Unauthorized : Failure
}
