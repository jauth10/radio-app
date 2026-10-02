package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.domain.Failure
import contract.s4feedback.RatingEventMessage
import kotlinx.coroutines.flow.Flow

/**
 * WS /events/ratings (RAD-15) as a data source interface, analogous to the
 * other S1-S4 data sources: [RatingWebSocketClient] is the real implementation,
 * a fake in tests stands in for RAD-16's switch-to-polling logic rather than a
 * real server.
 */
interface RatingEventDataSource {
    fun events(token: String): Flow<ConnectionEvent>
}

/**
 * [ConnectionEvent.Connected] fires as soon as the handshake succeeds, before
 * the token is known to be accepted - the stub cannot reject at the HTTP
 * upgrade itself, only after accepting it (see EventsRoutes), so a rejected
 * token can still follow immediately as `Failed(Failure.Unauthorized)`.
 */
sealed interface ConnectionEvent {
    data object Connected : ConnectionEvent
    data class MessageReceived(val message: RatingEventMessage) : ConnectionEvent
    data class Reconnecting(val failedAttempts: Int) : ConnectionEvent

    /**
     * [Failure.Unauthorized] fires immediately on a rejected token - retrying
     * the same token will not help, the caller has to rebuild the session.
     * Any other [Failure] fires after five consecutive failed attempts.
     */
    data class Failed(val failure: Failure) : ConnectionEvent
}
