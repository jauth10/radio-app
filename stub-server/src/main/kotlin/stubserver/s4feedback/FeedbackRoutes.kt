package stubserver.s4feedback

import contract.common.Endpoints
import contract.s4feedback.RatingRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import stubserver.common.respondError

@OptIn(ExperimentalTime::class)
fun Route.feedbackRoutes() {

    post(Endpoints.S4_RATINGS) {
        val idempotencyKey = call.request.headers[Endpoints.HEADER_IDEMPOTENCY_KEY]
        if (idempotencyKey.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "${Endpoints.HEADER_IDEMPOTENCY_KEY} header is required",
                retryable = false,
            )
            return@post
        }
        val rating = call.receive<RatingRequest>()
        when (val result = FeedbackStore.submit(idempotencyKey, rating, receivedAt = Clock.System.now())) {
            is FeedbackStore.SubmitResult.Success ->
                call.respond(HttpStatusCode.Created, result.response)
            is FeedbackStore.SubmitResult.Invalid ->
                call.respondError(HttpStatusCode.UnprocessableEntity, result.reason, retryable = false)
            is FeedbackStore.SubmitResult.Conflict ->
                call.respondError(HttpStatusCode.Conflict, result.reason, retryable = false)
        }
    }

    get(Endpoints.S4_AGGREGATE) {
        val showId = call.request.queryParameters[Endpoints.PARAM_SHOW_ID]
        if (showId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "${Endpoints.PARAM_SHOW_ID} is required", retryable = false)
            return@get
        }
        call.respond(FeedbackStore.aggregateFor(showId))
    }

    get(Endpoints.S4_RATINGS_SINCE) {
        val showId = call.request.queryParameters[Endpoints.PARAM_SHOW_ID]
        if (showId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "${Endpoints.PARAM_SHOW_ID} is required", retryable = false)
            return@get
        }
        val rawSince = call.request.queryParameters[Endpoints.PARAM_SINCE]
        if (rawSince.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "${Endpoints.PARAM_SINCE} is required", retryable = false)
            return@get
        }
        val since = runCatching { Instant.parse(rawSince) }.getOrElse {
            call.respondError(
                HttpStatusCode.BadRequest,
                "${Endpoints.PARAM_SINCE} must be an ISO-8601 instant",
                retryable = false,
            )
            return@get
        }
        call.respond(FeedbackStore.since(since, showId))
    }
}
