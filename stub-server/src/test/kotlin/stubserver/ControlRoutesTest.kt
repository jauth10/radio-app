package stubserver

import contract.s1playout.CurrentTrackDto
import contract.s1playout.HistoryEntryDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import stubserver.control.ControlResponse
import kotlin.time.ExperimentalTime

/**
 * The stub control endpoint is a test tool, not part of the assumed station
 * landscape (RAD-17). These tests pin down what the offline and error concept
 * relies on: every error pattern, how it is scoped and used up, and that it
 * never leaves traces.
 */
@OptIn(ExperimentalTime::class)
class ControlRoutesTest {

    private suspend fun HttpClient.arm(code: String, area: String = "all", count: Int = 1, extra: String = "") =
        control("""{"action":"injectFailure","code":"$code","area":"$area","count":$count$extra}""")

    // ---- injectFailure: the patterns ----

    @Test
    fun `503 is a retryable server fault`() = stubTest { client ->
        client.arm("503", area = "playout")

        val response = client.get("/playout/current")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.error().retryable)
        assertTrue(response.error().reason.contains("Stub-Steuerung"))
    }

    @Test
    fun `422 is a rejection that is not retryable`() = stubTest { client ->
        client.arm("422", area = "ratings")

        val response = client.rate()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertFalse(response.error().retryable)
    }

    @Test
    fun `409 is a conflict that is not retryable`() = stubTest { client ->
        client.arm("409", area = "requests")

        val response = client.get("/requests?listenerId=${uniqueId("l")}")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertFalse(response.error().retryable)
    }

    @Test
    fun `401 is an expired session that is not retryable`() = stubTest { client ->
        client.arm("401", area = "archive")

        val response = client.get("/archive/tracks?q=song")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(response.error().retryable)
    }

    @Test
    fun `timeout holds the request for the given time and then answers 504`() = stubTest { client ->
        client.arm("timeout", area = "playout", extra = ""","durationMs":400""")

        val started = System.nanoTime()
        val response = client.get("/playout/current")
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
        assertTrue(response.error().retryable)
        assertTrue("held for the armed time, took $elapsedMs ms", elapsedMs >= 350)
    }

    // ---- injectFailure: scope and use ----

    @Test
    fun `a pattern is used up after count requests and the next one goes through`() = stubTest { client ->
        client.arm("503", area = "playout", count = 2)

        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/playout/current").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/playout/current").status)
        assertTrue(client.get("/playout/current").status.value in listOf(200, 204))
    }

    @Test
    fun `a pattern only hits its own area`() = stubTest { client ->
        client.arm("503", area = "ratings", count = 5)

        assertTrue(client.get("/playout/current").status.value in listOf(200, 204))
        assertEquals(HttpStatusCode.OK, client.get("/archive/tracks?q=song").status)
        assertEquals(HttpStatusCode.OK, client.get("/requests?listenerId=listener-seed").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ratings/aggregate?showId=show-1").status)
    }

    @Test
    fun `the area 'all' hits every service`() = stubTest { client ->
        client.arm("503", area = "all", count = 4)

        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/playout/current").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/archive/tracks?q=song").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/requests?listenerId=l").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ratings/aggregate?showId=show-1").status)
    }

    @Test
    fun `patterns are used in the order they were armed`() = stubTest { client ->
        client.arm("503", area = "playout")
        client.arm("401", area = "playout")

        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/playout/current").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/playout/current").status)
    }

    @Test
    fun `an armed failure happens before the handler, so it leaves no trace and a retry with the same key succeeds`() =
        stubTest { client ->
            val before = client.get("/ratings/aggregate?showId=show-1").body<contract.s4feedback.AggregateDto>().playlistRatingCount
            val key = uniqueId("key")
            val listener = uniqueId("listener")
            client.arm("503", area = "ratings")

            assertEquals(HttpStatusCode.ServiceUnavailable, client.rate(key, listener).status)
            assertEquals(before, client.get("/ratings/aggregate?showId=show-1").body<contract.s4feedback.AggregateDto>().playlistRatingCount)

            assertEquals(HttpStatusCode.Created, client.rate(key, listener).status)
            assertEquals(before + 1, client.get("/ratings/aggregate?showId=show-1").body<contract.s4feedback.AggregateDto>().playlistRatingCount)
        }

    @Test
    fun `DELETE disarms everything and the control path is never intercepted`() = stubTest { client ->
        client.arm("503", area = "all", count = 50)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/playout/current").status)

        val response = client.delete("/stub/control")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<ControlResponse>().summary.contains("49"))
        assertTrue(client.get("/playout/current").status.value in listOf(200, 204))
    }

    @Test
    fun `requests to the control path itself are never failed, even with everything armed`() = stubTest { client ->
        client.arm("503", area = "all", count = 50)

        assertEquals(HttpStatusCode.OK, client.arm("401", area = "playout").status)
        assertEquals(HttpStatusCode.OK, client.delete("/stub/control").status)
    }

    // ---- invalid control requests ----

    @Test
    fun `an invalid control request is 400, never an injectable code`() = stubTest { client ->
        val invalid = listOf(
            """{"action":"injectFailure"}""",
            """{"action":"injectFailure","code":"503","count":0}""",
            """{"action":"injectFailure","code":"503","count":1001}""",
            """{"action":"injectFailure","code":"timeout","durationMs":0}""",
            """{"action":"injectFailure","code":"999"}""",
            """{"action":"injectFailure","code":"503","area":"nowhere"}""",
            """{"action":"nonsense"}""",
            """{"action":"titelWechsel"}""",
            """{"action":"changeTrack","trackId":"trk-unknown"}""",
            """{"action":"changeTrack","trackId":"trk-2","talkSegment":true}""",
            "not json",
        )
        for (body in invalid) {
            val response = client.control(body)
            assertEquals(body, HttpStatusCode.BadRequest, response.status)
        }
        assertTrue("nothing got armed by the invalid requests", client.get("/playout/current").status.value in listOf(200, 204))
    }

    // ---- changeTrack ----

    @Test
    fun `changeTrack puts a catalogue track on air with the seeded show and host and extends the history`() = stubTest { client ->
        client.control("""{"action":"changeTrack","trackId":"trk-1"}""")
        val historySize = client.get("/playout/history?limit=1000").body<List<HistoryEntryDto>>().size

        assertEquals(HttpStatusCode.OK, client.control("""{"action":"changeTrack","trackId":"trk-2"}""").status)

        val current = client.get("/playout/current").body<CurrentTrackDto>()
        assertEquals("trk-2", current.trackId)
        assertEquals("Second Song", current.title)
        assertEquals("show-1", current.showId)
        assertEquals("host-1", current.hostId)
        val history = client.get("/playout/history?limit=1000").body<List<HistoryEntryDto>>()
        assertEquals(historySize + 1, history.size)
        assertEquals("trk-2", history.first().trackId)
    }

    @Test
    fun `changeTrack without a track moves on to the next one and wraps around`() = stubTest { client ->
        client.control("""{"action":"changeTrack","trackId":"trk-2"}""")

        client.control("""{"action":"changeTrack"}""")
        assertEquals("trk-3", client.get("/playout/current").body<CurrentTrackDto>().trackId)
        client.control("""{"action":"changeTrack"}""")
        assertEquals("trk-1", client.get("/playout/current").body<CurrentTrackDto>().trackId)
    }

    @Test
    fun `a talk segment leaves the history alone and the next track restores the seeded show and host`() = stubTest { client ->
        client.control("""{"action":"changeTrack","trackId":"trk-1"}""")
        val historySize = client.get("/playout/history?limit=1000").body<List<HistoryEntryDto>>().size

        client.control("""{"action":"changeTrack","talkSegment":true}""")
        assertEquals(HttpStatusCode.NoContent, client.get("/playout/current").status)
        assertEquals(historySize, client.get("/playout/history?limit=1000").body<List<HistoryEntryDto>>().size)

        client.control("""{"action":"changeTrack"}""")
        val back = client.get("/playout/current").body<CurrentTrackDto>()
        assertEquals("trk-1", back.trackId)
        assertEquals("host-1", back.hostId)
    }

    // ---- dropConnection and the event channel ----

    @Test
    fun `dropConnection closes a connected host abnormally so that it reconnects`() = stubTest { client ->
        val session = eventSession(client.hostToken())
        client.awaitRegistered(session)

        val response = client.control("""{"action":"dropConnection"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<ControlResponse>().summary.startsWith("Closed "))
        val reason = withTimeout(5_000) { session.closeReason.await() }
        assertEquals(CloseReason.Codes.INTERNAL_ERROR, reason?.knownReason)
    }

    @Test
    fun `dropConnection with reason token closes like a rejected session`() = stubTest { client ->
        val session = eventSession(client.hostToken())
        client.awaitRegistered(session)

        client.control("""{"action":"dropConnection","reason":"token"}""")

        val reason = withTimeout(5_000) { session.closeReason.await() }
        assertEquals(CloseReason.Codes.VIOLATED_POLICY, reason?.knownReason)
    }

    @Test
    fun `without a connected host dropConnection closes nothing and is not an error`() = stubTest { client ->
        val response = client.control("""{"action":"dropConnection"}""")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `going offline also drops an event channel that is already open, so no live event slips through`() = stubTest { client ->
        val session = eventSession(client.hostToken())
        client.awaitRegistered(session)

        val response = client.arm("offline", area = "all", count = 1000)

        assertTrue(response.body<ControlResponse>().summary.contains("closed"))
        val reason = withTimeout(5_000) { session.closeReason.await() }
        assertEquals(CloseReason.Codes.INTERNAL_ERROR, reason?.knownReason)
    }

    @Test
    fun `offline for another area leaves an open event channel alone`() = stubTest { client ->
        val session = eventSession(client.hostToken())
        client.awaitRegistered(session)

        client.arm("offline", area = "ratings", count = 1000)

        assertEquals(null, kotlinx.coroutines.withTimeoutOrNull(400) { session.closeReason.await() })
        session.close()
    }

    @Test
    fun `an armed 503 on the event channel refuses the handshake`() = stubTest { client ->
        client.arm("503", area = "eventChannel")
        val token = client.hostToken()

        val refused = runCatching { eventSession(token) }

        assertTrue("the upgrade must fail while 503 is armed", refused.isFailure)
        val next = eventSession(token)
        client.awaitRegistered(next)
        next.drain()
    }

    @Test
    fun `an armed 401 on the event channel closes the session like a rejected token, once`() = stubTest { client ->
        client.arm("401", area = "eventChannel")
        val token = client.hostToken()

        val rejected = eventSession(token)
        val reason = withTimeout(5_000) { rejected.closeReason.await() }
        assertEquals(CloseReason.Codes.VIOLATED_POLICY, reason?.knownReason)

        val next = eventSession(token)
        client.awaitRegistered(next)
        assertNotNull(next)
    }

    @Test
    fun `an invalid token does not use up an armed event channel 401`() = stubTest { client ->
        client.arm("401", area = "eventChannel")

        val invalid = eventSession("session-unknown")
        withTimeout(5_000) { invalid.closeReason.await() }

        val valid = eventSession(client.hostToken())
        val reason = withTimeout(5_000) { valid.closeReason.await() }
        assertEquals("the armed 401 was still there for the valid session", CloseReason.Codes.VIOLATED_POLICY, reason?.knownReason)
    }
}
