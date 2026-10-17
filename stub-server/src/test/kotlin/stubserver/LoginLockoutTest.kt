package stubserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import stubserver.s1playout.PlayoutStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/** The lockout takes its clock as a parameter, so its five minutes can be checked without waiting. */
@OptIn(ExperimentalTime::class)
class LoginLockoutTest {

    private val start = Clock.System.now()

    @Test
    fun `five failures lock the device for exactly the lockout time`() {
        val device = uniqueId("device")
        repeat(4) { assertFalse(PlayoutStore.recordFailedLogin(device, start)) }
        assertTrue(PlayoutStore.recordFailedLogin(device, start))

        assertTrue(PlayoutStore.isLockedOut(device, start))
        assertTrue(PlayoutStore.isLockedOut(device, start + PlayoutStore.LOCKOUT - 1.seconds))
        assertFalse(PlayoutStore.isLockedOut(device, start + PlayoutStore.LOCKOUT + 1.seconds))
    }

    @Test
    fun `the lockout is five minutes`() {
        assertEquals(5.minutes, PlayoutStore.LOCKOUT)
    }

    @Test
    fun `after the lock the attempt count starts again`() {
        val device = uniqueId("device")
        repeat(5) { PlayoutStore.recordFailedLogin(device, start) }
        val later = start + PlayoutStore.LOCKOUT + 1.minutes

        assertFalse(PlayoutStore.isLockedOut(device, later))
        assertFalse(PlayoutStore.recordFailedLogin(device, later))
    }

    @Test
    fun `another device is not locked`() {
        val device = uniqueId("device")
        repeat(5) { PlayoutStore.recordFailedLogin(device, start) }

        assertFalse(PlayoutStore.isLockedOut(uniqueId("other"), start))
    }

    @Test
    fun `a successful login lifts a lock that is still running`() {
        val device = uniqueId("device")
        repeat(5) { PlayoutStore.recordFailedLogin(device, start) }

        PlayoutStore.resetFailedLogins(device)

        assertFalse(PlayoutStore.isLockedOut(device, start))
    }
}
