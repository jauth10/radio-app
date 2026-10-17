package stubserver

import contract.common.ErrorDto
import contract.common.RadioJson
import contract.s1playout.HostLoginResponse
import contract.s4feedback.RatingEventMessage
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingTarget
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull
import stubserver.control.StubControl
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Runs [block] against the real stub module (all routes, error handling, event
 * channel) in Ktor's in-process test host.
 *
 * The stores are process-wide objects, so state leaks between tests. Every test
 * therefore uses its own listener/device ids and idempotency keys and compares
 * counts relative to a starting value; only armed error patterns are cleared
 * here, since a leftover pattern would break an unrelated test.
 */
fun stubTest(block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit) = testApplication {
    application { module() }
    StubControl.clear()
    val client = createClient { install(ContentNegotiation) { json(RadioJson) } }
    block(client)
    StubControl.clear()
}

fun uniqueId(prefix: String) = "$prefix-${UUID.randomUUID()}"

suspend fun HttpResponse.error(): ErrorDto = body()

suspend fun HttpClient.control(json: String): HttpResponse = post("/stub/control") {
    contentType(ContentType.Application.Json)
    setBody(json)
}

suspend fun HttpClient.hostToken(deviceId: String = uniqueId("device")): String =
    post("/playout/host/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"hostCode":"ALEX-2026","deviceId":"$deviceId"}""")
    }.body<HostLoginResponse>().sessionToken

@OptIn(ExperimentalTime::class)
suspend fun HttpClient.rate(
    key: String = uniqueId("key"),
    listenerId: String = uniqueId("listener"),
    target: RatingTarget = RatingTarget.PLAYLIST,
    referenceId: String = if (target == RatingTarget.PLAYLIST) "show-1" else "host-1",
    value: Int = 4,
    comment: String? = null,
    displayName: String? = null,
): HttpResponse = post("/ratings") {
    header("Idempotency-Key", key)
    contentType(ContentType.Application.Json)
    setBody(RatingRequest(target, referenceId, value, comment, listenerId, Clock.System.now(), displayName))
}

/**
 * Ratings and wishes are limited per wall-clock hour. A test that needs several
 * submissions inside one hour is repeated, with fresh ids, if the hour rolled
 * over while it ran - otherwise it would fail once an hour for no real reason.
 */
@OptIn(ExperimentalTime::class)
suspend fun withinOneHour(block: suspend () -> Unit) {
    repeat(3) {
        val hour = stubserver.common.hourBucket(Clock.System.now())
        try {
            block()
            return
        } catch (e: AssertionError) {
            if (stubserver.common.hourBucket(Clock.System.now()) == hour) throw e
        }
    }
    error("the hour changed during every attempt")
}

suspend fun ApplicationTestBuilder.eventSession(token: String?): DefaultClientWebSocketSession {
    val ws = createClient { install(WebSockets) }
    val path = if (token == null) "/events/ratings" else "/events/ratings?token=$token"
    return ws.webSocketSession(path)
}

/** The next text frame as an event message, or null if nothing arrives within [waitMs]. */
suspend fun DefaultClientWebSocketSession.nextMessage(waitMs: Long = 2_000): RatingEventMessage? =
    withTimeoutOrNull(waitMs) {
        val frame = incoming.receive() as Frame.Text
        RadioJson.decodeFromString<RatingEventMessage>(frame.readText())
    }

/**
 * A broadcast only reaches a session once the server has registered it, which
 * happens just after the handshake. Rates until one message arrives (and takes
 * its companion), so a test can start from a session that is known to listen.
 */
suspend fun HttpClient.awaitRegistered(session: DefaultClientWebSocketSession) {
    repeat(40) {
        rate()
        if (session.nextMessage(waitMs = 150) != null) {
            session.drain()
            return
        }
    }
    error("the session was never registered for broadcasts")
}

/** Reads and drops whatever is still on its way; returns once nothing arrives for a moment. */
suspend fun DefaultClientWebSocketSession.drain() {
    while (nextMessage(waitMs = 250) != null) {
        // keep dropping
    }
}
