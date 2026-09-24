package stubserver.s1playout

import contract.common.Endpoints
import contract.s1playout.HostLoginRequest
import contract.s1playout.HostLoginResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import stubserver.common.respondError

private const val DEFAULT_HISTORY_LIMIT = 20

@OptIn(ExperimentalTime::class)
fun Route.playoutRoutes() {

    get(Endpoints.S1_CURRENT) {
        val current = PlayoutStore.currentTrack
        if (current == null) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(current)
        }
    }

    get(Endpoints.S1_HISTORY) {
        val rawLimit = call.request.queryParameters[Endpoints.PARAM_LIMIT]
        val limit = if (rawLimit == null) {
            DEFAULT_HISTORY_LIMIT
        } else {
            rawLimit.toIntOrNull() ?: run {
                call.respondError(HttpStatusCode.BadRequest, "limit must be an integer", retryable = false)
                return@get
            }
        }
        if (limit < 0) {
            call.respondError(HttpStatusCode.BadRequest, "limit must not be negative", retryable = false)
            return@get
        }
        call.respond(PlayoutStore.history.take(limit))
    }

    post(Endpoints.S1_HOST_LOGIN) {
        val request = call.receive<HostLoginRequest>()
        val host = PlayoutStore.findHost(request.hostCode)
        if (host == null) {
            val lockedOut = PlayoutStore.recordFailedLogin(request.deviceId)
            if (lockedOut) {
                call.respondError(HttpStatusCode.TooManyRequests, "too many failed login attempts", retryable = true)
            } else {
                call.respondError(HttpStatusCode.Unauthorized, "invalid host code", retryable = false)
            }
            return@post
        }
        PlayoutStore.resetFailedLogins(request.deviceId)
        call.respond(
            HostLoginResponse(
                sessionToken = PlayoutStore.issueSessionToken(),
                validUntil = Clock.System.now() + 1.hours,
                hostId = host.hostId,
                hostName = host.hostName,
            )
        )
    }
}
