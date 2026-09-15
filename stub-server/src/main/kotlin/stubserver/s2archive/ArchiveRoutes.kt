package stubserver.s2archive

import contract.common.Endpoints
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import stubserver.common.respondError

private const val DEFAULT_SEARCH_LIMIT = 20

fun Route.archiveRoutes() {

    get(Endpoints.S2_TRACK_SEARCH) {
        val query = call.request.queryParameters[Endpoints.PARAM_Q] ?: ""
        val rawLimit = call.request.queryParameters[Endpoints.PARAM_LIMIT]
        val limit = if (rawLimit == null) {
            DEFAULT_SEARCH_LIMIT
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
        val matches = ArchiveStore.tracks.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
        call.respond(matches.take(limit))
    }

    get(Endpoints.S2_TRACK_DETAIL) {
        val trackId = call.parameters["trackId"]
        if (trackId == null) {
            call.respondError(HttpStatusCode.BadRequest, "trackId is required", retryable = false)
            return@get
        }
        val track = ArchiveStore.tracks.find { it.trackId == trackId }
        if (track == null) {
            call.respondError(HttpStatusCode.NotFound, "unknown trackId: $trackId", retryable = false)
            return@get
        }
        call.respond(track)
    }
}
