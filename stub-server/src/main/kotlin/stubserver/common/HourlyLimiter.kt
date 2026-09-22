package stubserver.common

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val DEFAULT_MAX_PER_HOUR = 3

/**
 * Wall-clock hour bucket for [at] (e.g. 14:00:00-14:59:59 UTC is one bucket).
 *
 * Shared by [HourlyLimiter] (S3 wishes) and the rating conflict key (S4): both
 * need to agree on what "the same hour" means, so this is the one place that
 * decides it. Always call it with the server's own receive time, not a
 * client-supplied timestamp - the request DTOs' `timestamp` is the device's
 * creation time, which for an offline-created write can be hours older than
 * when the stub actually processes it.
 */
@OptIn(ExperimentalTime::class)
fun hourBucket(at: Instant): Long = at.epochSeconds / 3600

/**
 * Per-listener submission limit for wishes (S3): more than [maxPerHour] within
 * the same [hourBucket] is rejected with 422.
 *
 * Ratings (S4) do not use this class - a rating's own conflict key already
 * includes the hour bucket, so a second rating in a later hour is simply a
 * new, distinct submission rather than something a separate limiter needs to
 * count towards a cap.
 */
@OptIn(ExperimentalTime::class)
class HourlyLimiter(private val maxPerHour: Int = DEFAULT_MAX_PER_HOUR) {

    private val countsByListenerAndHour = mutableMapOf<Pair<String, Long>, Int>()

    /** Returns true and counts the submission if the listener is still under the limit. */
    fun tryConsume(listenerId: String, at: Instant): Boolean {
        val key = listenerId to hourBucket(at)
        val count = countsByListenerAndHour.getOrDefault(key, 0)
        if (count >= maxPerHour) return false
        countsByListenerAndHour[key] = count + 1
        return true
    }
}
