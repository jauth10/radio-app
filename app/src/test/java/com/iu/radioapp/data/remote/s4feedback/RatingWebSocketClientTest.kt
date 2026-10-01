package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.domain.Failure
import contract.common.RadioJson
import contract.s4feedback.EventType
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingEventMessage
import contract.s4feedback.RatingTarget
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Review findings this pre-empts (RAD-15 PR #12, jauth10):
 *  - A server that accepts the handshake and then immediately closes (the
 *    stub does exactly this for a rejected token, see EventsRoutes) must not
 *    reset the reconnect counter on handshake alone, or Failed never fires.
 *  - A rejected token (VIOLATED_POLICY) must fail immediately as
 *    Unauthorized, not spend the five-attempt retry budget.
 *
 * MockEngine cannot help with either - it has no WebSocket support (KTOR-537)
 * - so this spins up a real, throwaway, in-process server instead.
 *
 * [RatingWebSocketClient.connect] takes an injectable backoff for exactly
 * this reason: with the real 1s..16s schedule the first test would take
 * ~15s. The server is stopped only once it has confirmed closing the first
 * connection ([firstConnectionClosed]), not after a guessed delay - with a
 * fast backoff, a fixed sleep would race the client's own reconnect attempt.
 */
@OptIn(ExperimentalTime::class)
class RatingWebSocketClientTest {

    private val fastBackoff: (Int) -> Duration = { 50.milliseconds }

    @Test
    fun `receives a message, then reconnects after an abnormal close and reports Failed after five failures`() = runTest {
        var connectionCount = 0
        val port = 19191
        val firstConnectionClosed = CompletableDeferred<Unit>()

        val server = embeddedServer(Netty, port = port) {
            install(ServerWebSockets)
            routing {
                webSocket("/events/ratings") {
                    connectionCount++
                    if (connectionCount == 1) {
                        val message = RatingEventMessage(
                            type = EventType.NEW_RATING,
                            t0 = Clock.System.now(),
                            rating = RatingEventDto("rat-x", RatingTarget.PLAYLIST, 5, null, Clock.System.now(), null),
                        )
                        send(Frame.Text(RadioJson.encodeToString(message)))
                        close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "forced disconnect for test"))
                        firstConnectionClosed.complete(Unit)
                    }
                }
            }
        }
        server.start(wait = false)

        val client = HttpClient(OkHttp) { install(WebSockets) }
        val wsClient = RatingWebSocketClient(client, baseUrl = "http://127.0.0.1:$port/")

        val events = mutableListOf<ConnectionEvent>()
        val job = launch {
            wsClient.connect("any-token", fastBackoff).toList(events)
        }

        // Stop the server only once it has actually closed the first
        // connection - not after a guessed delay, which a fast backoff could
        // race (the client reconnecting successfully before the stop lands).
        firstConnectionClosed.await()
        server.stop(0, 0)
        job.join()

        assertTrue(
            "expected a Connected event for the handshake",
            events.any { it is ConnectionEvent.Connected },
        )
        assertTrue(
            "expected the message sent before the forced disconnect",
            events.any { it is ConnectionEvent.MessageReceived },
        )
        assertEquals(
            "five consecutive failures (the forced close plus four refused reconnects) means four Reconnecting events before Failed",
            listOf(1, 2, 3, 4),
            events.filterIsInstance<ConnectionEvent.Reconnecting>().map { it.failedAttempts },
        )
        assertEquals(ConnectionEvent.Failed(Failure.Connection), events.last())

        client.close()
    }

    @Test
    fun `a rejected token fails immediately as Unauthorized, without spending the retry budget`() = runTest {
        val port = 19192

        val server = embeddedServer(Netty, port = port) {
            install(ServerWebSockets)
            routing {
                // Mirrors EventsRoutes: the stub cannot reject at the HTTP
                // upgrade itself, only after accepting the handshake.
                webSocket("/events/ratings") {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid or missing token"))
                }
            }
        }
        server.start(wait = false)

        val client = HttpClient(OkHttp) { install(WebSockets) }
        val wsClient = RatingWebSocketClient(client, baseUrl = "http://127.0.0.1:$port/")

        val events = wsClient.connect("bad-token", fastBackoff).toList()

        server.stop(0, 0)
        client.close()

        assertTrue(
            "a rejected token must not reconnect at all",
            events.none { it is ConnectionEvent.Reconnecting },
        )
        assertEquals(
            listOf(ConnectionEvent.Connected, ConnectionEvent.Failed(Failure.Unauthorized)),
            events,
        )
    }
}
