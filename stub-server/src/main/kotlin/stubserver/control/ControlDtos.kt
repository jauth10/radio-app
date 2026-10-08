package stubserver.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of the stub control endpoint (RAD-17).
 *
 * TEST TOOL, not part of the assumed station landscape: it lives in
 * :stub-server on purpose, not in :contract, so the app can never depend on it.
 * Wire names are English like the rest of the API (the ticket's titelWechsel,
 * fehlerbild and verbindungAbbrechen are changeTrack, injectFailure and dropConnection).
 */
@Serializable
enum class ControlAction {
    @SerialName("changeTrack")
    CHANGE_TRACK,

    @SerialName("injectFailure")
    INJECT_FAILURE,

    @SerialName("dropConnection")
    DROP_CONNECTION,
}

/** The error patterns the ticket asks for. */
@Serializable
enum class ErrorCode {
    @SerialName("503")
    HTTP_503,

    @SerialName("422")
    HTTP_422,

    @SerialName("409")
    HTTP_409,

    @SerialName("401")
    HTTP_401,

    /** Stalls for [ControlRequest.durationMs], then answers 504 - longer than any client timeout. */
    @SerialName("timeout")
    TIMEOUT,
}

/** Which part of the stub an armed error pattern applies to. */
@Serializable
enum class ErrorArea {
    @SerialName("playout")
    PLAYOUT,

    @SerialName("archive")
    ARCHIVE,

    @SerialName("requests")
    REQUESTS,

    @SerialName("ratings")
    RATINGS,

    @SerialName("eventChannel")
    EVENT_CHANNEL,

    @SerialName("all")
    ALL,
}

/** How [ControlAction.DROP_CONNECTION] closes the sessions. */
@Serializable
enum class DropReason {
    /** Abnormal close: the client is expected to reconnect. */
    @SerialName("abnormal")
    ABNORMAL,

    /** VIOLATED_POLICY, as for a rejected token: the client is expected to give up as Unauthorized. */
    @SerialName("token")
    TOKEN,
}

/**
 * One flat request type for all actions (like every other DTO in this project);
 * only the fields of the chosen [action] are read.
 */
@Serializable
data class ControlRequest(
    val action: ControlAction,

    // CHANGE_TRACK: a catalogue track, or the next one in rotation when both are unset.
    val trackId: String? = null,
    val talkSegment: Boolean = false,

    // INJECT_FAILURE: the next [count] matching requests are answered with [code].
    val code: ErrorCode? = null,
    val area: ErrorArea = ErrorArea.ALL,
    val count: Int = 1,
    val durationMs: Long = DEFAULT_TIMEOUT_MS,

    // DROP_CONNECTION
    val reason: DropReason = DropReason.ABNORMAL,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}

@Serializable
data class ControlResponse(
    val summary: String,
)
