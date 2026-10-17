package stubserver

import contract.s1playout.CurrentTrackDto
import contract.s1playout.HistoryEntryDto
import contract.s1playout.HostLoginResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PlayoutRoutesTest {

    @Test
    fun `current track is delivered with show and host`() = stubTest { client ->
        client.control("""{"action":"changeTrack","trackId":"trk-1"}""")

        val response = client.get("/playout/current")

        assertEquals(HttpStatusCode.OK, response.status)
        val track = response.body<CurrentTrackDto>()
        assertEquals("trk-1", track.trackId)
        assertEquals("show-1", track.showId)
        assertEquals("host-1", track.hostId)
        assertEquals("Alex Host", track.hostName)
    }

    @Test
    fun `a talk segment is 204 without a body, not an error`() = stubTest { client ->
        client.control("""{"action":"changeTrack","talkSegment":true}""")

        val response = client.get("/playout/current")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.body<String>())
    }

    @Test
    fun `history is newest first and honours the limit`() = stubTest { client ->
        client.control("""{"action":"changeTrack","trackId":"trk-2"}""")

        val all = client.get("/playout/history?limit=1000").body<List<HistoryEntryDto>>()
        assertTrue(all.size >= 3)
        assertEquals("trk-2", all.first().trackId)
        assertEquals(all.sortedByDescending { it.startedAt }, all)

        assertEquals(2, client.get("/playout/history?limit=2").body<List<HistoryEntryDto>>().size)
        assertEquals(0, client.get("/playout/history?limit=0").body<List<HistoryEntryDto>>().size)
    }

    @Test
    fun `history rejects a negative or non numeric limit`() = stubTest { client ->
        assertEquals(HttpStatusCode.BadRequest, client.get("/playout/history?limit=-1").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/playout/history?limit=abc").status)
    }

    @Test
    fun `a valid host code logs in for one hour and every login gets its own token`() = stubTest { client ->
        suspend fun login() = client.post("/playout/host/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"hostCode":"ALEX-2026","deviceId":"${uniqueId("device")}"}""")
        }

        val before = Clock.System.now()
        val first = login()
        assertEquals(HttpStatusCode.OK, first.status)
        val session = first.body<HostLoginResponse>()
        assertEquals("host-1", session.hostId)
        assertEquals("Alex Host", session.hostName)
        assertTrue(session.sessionToken.isNotBlank())
        assertTrue(session.validUntil >= before + 59.minutes)
        assertTrue(session.validUntil <= Clock.System.now() + 61.minutes)

        assertNotEquals(session.sessionToken, login().body<HostLoginResponse>().sessionToken)
    }

    @Test
    fun `the second host code belongs to the second host`() = stubTest { client ->
        val session = client.post("/playout/host/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"hostCode":"SAM-2026","deviceId":"${uniqueId("device")}"}""")
        }.body<HostLoginResponse>()

        assertEquals("host-2", session.hostId)
    }

    @Test
    fun `an unknown code is 401 with the reason of the station and is not retryable`() = stubTest { client ->
        val response = client.post("/playout/host/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"hostCode":"WRONG","deviceId":"${uniqueId("device")}"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val error = response.error()
        assertEquals("Der Moderationscode ist ungültig.", error.reason)
        assertFalse(error.retryable)
    }

    @Test
    fun `the fifth failed attempt locks the device, even for the right code, and other devices are unaffected`() =
        stubTest { client ->
            val device = uniqueId("device")
            suspend fun attempt(code: String, deviceId: String = device) = client.post("/playout/host/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"hostCode":"$code","deviceId":"$deviceId"}""")
            }

            repeat(4) { assertEquals(HttpStatusCode.Unauthorized, attempt("WRONG").status) }
            val fifth = attempt("WRONG")
            assertEquals(HttpStatusCode.TooManyRequests, fifth.status)
            assertTrue(fifth.error().retryable)
            assertTrue(fifth.error().reason.contains("5 Minuten"))

            assertEquals(HttpStatusCode.TooManyRequests, attempt("ALEX-2026").status)
            assertEquals(HttpStatusCode.OK, attempt("ALEX-2026", deviceId = uniqueId("other-device")).status)
        }

    @Test
    fun `a successful login resets the failed attempts`() = stubTest { client ->
        val device = uniqueId("device")
        suspend fun attempt(code: String) = client.post("/playout/host/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"hostCode":"$code","deviceId":"$device"}""")
        }

        repeat(4) { attempt("WRONG") }
        assertEquals(HttpStatusCode.OK, attempt("ALEX-2026").status)
        repeat(4) { assertEquals(HttpStatusCode.Unauthorized, attempt("WRONG").status) }
    }

    @Test
    fun `a malformed login body is 400 and a missing content type is 415, neither is a server fault`() = stubTest { client ->
        val malformed = client.post("/playout/host/login") {
            contentType(ContentType.Application.Json)
            setBody("not json")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertFalse(malformed.error().retryable)

        val noContentType = client.post("/playout/host/login") { setBody("""{"hostCode":"ALEX-2026","deviceId":"d"}""") }
        assertEquals(HttpStatusCode.UnsupportedMediaType, noContentType.status)
        assertFalse(noContentType.error().retryable)
    }
}
