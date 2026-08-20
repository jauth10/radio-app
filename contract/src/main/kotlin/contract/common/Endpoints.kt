package contract.common

/**
 * All endpoint paths and header names in one place.
 * App client and stub server reference the same constants so they cannot
 * drift apart on differing spellings.
 */
object Endpoints {

    // --- S1 Playout ---
    const val S1_CURRENT = "/playout/current"
    const val S1_HISTORY = "/playout/history"          // ?limit=
    const val S1_HOST_LOGIN = "/playout/host/login"     // POST

    // --- S2 Archive ---
    const val S2_TRACK_SEARCH = "/archive/tracks"               // ?q=&limit=
    const val S2_TRACK_DETAIL = "/archive/tracks/{trackId}"

    // --- S3 Requests ---
    const val S3_REQUESTS = "/requests"                 // POST + GET ?listenerId=
    const val S3_REQUEST_DETAIL = "/requests/{requestId}"

    // --- S4 Feedback ---
    const val S4_RATINGS = "/ratings"                           // POST
    const val S4_AGGREGATE = "/ratings/aggregate"                // ?showId=
    const val S4_RATINGS_SINCE = "/ratings"                     // GET ?since=&showId=  (polling fallback)
    const val S4_WS_EVENTS = "/events/ratings"                  // WebSocket

    // --- Query parameter names ---
    const val PARAM_Q = "q"
    const val PARAM_LIMIT = "limit"
    const val PARAM_LISTENER_ID = "listenerId"
    const val PARAM_SHOW_ID = "showId"
    const val PARAM_SINCE = "since"
    const val PARAM_TOKEN = "token"   // WebSocket: token as query parameter

    // --- Headers ---
    const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"   // only POST /requests + POST /ratings
    const val HEADER_AUTH = "Authorization"                 // "Bearer {sessionToken}"
}
