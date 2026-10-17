package stubserver

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import stubserver.control.StubControl

/**
 * The offline pattern drops the TCP connection, which only the real Netty engine
 * can do - Ktor's in-process test host has no connection to drop - so this one
 * starts the stub on a free port and talks to it over a real socket.
 */
class OfflinePatternTest {

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private lateinit var base: String

    @Before
    fun start() {
        StubControl.clear()
        val port = ServerSocket(0).use { it.localPort }
        base = "http://127.0.0.1:$port"
        // Loopback only: a test has no business opening the stub to the network (or a firewall prompt).
        server = embeddedServer(Netty, port = port, host = "127.0.0.1", module = Application::module).also { it.start(wait = false) }
        client = HttpClient(OkHttp) { expectSuccess = false }
    }

    @After
    fun stop() {
        StubControl.clear()
        client.close()
        server.stop(0, 0)
    }

    private suspend fun arm(area: String) = client.post("$base/stub/control") {
        contentType(ContentType.Application.Json)
        setBody("""{"action":"injectFailure","code":"offline","area":"$area","count":1000}""")
    }

    private suspend fun failsAsNoNetwork(url: String): Long {
        val started = System.nanoTime()
        val result = runCatching { client.get(url) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("expected an I/O error, got ${result.getOrNull()?.status}", result.isFailure)
        return elapsedMs
    }

    @Test
    fun `offline fails the request at once with an I O error, not after a timeout`() = runBlocking {
        arm("all")

        val elapsedMs = failsAsNoNetwork("$base/playout/current")

        assertTrue("took $elapsedMs ms", elapsedMs < 2_000)
    }

    @Test
    fun `offline also hits a connection that is already open and kept alive`() = runBlocking {
        assertEquals(HttpStatusCode.OK, client.get("$base/archive/tracks?q=song").status)
        arm("all")

        failsAsNoNetwork("$base/archive/tracks?q=song")
        Unit
    }

    @Test
    fun `offline only hits its own area and stops with DELETE`() = runBlocking {
        arm("ratings")

        assertEquals(HttpStatusCode.OK, client.get("$base/archive/tracks?q=song").status)
        failsAsNoNetwork("$base/ratings/aggregate?showId=show-1")

        assertEquals(HttpStatusCode.OK, client.delete("$base/stub/control").status)
        assertEquals(HttpStatusCode.OK, client.get("$base/ratings/aggregate?showId=show-1").status)
    }

    @Test
    fun `the control path stays reachable while everything is offline`() = runBlocking {
        arm("all")

        assertEquals(HttpStatusCode.OK, client.delete("$base/stub/control").status)
        assertEquals(HttpStatusCode.OK, client.get("$base/archive/tracks?q=song").status)
    }
}
