package stubserver

import contract.common.RadioJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import stubserver.common.installErrorHandling
import stubserver.control.CONTROL_PATH
import stubserver.control.controlRoutes
import stubserver.control.installFailureInjection
import stubserver.s1playout.playoutRoutes
import stubserver.s2archive.archiveRoutes
import stubserver.s3requests.requestsRoutes
import stubserver.s4feedback.eventsRoutes
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
    install(WebSockets)
    installErrorHandling()
    installFailureInjection()

    log.info("TEST TOOL active: $CONTROL_PATH is not part of the station landscape, it only makes errors reproducible")

    routing {
        playoutRoutes()
        archiveRoutes()
        requestsRoutes()
        feedbackRoutes()
        eventsRoutes()
        controlRoutes()
    }
}
