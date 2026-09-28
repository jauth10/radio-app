package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.di.ApiBaseUrl
import contract.common.Endpoints
import contract.common.RadioJson
import contract.s4feedback.RatingEventMessage
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
 * Client for WS /events/ratings.
 *
 * Connects, reconnects with growing backoff on a dropped connection, and
 * reports [ConnectionEvent.Failed] after [MAX_ATTEMPTS] consecutive failures.
 * It does not decide to fall back to polling itself - per the ticket, that is
 * RatingRepository's call, not this client's; this class only reports what
 * happened.
 *
 * [baseUrl] is converted from http(s) to ws(s) explicitly rather than relying
 * on [HttpClient]'s default request scheme - Ktor's own docs do not state
 * whether that translation happens automatically for a WS upgrade, so this
 * stays deliberate instead of assumed.
 */
class RatingWebSocketClient @Inject constructor(
    private val client: HttpClient,
    @ApiBaseUrl private val baseUrl: String,
) {

    sealed interface ConnectionEvent {
        data class MessageReceived(val message: RatingEventMessage) : ConnectionEvent
        data object Reconnecting : ConnectionEvent
        /** Five failed attempts in a row - the caller decides what happens next. */
        data object Failed : ConnectionEvent
    }

    /**
     * Connects and stays connected, emitting every message as it arrives.
     * A successful connection resets the failure count, so a brief hiccup
     * does not use up the whole budget - only [MAX_ATTEMPTS] *consecutive*
     * failures end the flow with [ConnectionEvent.Failed].
     */
    fun connect(token: String): Flow<ConnectionEvent> = connect(token, ::defaultBackoff)

    /**
     * [backoffFor] is only a parameter so a test can replace real delays with
     * near-zero ones; every real caller uses [connect] above, which always
     * uses [defaultBackoff].
     */
    internal fun connect(token: String, backoffFor: (attempt: Int) -> Duration): Flow<ConnectionEvent> = flow {
        val url = wsUrl(token)
        var attempt = 0
        while (true) {
            try {
                client.webSocket(url) {
                    attempt = 0
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            emit(ConnectionEvent.MessageReceived(RadioJson.decodeFromString(frame.readText())))
                        }
                    }
                    // The incoming loop can end without throwing even when the
                    // server ended the session abnormally (e.g. it force-closed
                    // a stale connection) - only a genuine CloseReason.Codes.NORMAL
                    // means there is nothing left to reconnect for.
                    val reason = closeReason.await()
                    if (reason?.knownReason != CloseReason.Codes.NORMAL) {
                        throw ConnectionClosedAbnormally(reason)
                    }
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    emit(ConnectionEvent.Failed)
                    return@flow
                }
                emit(ConnectionEvent.Reconnecting)
                delay(backoffFor(attempt))
            }
        }
    }

    private fun wsUrl(token: String): String {
        val wsBase = baseUrl.replaceFirst(Regex("^http"), "ws").trimEnd('/')
        return "$wsBase${Endpoints.S4_WS_EVENTS}?${Endpoints.PARAM_TOKEN}=$token"
    }

    /** Marks a session that ended without throwing, but not with a normal close, as reconnect-worthy. */
    private class ConnectionClosedAbnormally(reason: CloseReason?) : Exception("closed abnormally: $reason")

    companion object {
        /** Matches the ticket: reconnect with growing backoff, report failure after five. */
        private const val MAX_ATTEMPTS = 5

        /** 1s, 2s, 4s, 8s, 16s - an assumption, the ticket only requires "growing". */
        private fun defaultBackoff(attempt: Int): Duration = (1L shl (attempt - 1)).seconds.coerceAtMost(16.seconds)
    }
}
