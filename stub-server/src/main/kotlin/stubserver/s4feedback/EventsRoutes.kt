package stubserver.s4feedback

import contract.common.Endpoints
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import stubserver.control.ErrorArea
import stubserver.control.ErrorCode
import stubserver.control.StubControl
import stubserver.s1playout.PlayoutStore

/** WS /events/ratings - see RatingEventBroadcaster for the distribution itself. */
fun Route.eventsRoutes() {
    webSocket(Endpoints.S4_WS_EVENTS) {
        val token = call.request.queryParameters[Endpoints.PARAM_TOKEN]
        if (token == null || PlayoutStore.hostIdForToken(token) == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid or missing token"))
            return@webSocket
        }

        // Stub control (RAD-17): simulates an expired session on an otherwise valid
        // token. Checked after the real token check so an invalid token never
        // uses up an armed pattern.
        if (StubControl.take(ErrorArea.EVENT_CHANNEL) { it == ErrorCode.HTTP_401 } != null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "session expired (stub control)"))
            return@webSocket
        }

        RatingEventBroadcaster.register(this)
        try {
            // Push-only channel: nothing meaningful arrives from the client,
            // but the loop has to run so the session stays open and this
            // coroutine notices when the connection actually closes.
            for (frame in incoming) {
                // Intentionally ignored.
            }
        } finally {
            RatingEventBroadcaster.unregister(this)
        }
    }
}
