package stubserver.s3requests

import contract.common.Endpoints
import contract.s3requests.CreateSongRequestDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import stubserver.common.respondError

@OptIn(ExperimentalTime::class)
fun Route.requestsRoutes() {

    post(Endpoints.S3_REQUESTS) {
        val idempotencyKey = call.request.headers[Endpoints.HEADER_IDEMPOTENCY_KEY]
        if (idempotencyKey.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "${Endpoints.HEADER_IDEMPOTENCY_KEY} header is required",
                retryable = false,
            )
            return@post
        }
        val request = call.receive<CreateSongRequestDto>()
        when (val result = RequestsStore.submit(idempotencyKey, request, receivedAt = Clock.System.now())) {
            is RequestsStore.SubmitResult.Success ->
                call.respond(HttpStatusCode.Created, result.response)
            is RequestsStore.SubmitResult.Rejected ->
                call.respondError(HttpStatusCode.UnprocessableEntity, result.reason, retryable = false)
        }
    }

    get(Endpoints.S3_REQUEST_DETAIL) {
        val requestId = call.parameters["requestId"]
        if (requestId == null) {
            call.respondError(HttpStatusCode.BadRequest, "requestId is required", retryable = false)
            return@get
        }
        val status = RequestsStore.statusOf(requestId)
        if (status == null) {
            call.respondError(HttpStatusCode.NotFound, "unknown requestId: $requestId", retryable = false)
            return@get
        }
        call.respond(status)
    }

    get(Endpoints.S3_REQUESTS) {
        val listenerId = call.request.queryParameters[Endpoints.PARAM_LISTENER_ID]
        if (listenerId.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "${Endpoints.PARAM_LISTENER_ID} is required",
                retryable = false,
            )
            return@get
        }
        call.respond(RequestsStore.overviewFor(listenerId))
    }
}
