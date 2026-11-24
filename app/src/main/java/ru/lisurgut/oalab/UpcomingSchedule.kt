package ru.lisurgut.oalab

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Planned weekly AniLibria airing slots, NOT guaranteed release timestamps.
 * The provider publishes weekly weekday and next episode number, not a time.
 */
data class ScheduledAnime(
    val anime: Anime,
    val dayOfWeek: Int,
    val nextEpisode: Int?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("anime", anime.toJson())
        put("dayOfWeek", dayOfWeek)
        if (nextEpisode != null) put("nextEpisode", nextEpisode)
    }

    companion object {
        fun fromJson(json: JSONObject): ScheduledAnime? {
            val day = json.optInt("dayOfWeek")
            val anime = json.optJSONObject("anime")?.let(Anime::fromJson) ?: return null
            if (day !in 1..7 || !anime.id.startsWith("aniliberty:")) return null
            val ep = json.optInt("nextEpisode").takeIf { it > 0 }
            return ScheduledAnime(anime, day, ep)
        }
    }
}

data class ScheduledDay(val date: LocalDate, val items: List<ScheduledAnime>)

object UpcomingSchedule {
    private val displayLocale = Locale.forLanguageTag("ru-RU")

    /** Tomorrow and six more calendar days; follows the user's device timezone. */
    fun upcomingDates(start: LocalDate = LocalDate.now(ZoneId.systemDefault())): List<LocalDate> =
        (1L..7L).map(start::plusDays)

    fun byDate(
        schedule: List<ScheduledAnime>,
        start: LocalDate = LocalDate.now(ZoneId.systemDefault())
    ): List<ScheduledDay> {
        val unique = schedule
            .filter { it.dayOfWeek in 1..7 }
            .distinctBy { it.dayOfWeek to it.anime.id }
        return upcomingDates(start).map { date ->
            ScheduledDay(
                date = date,
                items = unique.filter { it.dayOfWeek == date.dayOfWeek.value }
                    .sortedWith(
                        compareByDescending<ScheduledAnime> { it.anime.score }
                            .thenBy { it.anime.title }
                    )
            )
        }
    }

    fun dayTitle(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val difference = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        return when (difference) {
            0L -> "Сегодня"
            1L -> "Завтра"
            2L -> "Послезавтра"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE", displayLocale))
                .replaceFirstChar { it.titlecase(displayLocale) }
        }
    }

    fun displayDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", displayLocale))

    fun shortDay(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EE", displayLocale))
            .replace(".", "")
            .uppercase(displayLocale)
}
