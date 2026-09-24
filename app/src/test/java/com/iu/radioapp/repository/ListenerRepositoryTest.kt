package com.iu.radioapp.repository

import com.iu.radioapp.data.local.inMemoryUserPreferences
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerRepositoryTest {

    private val repository = ListenerRepository(inMemoryUserPreferences())

    private suspend fun listener() = (repository.getListener() as Outcome.Success).value

    @Test
    fun `listener id is created on first access and stays the same`() = runTest {
        val first = listener()

        assertTrue(first.listenerId.isNotBlank())
        assertEquals(first.listenerId, listener().listenerId)
    }

    @Test
    fun `display name is unset at first`() = runTest {
        assertNull(listener().displayName)
    }

    @Test
    fun `display name can be set and cleared without touching the id`() = runTest {
        val id = listener().listenerId

        repository.setDisplayName("Jo")
        assertEquals("Jo", listener().displayName)
        repository.setDisplayName(null)

        assertNull(listener().displayName)
        assertEquals(id, listener().listenerId)
    }
}
