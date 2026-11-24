package ru.lisurgut.oalab

import org.junit.Assert.*
import org.junit.Test

class EpisodeTimelineTest {
    private val samples = listOf(60_000L, 120_000L, 30_000L)

    @Test
    fun summedDurationAndEpisodeBoundaries() {
        assertEquals(210_000L, EpisodeTimeline.totalDuration(samples)!!)
        assertEquals(EpisodeSeek(0, 0L), EpisodeTimeline.locate(samples, -10L))
        assertEquals(EpisodeSeek(0, 59_999L), EpisodeTimeline.locate(samples, 59_999L))
        assertEquals(EpisodeSeek(1, 0L), EpisodeTimeline.locate(samples, 60_000L))
        assertEquals(EpisodeSeek(2, 0L), EpisodeTimeline.locate(samples, 180_000L))
        assertEquals(EpisodeSeek(2, 30_000L), EpisodeTimeline.locate(samples, 999_999L))
    }

    @Test
    fun globalProgressPreservesLocalEpisodePosition() {
        assertEquals(65_000L, EpisodeTimeline.globalPosition(samples, 1, 5_000L)!!)
        assertEquals(EpisodeSeek(1, 5_000L), EpisodeTimeline.locate(samples, 65_000L))
        assertEquals(210_000L, EpisodeTimeline.globalPosition(samples, 2, 30_000L)!!)
        assertNull(EpisodeTimeline.globalPosition(samples, 3, 2_000L))
    }

    @Test
    fun missingDurationsDoNotCreateFakeCombinedTimeline() {
        assertNull(EpisodeTimeline.totalDuration(listOf(0L, 120_000L)))
        assertNull(EpisodeTimeline.locate(listOf(60_000L, 0L), 5_000L))
        assertTrue(EpisodeTimeline.boundaryFractions(listOf(0L, 2L)).isEmpty())
        assertNull(EpisodeTimeline.totalDuration(emptyList()))
    }

    @Test
    fun chapterMarkersAreFractionalDurations() {
        val marks = EpisodeTimeline.boundaryFractions(samples)
        assertEquals(2, marks.size)
        assertEquals(60_000f / 210_000f, marks[0], 0.0001f)
        assertEquals(180_000f / 210_000f, marks[1], 0.0001f)
    }

    @Test
    fun screenshotSamplingIsTenDistinctEvenlySpacedTimes() {
        val times = EpisodeTimeline.frameTimes(100_000L)
        assertEquals(10, times.size)
        assertEquals(5_000L, times[0])
        assertEquals(95_000L, times[9])
        assertEquals(10, times.distinct().size)
        assertEquals(10_000L, times[1] - times[0])
        assertTrue(EpisodeTimeline.frameTimes(0).isEmpty())
    }

    @Test
    fun skipWindowStartInclusiveEndExclusive() {
        val range = SkipWindow(12_000L, 101_000L)
        assertFalse(range.contains(11_999L))
        assertTrue(range.contains(12_000L))
        assertTrue(range.contains(100_999L))
        assertFalse(range.contains(101_000L))
    }
}
