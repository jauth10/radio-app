package stubserver

import contract.common.RadioJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import stubserver.common.installErrorHandling
import stubserver.s1playout.playoutRoutes
import stubserver.s2archive.archiveRoutes
import stubserver.s3requests.requestsRoutes
import stubserver.s4feedback.feedbackRoutes

/**
 * Bound to all interfaces so the Android emulator's host alias (10.0.2.2)
 * can reach it without extra configuration; see the startup guide (RAD-21).
 */
private const val PORT = 8080

fun main() {
    embeddedServer(Netty, port = PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(RadioJson)
    }
    install(CallLogging)
    installErrorHandling()

    routing {
        playoutRoutes()
        archiveRoutes()
        requestsRoutes()
        feedbackRoutes()
    }
}
