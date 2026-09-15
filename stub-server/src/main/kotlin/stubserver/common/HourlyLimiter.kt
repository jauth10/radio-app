package stubserver.common

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val DEFAULT_MAX_PER_HOUR = 3

/**
 * Per-listener submission limit for wishes (S3) and ratings (S4), shared by
 * both since the ticket applies the same rule to each.
 *
 * "Broadcast hour" here is a wall-clock hour bucket (e.g. 14:00:00-14:59:59
 * UTC counts as one bucket): the stub server has no show schedule to bucket
 * against, that lives in the app's domain model, not :contract.
 */
@OptIn(ExperimentalTime::class)
class HourlyLimiter(private val maxPerHour: Int = DEFAULT_MAX_PER_HOUR) {

    private val countsByListenerAndHour = mutableMapOf<Pair<String, Long>, Int>()

    /** Returns true and counts the submission if the listener is still under the limit. */
    fun tryConsume(listenerId: String, at: Instant): Boolean {
        val key = listenerId to (at.epochSeconds / 3600)
        val count = countsByListenerAndHour.getOrDefault(key, 0)
        if (count >= maxPerHour) return false
        countsByListenerAndHour[key] = count + 1
        return true
    }
}
