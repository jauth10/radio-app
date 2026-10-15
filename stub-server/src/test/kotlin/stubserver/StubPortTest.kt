package stubserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StubPortTest {

    @Test
    fun `without the variable the stub uses 8080`() {
        assertEquals(8080, stubPort(null))
        assertEquals(8080, stubPort(""))
        assertEquals(8080, stubPort("  "))
    }

    @Test
    fun `a valid port is taken, surrounding blanks are ignored`() {
        assertEquals(9090, stubPort("9090"))
        assertEquals(9090, stubPort(" 9090 "))
    }

    @Test
    fun `an unusable value fails loudly instead of falling back to the busy default`() {
        for (value in listOf("9O90", "90 90", "0", "65536", "-1")) {
            val error = runCatching { stubPort(value) }.exceptionOrNull()
            assertTrue("'$value' must be rejected", error is IllegalArgumentException)
            assertTrue(error!!.message!!.contains("STUB_PORT"))
        }
    }
}
