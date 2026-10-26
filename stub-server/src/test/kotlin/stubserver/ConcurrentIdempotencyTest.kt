package stubserver

import contract.common.RadioJson
import contract.s3requests.SongRequestOverviewDto
import contract.s3requests.SongRequestResponse
import contract.s4feedback.AggregateDto
import contract.s4feedback.RatingResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.ServerSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Simultaneous requests with the same Idempotency-Key must create exactly one
 * wish or rating. Only a real server handles them in parallel - Ktor's
 * in-process test host does not - so this one runs Netty on a free port.
 */
@OptIn(ExperimentalTime::class)
class ConcurrentIdempotencyTest {

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private lateinit var base: String

    @Before
    fun start() {
        val port = ServerSocket(0).use { it.localPort }
        base = "http://127.0.0.1:$port"
        server = embeddedServer(Netty, port = port, host = "127.0.0.1", module = Application::module)
            .also { it.start(wait = false) }
        client = HttpClient(OkHttp) {
            expectSuccess = false
            // OkHttp allows only five requests per host at a time by default.
            engine { config { dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 64 }) } }
        }
    }

    @After
    fun stop() {
        client.close()
        server.stop(0, 0)
    }

    /** Sends [request] [PARALLEL] times at once, released together by one gate. */
    private fun simultaneously(request: suspend () -> HttpResponse): List<Pair<HttpStatusCode, String>> = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val calls = List(PARALLEL) {
            async(Dispatchers.IO) {
                gate.await()
                val response = request()
                response.status to response.bodyAsText()
            }
        }
        gate.complete(Unit)
        calls.awaitAll()
    }

    @Test
    fun `simultaneous wishes with the same key create exactly one wish`() {
        repeat(ROUNDS) { round ->
            val key = uniqueId("key")
            val listener = uniqueId("listener")
            val body = """{"trackId":"trk-1","listenerId":"$listener","message":"Gruß","timestamp":"${Clock.System.now()}"}"""

            val answers = simultaneously {
                client.post("$base/requests") {
                    header("Idempotency-Key", key)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }

            assertEquals("round $round: every answer is 201", List(PARALLEL) { HttpStatusCode.Created }, answers.map { it.first })
            val ids = answers.map { RadioJson.decodeFromString<SongRequestResponse>(it.second).requestId }.toSet()
            assertEquals("round $round: one request id for all", 1, ids.size)
            val stored = runBlocking { client.get("$base/requests?listenerId=$listener").bodyAsText() }
            assertEquals("round $round: one stored wish", 1, RadioJson.decodeFromString<List<SongRequestOverviewDto>>(stored).size)
        }
    }

    @Test
    fun `simultaneous ratings with the same key count once`() {
        repeat(ROUNDS) { round ->
            val key = uniqueId("key")
            val before = playlistCount()
            val body = """{"target":"PLAYLIST","referenceId":"show-1","value":4,"listenerId":"${uniqueId("listener")}","timestamp":"${Clock.System.now()}"}"""

            val answers = simultaneously {
                client.post("$base/ratings") {
                    header("Idempotency-Key", key)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }

            assertEquals("round $round: every answer is 201", List(PARALLEL) { HttpStatusCode.Created }, answers.map { it.first })
            val ids = answers.map { RadioJson.decodeFromString<RatingResponse>(it.second).ratingId }.toSet()
            assertEquals("round $round: one rating id for all", 1, ids.size)
            assertEquals("round $round: counted once", before + 1, playlistCount())
        }
    }

    private fun playlistCount(): Int = runBlocking {
        RadioJson.decodeFromString<AggregateDto>(client.get("$base/ratings/aggregate?showId=show-1").bodyAsText()).playlistRatingCount
    }

    private companion object {
        const val PARALLEL = 10
        // Several rounds, because a race does not show up in every single one.
        const val ROUNDS = 20
    }
}
