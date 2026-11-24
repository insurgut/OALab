package ru.lisurgut.oalab

import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {
    private fun anime(
        id: String,
        name: String,
        genres: List<String>,
        score: Int = 70,
        year: Int = 2022
    ): Anime = Anime(
        id = "aniliberty:" + id,
        title = name,
        score = score,
        year = year,
        genres = genres
    )

    @Test
    fun favoritesAndWatchedGenresInfluenceRanking() {
        val watched = anime("1", "Любимая история", listOf("Фэнтези", "Приключения"))
        val fantasy = anime("2", "Фэнтези", listOf("Фэнтези"), 68)
        val romance = anime("3", "Романтика", listOf("Романтика"), 99)
        val ranked = RecommendationEngine.rank(
            candidates = listOf(romance, fantasy, watched),
            history = listOf(LibraryEntry(watched, status = "Просмотрено", favorite = true))
        )
        assertEquals("aniliberty:2", ranked.first().anime.id)
        assertFalse(ranked.any { it.anime.id == watched.id })
        assertTrue(ranked.first().reason.contains("Фэнтези") ||
            ranked.first().reason.contains("Любимая история"))
    }

    @Test
    fun explicitGenrePreferenceWorksWithoutWatchHistory() {
        val action = anime("10", "Боевик", listOf("Боевик"), 98)
        val detective = anime("11", "Детектив", listOf("Детектив"), 50)
        val ranked = RecommendationEngine.rank(
            candidates = listOf(action, detective),
            history = emptyList(),
            explicitGenres = setOf("Детектив"),
            mode = RecommendationMode.GENRES
        )
        assertEquals(detective.id, ranked.first().anime.id)
    }

    @Test
    fun removesHiddenWatchedAndNonProviderEntries() {
        val hidden = anime("4", "Скрытое", listOf("Драма"))
        val watched = anime("5", "Просмотрено", listOf("Драма"))
        val available = anime("6", "Новое", listOf("Драма"))
        val external = Anime(id = "external:7", title = "Other provider")
        val ranked = RecommendationEngine.rank(
            listOf(hidden, watched, available, available, external),
            history = listOf(LibraryEntry(watched, status = "Смотрю")),
            hidden = setOf(hidden.id)
        )
        assertEquals(listOf(available.id), ranked.map { it.anime.id })
    }

    @Test
    fun coldStartStillShowsRealTitlesWithoutMakingUpRatings() {
        val low = anime("8", "Аниме 8", emptyList(), score = 20)
        val high = anime("9", "Аниме 9", emptyList(), score = 95)
        val ranked = RecommendationEngine.rank(listOf(low, high), emptyList())
        assertEquals(high.id, ranked.first().anime.id)
        assertEquals(2, ranked.size)
    }

    @Test
    fun supportsMixedHistoryAndUserFeedback() {
        val liked = anime("12", "Любимое", listOf("Комедия"))
        val dropped = anime("13", "Брошено", listOf("Ужасы"))
        val funny = anime("14", "Весёлое", listOf("Комедия"), 70)
        val scary = anime("15", "Ужасы", listOf("Ужасы"), 78)
        val ranked = RecommendationEngine.rank(
            listOf(funny, scary),
            listOf(
                LibraryEntry(liked, favorite = true),
                LibraryEntry(dropped, status = "Брошено")
            )
        )
        assertEquals(funny.id, ranked.first().anime.id)
    }

    @Test
    fun capsResultsAndHandlesBadCandidates() {
        val list = (1..100).map {
            anime(it.toString(), "Аниме " + it, listOf("Драма"))
        }
        assertEquals(12, RecommendationEngine.rank(list, emptyList(), limit = 12).size)
        assertTrue(RecommendationEngine.rank(list, emptyList(), limit = 0).isEmpty())
        assertTrue(RecommendationEngine.rank(emptyList(), emptyList()).isEmpty())
    }
}
