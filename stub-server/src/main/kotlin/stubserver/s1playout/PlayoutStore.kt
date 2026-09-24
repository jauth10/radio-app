package stubserver.s1playout

import contract.s1playout.CurrentTrackDto
import contract.s1playout.HistoryEntryDto
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

    var currentTrack: CurrentTrackDto? = CurrentTrackDto(
        trackId = "trk-1",
        artist = "The Fake Band",
        title = "Sample Song",
        album = "Fake Album",
        coverUrl = null,
        durationSeconds = 210,
        startedAt = Instant.parse("2026-08-28T10:00:00Z"),
        showId = "show-1",
        hostId = "host-1",
        hostName = "Alex Host",
    )

    val history: List<HistoryEntryDto> = listOf(
        HistoryEntryDto("trk-1", "The Fake Band", "Sample Song", "Fake Album", Instant.parse("2026-08-28T10:00:00Z")),
        HistoryEntryDto("trk-2", "Second Artist", "Second Song", "Some Album", Instant.parse("2026-08-28T09:50:00Z")),
        HistoryEntryDto("trk-3", "Third Artist", "Third Song", null, Instant.parse("2026-08-28T09:40:00Z")),
    )

    /** One entry per code in the fixed moderation code list. */
    data class HostCredential(val hostCode: String, val hostId: String, val hostName: String)

    private val hostCodes: List<HostCredential> = listOf(
        HostCredential(hostCode = "ALEX-2026", hostId = "host-1", hostName = "Alex Host"),
        HostCredential(hostCode = "SAM-2026", hostId = "host-2", hostName = "Sam Host"),
    )

    fun findHost(hostCode: String): HostCredential? = hostCodes.find { it.hostCode == hostCode }

    fun issueSessionToken(): String = "session-${UUID.randomUUID()}"

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
