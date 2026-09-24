package com.iu.radioapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTest {

    private val fromPlayout = Track("trk-1", "A", "T", album = null, coverUrl = null, durationSeconds = 210, broadcastable = null)
    private val fromArchive = Track("trk-1", "A", "T", album = "Album", coverUrl = "cover", durationSeconds = null, broadcastable = true)

    @Test
    fun `completedWith fills only the unknown fields`() {
        assertEquals(Track("trk-1", "A", "T", "Album", "cover", 210, true), fromPlayout.completedWith(fromArchive))
    }

    @Test
    fun `completedWith keeps values that are already known`() {
        assertEquals("Album", fromArchive.completedWith(fromPlayout.copy(album = "Other")).album)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `completedWith refuses a different track`() {
        fromPlayout.completedWith(fromArchive.copy(trackId = "trk-2"))
    }
}
