package stubserver.control

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.websocket.CloseReason
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import stubserver.common.respondError
import stubserver.s1playout.PlayoutStore
import stubserver.s2archive.ArchiveStore
import stubserver.s4feedback.RatingEventBroadcaster

/** Stub control path (RAD-17). */
const val CONTROL_PATH = "/stub/control"

private const val MAX_COUNT = 1_000

/**
 * Stub control endpoint (RAD-17): TEST TOOL, expressly not part of the assumed
 * station landscape. Makes the offline and error concept reproducible and
 * demonstrable. Problems with a control request itself are always 400, never one
 * of the injectable codes, so they cannot be mistaken for a simulated error.
 */
@OptIn(ExperimentalTime::class)
fun Route.controlRoutes() {
    route(CONTROL_PATH) {

        post {
            val request = call.receive<ControlRequest>()
            when (request.action) {
                ControlAction.CHANGE_TRACK -> changeTrack(call, request)
                ControlAction.INJECT_FAILURE -> errorPattern(call, request)
                ControlAction.DROP_CONNECTION -> dropConnection(call, request)
            }
        }

        delete {
            val pending = StubControl.clear()
            call.respond(ControlResponse("Disarmed all error patterns ($pending request(s) were still armed)"))
        }
    }
}

@OptIn(ExperimentalTime::class)
private suspend fun changeTrack(call: ApplicationCall, request: ControlRequest) {
    if (request.talkSegment) {
        if (request.trackId != null) {
            call.respondError(HttpStatusCode.BadRequest, "talkSegment and trackId are mutually exclusive", retryable = false)
            return
        }
        PlayoutStore.startTalkSegment()
        call.respond(ControlResponse("Talk segment started: GET /playout/current now answers 204"))
        return
    }

    val catalogue = ArchiveStore.tracks
    val track = if (request.trackId != null) {
        catalogue.find { it.trackId == request.trackId } ?: run {
            call.respondError(HttpStatusCode.BadRequest, "unknown trackId '${request.trackId}'", retryable = false)
            return
        }
    } else {
        val liveIndex = catalogue.indexOfFirst { it.trackId == PlayoutStore.currentTrack?.trackId }
        catalogue[(liveIndex + 1) % catalogue.size]
    }

    val started = PlayoutStore.startTrack(track, startedAt = Clock.System.now())
    call.respond(ControlResponse("Now playing ${started.trackId}: ${started.artist} - ${started.title}"))
}

private suspend fun errorPattern(call: ApplicationCall, request: ControlRequest) {
    val code = request.code
    if (code == null) {
        call.respondError(HttpStatusCode.BadRequest, "code is required for injectFailure", retryable = false)
        return
    }
    if (request.count !in 1..MAX_COUNT) {
        call.respondError(HttpStatusCode.BadRequest, "count must be between 1 and $MAX_COUNT", retryable = false)
        return
    }
    if (request.durationMs <= 0) {
        call.respondError(HttpStatusCode.BadRequest, "durationMs must be positive", retryable = false)
        return
    }
    StubControl.arm(code, request.area, request.count, request.durationMs)
    call.respond(ControlResponse("Armed: next ${request.count} request(s) in area ${request.area} fail with $code"))
}

private suspend fun dropConnection(call: ApplicationCall, request: ControlRequest) {
    val reason = when (request.reason) {
        DropReason.ABNORMAL -> CloseReason(CloseReason.Codes.INTERNAL_ERROR, "connection dropped (stub control)")
        DropReason.TOKEN -> CloseReason(CloseReason.Codes.VIOLATED_POLICY, "session rejected (stub control)")
    }
    val closed = RatingEventBroadcaster.closeAll(reason)
    call.respond(ControlResponse("Closed $closed event channel session(s) with ${reason.knownReason}"))
}
