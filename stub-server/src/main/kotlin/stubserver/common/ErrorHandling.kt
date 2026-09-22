package stubserver.common

import contract.common.ErrorDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

/** Uniform error body: [ErrorDto], the same wire format the app's data sources expect. */
suspend fun ApplicationCall.respondError(status: HttpStatusCode, reason: String, retryable: Boolean) {
    respond(status, ErrorDto(reason = reason, retryable = retryable))
}

/**
 * Malformed request bodies (e.g. invalid JSON for host login) surface as
 * [BadRequestException] from ContentNegotiation; a missing/wrong Content-Type
 * header - nothing to convert the body with - is its own
 * [CannotTransformContentToTypeException], not a [BadRequestException] subtype.
 * Without its own handler it falls into the generic 500 below, which would
 * make a client-side bug look like a retryable server fault and send the
 * outbox down five pointless attempts instead of surfacing the real problem.
 * Anything else unhandled is a genuine server fault (500), not one of the
 * deliberately modelled codes.
 */
fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message ?: "malformed request", retryable = false)
        }
        exception<CannotTransformContentToTypeException> { call, cause ->
            call.respondError(
                HttpStatusCode.UnsupportedMediaType,
                cause.message ?: "unsupported media type",
                retryable = false,
            )
        }
        exception<Throwable> { call, cause ->
            call.respondError(HttpStatusCode.InternalServerError, cause.message ?: "internal error", retryable = true)
        }
    }
}
