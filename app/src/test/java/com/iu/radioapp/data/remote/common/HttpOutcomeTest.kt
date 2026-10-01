package com.iu.radioapp.data.remote.common

import com.iu.radioapp.data.remote.s1playout.HttpPlayoutDataSource
import com.iu.radioapp.data.remote.s2archive.HttpArchiveDataSource
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.common.RadioJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Review finding from PR #11: an error body that isn't an ErrorDto (no body at
 * all, HTML from a proxy, a differently shaped JSON) used to throw out of the
 * data source instead of becoming an Outcome, contradicting PlayoutDataSource's
 * own "never a thrown exception" promise - and 502/504 were wrongly routed
 * through the ErrorDto path instead of Failure.Server.
 */
class HttpOutcomeTest {

    private fun clientRespondingWith(
        status: HttpStatusCode,
        body: String? = null,
        contentType: String? = "application/json",
    ): HttpClient {
        val engine = MockEngine {
            respond(
                content = body?.let { ByteReadChannel(it) } ?: ByteReadChannel.Empty,
                status = status,
                headers = contentType?.let { headersOf(HttpHeaders.ContentType, it) } ?: headersOf(),
            )
        }
        return HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(RadioJson) }
            defaultRequest { url("http://localhost/") }
        }
    }

    private fun clientThatFailsToConnect(): HttpClient {
        val engine = MockEngine { throw IOException("connection refused") }
        return HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(RadioJson) }
            defaultRequest { url("http://localhost/") }
        }
    }

    @Test
    fun `200 with a valid body succeeds`() = runTest {
        val client = clientRespondingWith(
            HttpStatusCode.OK,
            body = """{"trackId":"trk-1","artist":"A","title":"T","broadcastable":true}""",
        )

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-1")

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `200 with an unreadable body is a diagnostic rejection, not a crash`() = runTest {
        val client = clientRespondingWith(HttpStatusCode.OK, body = "not json at all")

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-1")

        assertEquals(Outcome.Error(Failure.Rejected("HTTP 200", retryable = false)), result)
    }

    @Test
    fun `404 with no body at all is a diagnostic rejection, not a crash`() = runTest {
        val client = clientRespondingWith(HttpStatusCode.NotFound, body = null, contentType = null)

        val result = HttpArchiveDataSource(client).getTrackDetail("missing")

        assertEquals(Outcome.Error(Failure.Rejected("HTTP 404", retryable = false)), result)
    }

    @Test
    fun `404 with a real ErrorDto still carries the server's reason`() = runTest {
        val client = clientRespondingWith(
            HttpStatusCode.NotFound,
            body = """{"reason":"unknown trackId: trk-9","retryable":false}""",
        )

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-9")

        assertEquals(Outcome.Error(Failure.Rejected("unknown trackId: trk-9", retryable = false)), result)
    }

    @Test
    fun `400 with a differently shaped JSON body is a diagnostic rejection, not a crash`() = runTest {
        val client = clientRespondingWith(HttpStatusCode.BadRequest, body = """{"foo":"bar"}""")

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-1")

        assertEquals(Outcome.Error(Failure.Rejected("HTTP 400", retryable = false)), result)
    }

    @Test
    fun `502 with an HTML body is Server, not a crash and not Rejected`() = runTest {
        val client = clientRespondingWith(
            HttpStatusCode.BadGateway,
            body = "<html><body>Bad Gateway</body></html>",
            contentType = "text/html",
        )

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-1")

        assertEquals(Outcome.Error(Failure.Server), result)
    }

    @Test
    fun `500 with an ErrorDto body is still Server - the body is not needed`() = runTest {
        val client = clientRespondingWith(
            HttpStatusCode.InternalServerError,
            body = """{"reason":"internal error","retryable":true}""",
        )

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-1")

        assertEquals(Outcome.Error(Failure.Server), result)
    }

    @Test
    fun `503 is Server`() = runTest {
        val client = clientRespondingWith(HttpStatusCode.ServiceUnavailable, body = null, contentType = null)

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-1")

        assertEquals(Outcome.Error(Failure.Server), result)
    }

    @Test
    fun `401 is Unauthorized`() = runTest {
        val client = clientRespondingWith(HttpStatusCode.Unauthorized, body = null, contentType = null)

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-1")

        assertEquals(Outcome.Error(Failure.Unauthorized), result)
    }

    @Test
    fun `a connection failure is Connection, not a crash`() = runTest {
        val client = clientThatFailsToConnect()

        val result = HttpArchiveDataSource(client).getTrackDetail("trk-1")

        assertEquals(Outcome.Error(Failure.Connection), result)
    }

    @Test
    fun `204 on current-track is success with null`() = runTest {
        val client = clientRespondingWith(HttpStatusCode.NoContent, body = null, contentType = null)

        val result = HttpPlayoutDataSource(client).getCurrentTrack()

        assertEquals(Outcome.Success(null), result)
    }
}
