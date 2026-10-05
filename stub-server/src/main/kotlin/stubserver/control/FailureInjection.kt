package stubserver.control

import contract.common.Endpoints
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import kotlinx.coroutines.delay
import stubserver.common.respondError

/**
 * Answers armed requests (see [StubControl]) with the armed error pattern
 * instead of handing them to the real route, so a failed request never has side
 * effects and a retry with the same Idempotency-Key simply succeeds.
 *
 * The control path itself is never intercepted - otherwise an armed 'all'
 * pattern could lock out the very request that disarms it. On the event channel
 * a 401 is not an HTTP answer but a VIOLATED_POLICY close after the handshake,
 * exactly like a rejected token (see EventsRoutes); that one is taken there.
 */
fun Application.installFailureInjection() {
    intercept(ApplicationCallPipeline.Plugins) {
        val area = areaOf(call.request.path()) ?: return@intercept
        val failure = StubControl.take(area) { code ->
            !(area == ErrorArea.EVENT_CHANNEL && code == ErrorCode.HTTP_401)
        } ?: return@intercept

        when (failure.code) {
            ErrorCode.HTTP_503 -> call.respondError(
                HttpStatusCode.ServiceUnavailable,
                "Simulierter Serverfehler (Stub-Steuerung)",
                retryable = true,
            )
            ErrorCode.HTTP_422 -> call.respondError(
                HttpStatusCode.UnprocessableEntity,
                "Simulierte Ablehnung (Stub-Steuerung)",
                retryable = false,
            )
            ErrorCode.HTTP_409 -> call.respondError(
                HttpStatusCode.Conflict,
                "Simulierter Konflikt (Stub-Steuerung)",
                retryable = false,
            )
            ErrorCode.HTTP_401 -> call.respondError(
                HttpStatusCode.Unauthorized,
                "Simulierte abgelaufene Sitzung (Stub-Steuerung)",
                retryable = false,
            )
            ErrorCode.TIMEOUT -> {
                delay(failure.durationMs)
                call.respondError(
                    HttpStatusCode.GatewayTimeout,
                    "Simulierte Zeitüberschreitung (Stub-Steuerung)",
                    retryable = true,
                )
            }
        }
        finish()
    }
}

private fun areaOf(path: String): ErrorArea? = when {
    path == Endpoints.S4_WS_EVENTS -> ErrorArea.EVENT_CHANNEL
    path.isUnder("/playout") -> ErrorArea.PLAYOUT
    path.isUnder("/archive") -> ErrorArea.ARCHIVE
    path.isUnder("/requests") -> ErrorArea.REQUESTS
    path.isUnder("/ratings") -> ErrorArea.RATINGS
    else -> null
}

private fun String.isUnder(prefix: String) = this == prefix || startsWith("$prefix/")
