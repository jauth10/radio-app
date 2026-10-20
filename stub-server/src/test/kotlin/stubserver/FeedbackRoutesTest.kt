package stubserver

import contract.s4feedback.AggregateDto
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingResponse
import contract.s4feedback.RatingTarget
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FeedbackRoutesTest {

    private suspend fun HttpClient.aggregate(showId: String = "show-1") =
        get("/ratings/aggregate?showId=$showId").body<AggregateDto>()

    @Test
    fun `a rating without an idempotency key is 400`() = stubTest { client ->
        val response = client.post("/ratings") {
            contentType(ContentType.Application.Json)
            setBody("""{"target":"PLAYLIST","referenceId":"show-1","value":4,"listenerId":"l","timestamp":"2026-08-28T10:00:00Z"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertFalse(response.error().retryable)
    }

    @Test
    fun `an accepted rating is 201 and counted in the aggregate`() = stubTest { client ->
        val before = client.aggregate().playlistRatingCount

        val response = client.rate(value = 5)

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.body<RatingResponse>().ratingId.startsWith("rat-"))
        assertEquals(before + 1, client.aggregate().playlistRatingCount)
    }

    @Test
    fun `a value outside one to five is 422 and counts for nothing`() = stubTest { client ->
        val before = client.aggregate().playlistRatingCount

        for (value in listOf(0, 6, -1)) {
            val response = client.rate(value = value)

            assertEquals("value $value", HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("Die Bewertung muss zwischen 1 und 5 liegen.", response.error().reason)
            assertFalse(response.error().retryable)
        }
        assertEquals(before, client.aggregate().playlistRatingCount)
    }

    @Test
    fun `sending the same rating again with the same key returns the same answer and counts once`() = stubTest { client ->
        val listener = uniqueId("listener")
        val key = uniqueId("key")
        val before = client.aggregate().playlistRatingCount

        val first = client.rate(key, listener).body<RatingResponse>()
        val replay = client.rate(key, listener)

        assertEquals(HttpStatusCode.Created, replay.status)
        assertEquals(first, replay.body<RatingResponse>())
        assertEquals(before + 1, client.aggregate().playlistRatingCount)
    }

    @Test
    fun `a rejection is the first answer for its key, so a retry gets it again even with a changed body`() = stubTest { client ->
        val listener = uniqueId("listener")
        val key = uniqueId("key")
        val before = client.aggregate().playlistRatingCount

        val first = client.rate(key, listener, value = 6)
        val replay = client.rate(key, listener, value = 4)

        assertEquals(HttpStatusCode.UnprocessableEntity, first.status)
        assertEquals(first.status, replay.status)
        assertEquals(first.error(), replay.error())
        assertEquals(before, client.aggregate().playlistRatingCount)
    }

    @Test
    fun `a conflict is answered identically on a retry with the same key`() = stubTest { client ->
        withinOneHour {
            val listener = uniqueId("listener")
            val key = uniqueId("key")
            assertEquals(HttpStatusCode.Created, client.rate(listenerId = listener).status)

            val first = client.rate(key, listener)
            val replay = client.rate(key, listener)

            assertEquals(HttpStatusCode.Conflict, first.status)
            assertEquals(first.status, replay.status)
            assertEquals(first.error(), replay.error())
        }
    }

    @Test
    fun `a replay is answered even after the same listener has rated again, instead of a conflict`() = stubTest { client ->
        withinOneHour {
            val listener = uniqueId("listener")
            val key = uniqueId("key")
            val first = client.rate(key, listener).body<RatingResponse>()
            assertEquals(HttpStatusCode.Conflict, client.rate(listenerId = listener).status)

            val replay = client.rate(key, listener)

            assertEquals(HttpStatusCode.Created, replay.status)
            assertEquals(first, replay.body<RatingResponse>())
        }
    }

    @Test
    fun `a second rating of the same target in the same hour is 409, any other combination is fine`() = stubTest { client ->
        withinOneHour {
            val listener = uniqueId("listener")
            assertEquals(HttpStatusCode.Created, client.rate(listenerId = listener).status)

            val conflict = client.rate(listenerId = listener)
            assertEquals(HttpStatusCode.Conflict, conflict.status)
            assertFalse(conflict.error().retryable)
            assertTrue(conflict.error().reason.contains("in dieser Stunde"))

            assertEquals(HttpStatusCode.Created, client.rate(listenerId = listener, target = RatingTarget.HOST).status)
            assertEquals(HttpStatusCode.Created, client.rate(listenerId = uniqueId("listener")).status)
        }
    }

    @Test
    fun `a conflicting rating counts for nothing`() = stubTest { client ->
        withinOneHour {
            val listener = uniqueId("listener")
            client.rate(listenerId = listener)
            val before = client.aggregate().playlistRatingCount

            client.rate(listenerId = listener)

            assertEquals(before, client.aggregate().playlistRatingCount)
        }
    }

    @Test
    fun `the aggregate needs a show id and is empty for an unknown show`() = stubTest { client ->
        assertEquals(HttpStatusCode.BadRequest, client.get("/ratings/aggregate").status)

        val unknown = client.aggregate("show-unknown")
        assertEquals(0, unknown.playlistRatingCount)
        assertEquals(0, unknown.hostRatingCount)
    }

    @Test
    fun `the aggregate averages playlist and host ratings separately`() = stubTest { client ->
        val before = client.aggregate()

        client.rate(value = 5, target = RatingTarget.HOST)
        client.rate(value = 1, target = RatingTarget.HOST)

        val after = client.aggregate()
        assertEquals(before.hostRatingCount + 2, after.hostRatingCount)
        assertEquals(before.playlistRatingCount, after.playlistRatingCount)
        val expected = (before.averageHostRating * before.hostRatingCount + 6) / (before.hostRatingCount + 2)
        assertEquals(expected, after.averageHostRating, 0.0001)
    }

    // ---- GET /ratings?since= (polling fallback) ----

    private suspend fun HttpClient.since(token: String?, since: String, showId: String? = "show-1") =
        get("/ratings") {
            token?.let { header("Authorization", "Bearer $it") }
            parameter("since", since)
            showId?.let { parameter("showId", it) }
        }

    @Test
    fun `polling needs a valid host session token`() = stubTest { client ->
        val since = (Clock.System.now() - 10.seconds).toString()

        assertEquals(HttpStatusCode.Unauthorized, client.since(token = null, since = since).status)
        assertEquals(HttpStatusCode.Unauthorized, client.since(token = "session-unknown", since = since).status)
        val wrongScheme = client.get("/ratings") {
            header("Authorization", "Token ${client.hostToken()}")
            parameter("since", since)
            parameter("showId", "show-1")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongScheme.status)
        assertFalse(client.since(token = null, since = since).error().retryable)
    }

    @Test
    fun `polling returns only ratings received after the given instant, with the display name`() = stubTest { client ->
        val token = client.hostToken()
        client.rate(comment = "alt")
        // The system clock is coarser than two quick requests, and "since" is strictly greater-than.
        Thread.sleep(30)
        val cut = Clock.System.now()
        Thread.sleep(30)
        client.rate(comment = "neu", displayName = "Hörer Test")

        val events = client.since(token, cut.toString()).body<List<RatingEventDto>>()

        assertEquals(listOf("neu"), events.map { it.comment })
        assertEquals("Hörer Test", events.single().displayName)
        assertTrue(events.single().serverReceivedAt >= cut)
    }

    @Test
    fun `polling for an unknown show is empty and a very old instant returns the seeded ratings`() = stubTest { client ->
        val token = client.hostToken()

        assertTrue(client.since(token, "2020-01-01T00:00:00Z", showId = "show-unknown").body<List<RatingEventDto>>().isEmpty())
        val all = client.since(token, "2020-01-01T00:00:00Z").body<List<RatingEventDto>>()
        assertTrue(all.any { it.ratingId == "rat-1" })
    }

    @Test
    fun `polling checks the token before the parameters, and then needs both parameters`() = stubTest { client ->
        val token = client.hostToken()

        assertEquals(HttpStatusCode.Unauthorized, client.since(token = null, since = "bad", showId = null).status)
        assertEquals(HttpStatusCode.BadRequest, client.since(token, since = "2026-08-28T10:00:00Z", showId = null).status)
        assertEquals(HttpStatusCode.BadRequest, client.since(token, since = "not-an-instant").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/ratings") {
            header("Authorization", "Bearer $token")
            parameter("showId", "show-1")
        }.status)
    }

    @Test
    fun `every rating gets its own id`() = stubTest { client ->
        val a = client.rate().body<RatingResponse>().ratingId
        val b = client.rate().body<RatingResponse>().ratingId

        assertNotEquals(a, b)
    }
}
