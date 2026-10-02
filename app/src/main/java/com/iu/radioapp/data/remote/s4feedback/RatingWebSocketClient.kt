package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.di.ApiBaseUrl
import com.iu.radioapp.domain.Failure
import contract.common.Endpoints
import contract.common.RadioJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [RatingEventDataSource] backed by a real WS connection to /events/ratings.
 *
 * Connects, reconnects with growing backoff on a dropped connection, and
 * reports [ConnectionEvent.Failed] after [MAX_ATTEMPTS] consecutive failures -
 * except a rejected token ([CloseReason.Codes.VIOLATED_POLICY]), which fails
 * immediately without using up the retry budget, since retrying the same
 * token cannot succeed. It does not decide to fall back to polling itself -
 * per the ticket, that is RatingRepository's call, not this client's; this
 * class only reports what happened.
 *
 * The reconnect counter resets only once a connection has actually proven
 * itself by delivering a frame, not merely on a successful handshake: the
 * stub accepts the handshake even for a rejected token and only closes
 * afterwards (it cannot reject at the HTTP upgrade itself), so resetting on
 * handshake alone would hide a rapidly rejecting server behind an
 * ever-resetting counter and [ConnectionEvent.Failed] would never fire.
 * Known gap: a session that stays open but idle (no ratings for a while)
 * then drops abnormally is counted the same as a fresh failure streak -
 * there is no server-side keepalive yet to prove "still open" without
 * traffic.
 *
 * [baseUrl] is converted from http(s) to ws(s) explicitly rather than relying
 * on [HttpClient]'s default request scheme - Ktor's own docs do not state
 * whether that translation happens automatically for a WS upgrade, so this
 * stays deliberate instead of assumed.
 */
class RatingWebSocketClient @Inject constructor(
    private val client: HttpClient,
    @ApiBaseUrl private val baseUrl: String,
) : RatingEventDataSource {

    override fun events(token: String): Flow<ConnectionEvent> = connect(token, ::defaultBackoff)

    /**
     * [backoffFor] is only a parameter so a test can replace real delays with
     * near-zero ones; every real caller uses [events] above, which always
     * uses [defaultBackoff].
     */
    internal fun connect(token: String, backoffFor: (attempt: Int) -> Duration): Flow<ConnectionEvent> = flow {
        val url = wsUrl(token)
        var attempt = 0
        while (true) {
            try {
                var provenOpen = false
                client.webSocket(url) {
                    emit(ConnectionEvent.Connected)
                    for (frame in incoming) {
                        if (!provenOpen) {
                            provenOpen = true
                            attempt = 0
                        }
                        if (frame is Frame.Text) {
                            emit(ConnectionEvent.MessageReceived(RadioJson.decodeFromString(frame.readText())))
                        }
                    }
                    // The incoming loop can end without throwing even when the
                    // server ended the session abnormally (e.g. it force-closed
                    // a stale connection) - only a genuine CloseReason.Codes.NORMAL
                    // means there is nothing left to reconnect for.
                    when (closeReason.await()?.knownReason) {
                        CloseReason.Codes.NORMAL -> Unit
                        CloseReason.Codes.VIOLATED_POLICY -> throw RejectedToken()
                        else -> throw ConnectionClosedAbnormally()
                    }
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: RejectedToken) {
                emit(ConnectionEvent.Failed(Failure.Unauthorized))
                return@flow
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    emit(ConnectionEvent.Failed(Failure.Connection))
                    return@flow
                }
                emit(ConnectionEvent.Reconnecting(attempt))
                delay(backoffFor(attempt))
            }
        }
    }

    private fun wsUrl(token: String): String {
        val wsBase = baseUrl.replaceFirst(Regex("^http"), "ws").trimEnd('/')
        return "$wsBase${Endpoints.S4_WS_EVENTS}?${Endpoints.PARAM_TOKEN}=$token"
    }

    /** Marks a session that ended without throwing, but not with a normal close, as reconnect-worthy. */
    private class ConnectionClosedAbnormally : Exception()

    /** The stub rejected the token (VIOLATED_POLICY) - final, not reconnect-worthy. */
    private class RejectedToken : Exception()

    companion object {
        /** Matches the ticket: reconnect with growing backoff, report failure after five. */
        private const val MAX_ATTEMPTS = 5

        /** 1s, 2s, 4s, 8s, 16s - an assumption, the ticket only requires "growing". */
        private fun defaultBackoff(attempt: Int): Duration = (1L shl (attempt - 1)).seconds.coerceAtMost(16.seconds)
    }
}
