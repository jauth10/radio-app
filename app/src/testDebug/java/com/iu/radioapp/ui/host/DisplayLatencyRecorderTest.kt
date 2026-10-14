package com.iu.radioapp.ui.host

import com.iu.radioapp.domain.FeedSource
import com.iu.radioapp.domain.RatingEvent
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.ReceivedRating
import com.iu.radioapp.repository.MutableClock
import com.iu.radioapp.repository.TEST_NOW
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayLatencyRecorderTest {

    private val lines = mutableListOf<String>()

    private fun TestScope.recorder() = DisplayLatencyRecorder(MutableClock(scheduler = testScheduler)) { lines += it }

    private fun received(id: String, source: FeedSource, serverReceivedAt: Instant = TEST_NOW) =
        ReceivedRating(RatingEvent(id, RatingTarget.PLAYLIST, 4, null, serverReceivedAt, null), source)

    private fun DisplayLatencyRecorder.show(rating: ReceivedRating) {
        onListed(listOf(rating))
        onDisplayed(rating)
    }

    @Test
    fun `delta is the time from server receipt to the first drawing`() = runTest {
        val recorder = recorder()
        val rating = received("rat-1", FeedSource.EVENT_CHANNEL)

        advanceTimeBy(1_500)
        recorder.show(rating)

        val displayedAt = TEST_NOW.toEpochMilliseconds() + 1_500
        assertEquals(listOf("rat-1,EVENT_CHANNEL,${TEST_NOW.toEpochMilliseconds()},$displayedAt,1500,true"), lines)
    }

    @Test
    fun `a rating is recorded once however often it is drawn or listed again`() = runTest {
        val recorder = recorder()
        val rating = received("rat-1", FeedSource.EVENT_CHANNEL)

        recorder.show(rating)
        repeat(3) { recorder.onDisplayed(rating) }
        advanceTimeBy(5_000)
        recorder.show(rating)

        assertEquals(1, lines.size)
    }

    @Test
    fun `catch-up ratings are not recorded`() = runTest {
        val recorder = recorder()

        recorder.show(received("rat-old", FeedSource.CATCH_UP))

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `event channel and polling stay apart by their path`() = runTest {
        val recorder = recorder()

        recorder.show(received("rat-1", FeedSource.EVENT_CHANNEL))
        recorder.show(received("rat-2", FeedSource.POLLING))

        assertEquals(listOf("EVENT_CHANNEL", "POLLING"), lines.map { it.split(",")[1] })
    }

    @Test
    fun `nothing is written before the rating is drawn`() = runTest {
        val recorder = recorder()

        recorder.onListed(listOf(received("rat-1", FeedSource.EVENT_CHANNEL)))

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `a rating drawn long after it was listed is marked as not visible on arrival`() = runTest {
        val recorder = recorder()
        val rating = received("rat-1", FeedSource.POLLING, serverReceivedAt = TEST_NOW - 4.seconds)

        recorder.onListed(listOf(rating))
        advanceTimeBy(3_000)
        recorder.onDisplayed(rating)

        assertEquals(listOf("7000", "false"), lines.single().split(",").takeLast(2))
    }

    @Test
    fun `a rating drawn within the tolerance counts as visible on arrival`() = runTest {
        val recorder = recorder()
        val rating = received("rat-1", FeedSource.EVENT_CHANNEL)

        recorder.onListed(listOf(rating))
        advanceTimeBy(DisplayLatencyRecorder.ARRIVAL_TOLERANCE.inWholeMilliseconds)
        recorder.onDisplayed(rating)

        assertEquals("true", lines.single().split(",").last())
    }
}
