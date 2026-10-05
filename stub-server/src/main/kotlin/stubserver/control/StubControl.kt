package stubserver.control

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Error patterns armed through the stub control endpoint (RAD-17).
 *
 * Request threads consume while the control endpoint arms, so the counters are
 * atomic and the queue concurrent. Patterns are consumed in the order they were
 * armed; a pattern with [ErrorArea.ALL] matches every area.
 */
object StubControl {

    /** What a consumed pattern asks the caller to do. */
    data class InjectedFailure(val code: ErrorCode, val durationMs: Long)

    private class Armed(
        val code: ErrorCode,
        val area: ErrorArea,
        val durationMs: Long,
        count: Int,
    ) {
        val remaining = AtomicInteger(count)

        fun tryTake(): Boolean {
            while (true) {
                val current = remaining.get()
                if (current <= 0) return false
                if (remaining.compareAndSet(current, current - 1)) return true
            }
        }
    }

    private val armed = ConcurrentLinkedQueue<Armed>()

    fun arm(code: ErrorCode, area: ErrorArea, count: Int, durationMs: Long) {
        armed.removeIf { it.remaining.get() <= 0 }
        armed.add(Armed(code, area, durationMs, count))
    }

    /** Disarms everything and returns how many requests were still going to be failed. */
    fun clear(): Int {
        val pending = armed.sumOf { it.remaining.get().coerceAtLeast(0) }
        armed.clear()
        return pending
    }

    /**
     * Takes one failure for a request in [target], if a pattern is armed for it
     * and its code passes [accepts]. Returns null when the request should go
     * through normally.
     */
    fun take(target: ErrorArea, accepts: (ErrorCode) -> Boolean = { true }): InjectedFailure? {
        for (pattern in armed) {
            if (pattern.area != ErrorArea.ALL && pattern.area != target) continue
            if (!accepts(pattern.code)) continue
            if (pattern.tryTake()) return InjectedFailure(pattern.code, pattern.durationMs)
        }
        return null
    }
}
