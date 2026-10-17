package stubserver

import contract.s2archive.TrackDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRoutesTest {

    private suspend fun HttpClient.search(query: String) = get("/archive/tracks$query")

    @Test
    fun `search finds a track by title or by artist, ignoring case`() = stubTest { client ->
        assertEquals(listOf("trk-1"), client.search("?q=sample").body<List<TrackDto>>().map { it.trackId })
        assertEquals(listOf("trk-2"), client.search("?q=SECOND%20ARTIST").body<List<TrackDto>>().map { it.trackId })
        assertEquals(3, client.search("?q=song").body<List<TrackDto>>().size)
    }

    @Test
    fun `search without a match is an empty list, not an error`() = stubTest { client ->
        val response = client.search("?q=zzzz")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<List<TrackDto>>().isEmpty())
    }

    @Test
    fun `search honours the limit`() = stubTest { client ->
        assertEquals(2, client.search("?q=song&limit=2").body<List<TrackDto>>().size)
        assertEquals(0, client.search("?q=song&limit=0").body<List<TrackDto>>().size)
    }

    @Test
    fun `a search term below two characters is refused with a reason for the listener`() = stubTest { client ->
        for (query in listOf("?q=a", "?q=", "", "?q=%20%20", "?q=%20a%20")) {
            val response = client.search(query)

            assertEquals(query, HttpStatusCode.BadRequest, response.status)
            val error = response.error()
            assertTrue(error.reason, error.reason.contains("mindestens 2 Zeichen"))
            assertFalse(error.retryable)
        }
    }

    @Test
    fun `a negative or non numeric limit is 400`() = stubTest { client ->
        assertEquals(HttpStatusCode.BadRequest, client.search("?q=song&limit=-1").status)
        assertEquals(HttpStatusCode.BadRequest, client.search("?q=song&limit=abc").status)
    }

    @Test
    fun `track detail carries the broadcastable flag`() = stubTest { client ->
        assertEquals(true, client.get("/archive/tracks/trk-1").body<TrackDto>().broadcastable)
        assertEquals(false, client.get("/archive/tracks/trk-3").body<TrackDto>().broadcastable)
    }

    @Test
    fun `an unknown track is 404 and not retryable`() = stubTest { client ->
        val response = client.get("/archive/tracks/trk-unknown")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Diesen Titel gibt es im Archiv nicht.", response.error().reason)
        assertFalse(response.error().retryable)
    }

    @Test
    fun `surrounding blanks do not count and do not change the hits`() = stubTest { client ->
        assertEquals(listOf("trk-1"), client.search("?q=%20sample%20").body<List<TrackDto>>().map { it.trackId })
    }
}
