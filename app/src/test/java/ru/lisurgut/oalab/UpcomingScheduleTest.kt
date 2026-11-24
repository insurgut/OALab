package ru.lisurgut.oalab

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class UpcomingScheduleTest {
    private val base = LocalDate.of(2026, 11, 24) // Tuesday
    private val animeA = Anime(id = "aniliberty:11", title = "Среда")
    private val animeB = Anime(id = "aniliberty:12", title = "Вторник")
    private val animeC = Anime(id = "aniliberty:13", title = "Пятница")

    @Test fun upcomingWeekStartsTomorrowAndEndsExactlySevenDaysLater() {
        val dates = UpcomingSchedule.upcomingDates(base)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 11, 25), dates.first())
        assertEquals(LocalDate.of(2026, 12, 1), dates.last())
        assertEquals(7, dates.distinct().size)
    }

    @Test fun groupsByRealWeekdayAcrossMonthBoundary() {
        val schedule = listOf(
            ScheduledAnime(animeA, dayOfWeek = 3, nextEpisode = 4),
            ScheduledAnime(animeB, dayOfWeek = 2, nextEpisode = 5),
            ScheduledAnime(animeC, dayOfWeek = 5, nextEpisode = 11)
        )
        val days = UpcomingSchedule.byDate(schedule, base)
        assertEquals(listOf(animeA.id), days[0].items.map { it.anime.id })
        assertEquals(listOf(animeC.id), days[2].items.map { it.anime.id })
        assertEquals(listOf(animeB.id), days[6].items.map { it.anime.id })
        assertTrue(days[1].items.isEmpty())
    }

    @Test fun duplicateProviderEventsAreNotRepeated() {
        val item = ScheduledAnime(animeA, 3, 4)
        val days = UpcomingSchedule.byDate(listOf(item, item), base)
        assertEquals(1, days.first().items.size)
    }

    @Test fun invalidWeekdaysNeverGenerateFictitiousEvents() {
        val entries = listOf(
            ScheduledAnime(animeA, 0, 4), ScheduledAnime(animeA, 8, 4),
            ScheduledAnime(animeB, -1, 5)
        )
        assertTrue(UpcomingSchedule.byDate(entries, base).all { it.items.isEmpty() })
    }

    @Test fun dayLabelsAreActualRelativeDates() {
        assertEquals("Завтра", UpcomingSchedule.dayTitle(base.plusDays(1), base))
        assertEquals("Послезавтра", UpcomingSchedule.dayTitle(base.plusDays(2), base))
        assertEquals("Сегодня", UpcomingSchedule.dayTitle(base, base))
    }

    @Test fun scheduleEmptyStillContainsAllSevenDates() {
        val days = UpcomingSchedule.byDate(emptyList(), base)
        assertEquals(7, days.size)
        assertTrue(days.all { it.items.isEmpty() })
    }
}
