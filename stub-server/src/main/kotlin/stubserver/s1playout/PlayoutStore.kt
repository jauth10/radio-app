package stubserver.s1playout

import contract.s1playout.CurrentTrackDto
import contract.s1playout.HistoryEntryDto
import contract.s2archive.TrackDto
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Fixed in-memory data for S1 - Playout & schedule.
 *
 * [currentTrack] is a mutable `var`, not fixed sample data returned as-is:
 * the stub-control endpoint (RAD-17) sets it to null to demonstrate a talk
 * segment (204) or swaps in a track to simulate a title change, so the 204
 * path already needs to be real here even though the seed data starts non-null.
 */
@OptIn(ExperimentalTime::class)
object PlayoutStore {

    private const val SEEDED_SHOW_ID = "show-1"
    private const val SEEDED_HOST_ID = "host-1"
    private const val SEEDED_HOST_NAME = "Alex Host"
    private const val DEFAULT_DURATION_SECONDS = 210

    // Written by the stub-control endpoint while request threads read it.
    @Volatile
    var currentTrack: CurrentTrackDto? = CurrentTrackDto(
        trackId = "trk-1",
        artist = "The Fake Band",
        title = "Sample Song",
        album = "Fake Album",
        coverUrl = null,
        durationSeconds = DEFAULT_DURATION_SECONDS,
        startedAt = Instant.parse("2026-08-28T10:00:00Z"),
        showId = SEEDED_SHOW_ID,
        hostId = SEEDED_HOST_ID,
        hostName = SEEDED_HOST_NAME,
    )
        private set

    @Volatile
    private var historyEntries: List<HistoryEntryDto> = listOf(
        HistoryEntryDto("trk-1", "The Fake Band", "Sample Song", "Fake Album", Instant.parse("2026-08-28T10:00:00Z")),
        HistoryEntryDto("trk-2", "Second Artist", "Second Song", "Some Album", Instant.parse("2026-08-28T09:50:00Z")),
        HistoryEntryDto("trk-3", "Third Artist", "Third Song", null, Instant.parse("2026-08-28T09:40:00Z")),
    )

    /** Newest first. Immutable snapshot: a concurrent title change never alters a list being read. */
    val history: List<HistoryEntryDto> get() = historyEntries

    /**
     * Title change: [track] goes on air now, with the show and host that are
     * currently live (the seeded pairing after a talk segment), and is
     * prepended to the history like the real station would.
     */
    @Synchronized
    fun startTrack(track: TrackDto, startedAt: Instant): CurrentTrackDto {
        val live = currentTrack
        val started = CurrentTrackDto(
            trackId = track.trackId,
            artist = track.artist,
            title = track.title,
            album = track.album,
            coverUrl = track.coverUrl,
            durationSeconds = DEFAULT_DURATION_SECONDS,
            startedAt = startedAt,
            showId = live?.showId ?: SEEDED_SHOW_ID,
            hostId = if (live != null) live.hostId else SEEDED_HOST_ID,
            hostName = if (live != null) live.hostName else SEEDED_HOST_NAME,
        )
        historyEntries = listOf(
            HistoryEntryDto(track.trackId, track.artist, track.title, track.album, startedAt),
        ) + historyEntries
        currentTrack = started
        return started
    }

    /** Talk segment: no track is playing, so GET /playout/current answers 204. History stays as is. */
    @Synchronized
    fun startTalkSegment() {
        currentTrack = null
    }

    /** One entry per code in the fixed moderation code list. */
    data class HostCredential(val hostCode: String, val hostId: String, val hostName: String)

    private val hostCodes: List<HostCredential> = listOf(
        HostCredential(hostCode = "ALEX-2026", hostId = "host-1", hostName = "Alex Host"),
        HostCredential(hostCode = "SAM-2026", hostId = "host-2", hostName = "Sam Host"),
    )

    fun findHost(hostCode: String): HostCredential? = hostCodes.find { it.hostCode == hostCode }

    /**
     * Token -> hostId. RAD-12 issued tokens without keeping them anywhere,
     * which meant nothing could ever validate one - the gap RAD-14's review
     * flagged for GET /ratings and RAD-15's own WS token check both need
     * closed. No expiry is enforced here; the stub only tracks "was this ever
     * issued", matching the rest of this store's fixed, no-real-time-limits style.
     */
    private val issuedSessions = mutableMapOf<String, String>()

    fun issueSessionToken(hostId: String): String {
        val token = "session-${UUID.randomUUID()}"
        issuedSessions[token] = hostId
        return token
    }

    fun hostIdForToken(token: String): String? = issuedSessions[token]

    /**
     * Assumption (not stated in the ticket or the interface table): five failed
     * login attempts locks a device out with 429 until a correct code resets it.
     */
    private const val MAX_LOGIN_ATTEMPTS = 5

    private val failedLoginAttempts = mutableMapOf<String, Int>()

    /** Records one failed attempt and reports whether [deviceId] is now locked out. */
    fun recordFailedLogin(deviceId: String): Boolean {
        val attempts = (failedLoginAttempts[deviceId] ?: 0) + 1
        failedLoginAttempts[deviceId] = attempts
        return attempts >= MAX_LOGIN_ATTEMPTS
    }

    fun resetFailedLogins(deviceId: String) {
        failedLoginAttempts.remove(deviceId)
    }
}
