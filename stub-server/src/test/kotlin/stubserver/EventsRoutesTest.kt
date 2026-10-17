package stubserver

import contract.s4feedback.AggregateDto
import contract.s4feedback.EventType
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EventsRoutesTest {

    @Test
    fun `a missing or unknown token is closed with VIOLATED_POLICY right after the handshake`() = stubTest { client ->
        for (token in listOf(null, "session-unknown", "")) {
            val session = eventSession(token)

            val reason = withTimeout(5_000) { session.closeReason.await() }

            assertEquals("token '$token'", CloseReason.Codes.VIOLATED_POLICY, reason?.knownReason)
        }
    }

    @Test
    fun `a new rating reaches a connected host as the rating followed by the updated aggregate`() = stubTest { client ->
        val session = eventSession(client.hostToken())
        client.awaitRegistered(session)

        client.rate(value = 5, comment = "live-1", displayName = "Hörer Live")

        val rating = session.nextMessage()
        val aggregate = session.nextMessage()
        assertNotNull(rating)
        assertNotNull(aggregate)
        assertEquals(EventType.NEW_RATING, rating!!.type)
        assertEquals("live-1", rating.rating!!.comment)
        assertEquals("Hörer Live", rating.rating!!.displayName)
        assertEquals(5, rating.rating!!.value)
        assertEquals(EventType.AGGREGATE_UPDATED, aggregate!!.type)
        assertEquals(client.get("/ratings/aggregate?showId=show-1").body<AggregateDto>().playlistRatingCount, aggregate.aggregate!!.playlistRatingCount)
        assertEquals("both messages belong to the same rating", rating.t0, aggregate.t0)
        assertTrue("t0 is the push time, not before the rating was received", rating.t0 >= rating.rating!!.serverReceivedAt)
        session.close()
    }

    @Test
    fun `every connected host gets the event`() = stubTest { client ->
        val first = eventSession(client.hostToken())
        val second = eventSession(client.hostToken())
        client.awaitRegistered(first)
        client.awaitRegistered(second)
        first.drain()
        second.drain()

        client.rate(comment = "for-both")

        assertEquals("for-both", first.nextMessage()?.rating?.comment)
        assertEquals("for-both", second.nextMessage()?.rating?.comment)
        first.close()
        second.close()
    }

    @Test
    fun `a replay, a conflict and an invalid rating send nothing`() = stubTest { client ->
        withinOneHour {
            val session = eventSession(client.hostToken())
            client.awaitRegistered(session)
            val listener = uniqueId("listener")
            val key = uniqueId("key")
            client.rate(key, listener, comment = "once")
            assertEquals("once", session.nextMessage()?.rating?.comment)
            session.nextMessage()

            client.rate(key, listener, comment = "once")
            client.rate(listenerId = listener, comment = "conflict")
            client.rate(value = 9, comment = "invalid")

            assertNull(session.nextMessage(waitMs = 400))
            session.close()
        }
    }

    @Test
    fun `a host that left does not disturb the next rating`() = stubTest { client ->
        val gone = eventSession(client.hostToken())
        client.awaitRegistered(gone)
        gone.close()
        val staying = eventSession(client.hostToken())
        client.awaitRegistered(staying)

        val response = client.rate(comment = "after-leave")

        assertEquals(io.ktor.http.HttpStatusCode.Created, response.status)
        assertEquals("after-leave", staying.nextMessage()?.rating?.comment)
        staying.close()
    }
}
