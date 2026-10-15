package stubserver

import contract.s3requests.CreateSongRequestDto
import contract.s3requests.RequestStatus
import contract.s3requests.SongRequestOverviewDto
import contract.s3requests.SongRequestResponse
import contract.s3requests.SongRequestStatusDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RequestsRoutesTest {

    private suspend fun HttpClient.wish(
        key: String? = uniqueId("key"),
        listenerId: String,
        trackId: String = "trk-1",
    ): HttpResponse = post("/requests") {
        key?.let { header("Idempotency-Key", it) }
        contentType(ContentType.Application.Json)
        setBody(CreateSongRequestDto(trackId, listenerId, displayName = null, message = "Gruß", timestamp = Clock.System.now()))
    }

    @Test
    fun `a wish without an idempotency key is 400`() = stubTest { client ->
        val response = client.wish(key = null, listenerId = uniqueId("listener"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertFalse(response.error().retryable)
    }

    @Test
    fun `an accepted wish is 201 and pending until the station decides`() = stubTest { client ->
        val response = client.wish(listenerId = uniqueId("listener"))

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<SongRequestResponse>()
        assertTrue(body.requestId.startsWith("req-"))
        assertEquals(RequestStatus.PENDING, body.status)
        assertNull(body.scheduledBroadcast)

        val status = client.get("/requests/${body.requestId}").body<SongRequestStatusDto>()
        assertEquals(RequestStatus.PENDING, status.status)
    }

    @Test
    fun `sending the same wish again with the same key returns the same answer and creates nothing new`() = stubTest { client ->
        val listener = uniqueId("listener")
        val key = uniqueId("key")

        val first = client.wish(key, listener).body<SongRequestResponse>()
        val replay = client.wish(key, listener)

        assertEquals(HttpStatusCode.Created, replay.status)
        assertEquals(first, replay.body<SongRequestResponse>())
        assertEquals(1, client.get("/requests?listenerId=$listener").body<List<SongRequestOverviewDto>>().size)
    }

    @Test
    fun `a replay is answered even when the hourly limit is used up, and does not count against it`() = stubTest { client ->
        withinOneHour {
            val listener = uniqueId("listener")
            val keys = List(3) { uniqueId("key") }
            val first = keys.map { client.wish(it, listener).body<SongRequestResponse>() }

            val fourth = client.wish(uniqueId("key"), listener)
            assertEquals(HttpStatusCode.UnprocessableEntity, fourth.status)
            assertFalse(fourth.error().retryable)
            assertTrue(fourth.error().reason.contains("Wunschlimit"))

            val replay = client.wish(keys.first(), listener)
            assertEquals(HttpStatusCode.Created, replay.status)
            assertEquals(first.first(), replay.body<SongRequestResponse>())
            assertEquals(3, client.get("/requests?listenerId=$listener").body<List<SongRequestOverviewDto>>().size)
        }
    }

    @Test
    fun `the limit is per listener`() = stubTest { client ->
        withinOneHour {
            val busy = uniqueId("listener")
            repeat(3) { client.wish(listenerId = busy) }

            assertEquals(HttpStatusCode.UnprocessableEntity, client.wish(listenerId = busy).status)
            assertEquals(HttpStatusCode.Created, client.wish(listenerId = uniqueId("listener")).status)
        }
    }

    @Test
    fun `a track that must not be broadcast or does not exist is 422 with the reason`() = stubTest { client ->
        for (trackId in listOf("trk-3", "trk-unknown")) {
            val response = client.wish(listenerId = uniqueId("listener"), trackId = trackId)

            assertEquals(trackId, HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("Dieser Titel ist nicht sendefähig.", response.error().reason)
            assertFalse(response.error().retryable)
        }
    }

    @Test
    fun `a rejected wish is not stored, so a later retry of it is judged again`() = stubTest { client ->
        val listener = uniqueId("listener")
        val key = uniqueId("key")

        assertEquals(HttpStatusCode.UnprocessableEntity, client.wish(key, listener, trackId = "trk-3").status)
        assertEquals(0, client.get("/requests?listenerId=$listener").body<List<SongRequestOverviewDto>>().size)
    }

    @Test
    fun `the status of a seeded accepted wish carries the planned broadcast`() = stubTest { client ->
        val status = client.get("/requests/req-seed-accepted").body<SongRequestStatusDto>()

        assertEquals(RequestStatus.ACCEPTED, status.status)
        assertNotNull(status.scheduledBroadcast)
    }

    @Test
    fun `the status of a seeded rejected wish carries the station's reason`() = stubTest { client ->
        val status = client.get("/requests/req-seed-rejected").body<SongRequestStatusDto>()

        assertEquals(RequestStatus.REJECTED, status.status)
        assertEquals("Titel nicht im Bestand", status.reason)
    }

    @Test
    fun `an unknown request id is 404`() = stubTest { client ->
        val response = client.get("/requests/req-unknown")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Diesen Wunsch gibt es nicht.", response.error().reason)
    }

    @Test
    fun `the overview lists the wishes of one listener and needs the listener id`() = stubTest { client ->
        val seeded = client.get("/requests?listenerId=listener-seed").body<List<SongRequestOverviewDto>>()
        assertEquals(listOf("req-seed-accepted", "req-seed-rejected"), seeded.map { it.requestId })

        assertTrue(client.get("/requests?listenerId=${uniqueId("nobody")}").body<List<SongRequestOverviewDto>>().isEmpty())
        assertEquals(HttpStatusCode.BadRequest, client.get("/requests").status)
    }

    @Test
    fun `a malformed wish body is 400`() = stubTest { client ->
        val response = client.post("/requests") {
            header("Idempotency-Key", uniqueId("key"))
            contentType(ContentType.Application.Json)
            setBody("{")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
