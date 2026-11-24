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

    @Test
    fun genreReasonNamesActualWatchedAnimeAndSharedGenre() {
        val seed = anime("201", "Оригинальное фэнтези", listOf("Фэнтези", "Драма"))
        val similar = anime("202", "Продолжение интереса", listOf("Фэнтези"))
        val result = RecommendationEngine.rank(
            listOf(similar), listOf(LibraryEntry(seed, status = "Просмотрено")),
            kindFilter = RecommendationKind.GENRES
        )
        assertEquals(1, result.size)
        assertEquals(RecommendationKind.GENRES, result[0].kind)
        assertEquals(seed.id, result[0].sourceAnime?.id)
        assertTrue(result[0].reason.contains(seed.title))
        assertTrue(result[0].reason.contains("Фэнтези"))
    }

    @Test
    fun favoriteAndWatchingHaveDistinctGroundedReasons() {
        val favorite = anime("203", "Любимый сериал", listOf("Приключения"))
        val watching = anime("204", "Смотрю сейчас", listOf("Детектив"))
        val candidate = anime("205", "Микс", listOf("Приключения", "Детектив"))
        val history = listOf(
            LibraryEntry(favorite, favorite = true, status = "Просмотрено"),
            LibraryEntry(watching, status = "Смотрю")
        )
        val fromFavorite = RecommendationEngine.rank(
            listOf(candidate), history, kindFilter = RecommendationKind.FAVORITE
        ).single()
        val fromWatching = RecommendationEngine.rank(
            listOf(candidate), history, kindFilter = RecommendationKind.WATCHING
        ).single()
        assertEquals(favorite.id, fromFavorite.sourceAnime?.id)
        assertEquals(watching.id, fromWatching.sourceAnime?.id)
        assertEquals(RecommendationKind.FAVORITE, fromFavorite.kind)
        assertEquals(RecommendationKind.WATCHING, fromWatching.kind)
    }

    @Test
    fun plotAndStyleBasisRequireActualEvidence() {
        val seed = anime("206", "Оригинал", listOf("Драма"), year = 2022)
            .copy(description = "Волшебник путешествует сквозь бесконечные миры и разгадывает тайны.")
            .copy(format = "TV")
        val close = anime("207", "Сходный", listOf("Драма"), year = 2023)
            .copy(description = "Юный волшебник разгадывает тайны далёкого мира.")
            .copy(format = "TV")
        val history = listOf(LibraryEntry(seed, status = "Просмотрено"))
        val plot = RecommendationEngine.rank(
            listOf(close), history, kindFilter = RecommendationKind.STORY
        ).single()
        val style = RecommendationEngine.rank(
            listOf(close), history, kindFilter = RecommendationKind.STYLE
        ).single()
        assertEquals(seed.id, plot.sourceAnime?.id)
        assertEquals(seed.id, style.sourceAnime?.id)

        val unrelated = close.copy(
            id = "aniliberty:208", description = "", genres = emptyList(),
            year = 1990, format = "MOVIE"
        )
        assertTrue(
            RecommendationEngine.rank(
                listOf(unrelated), history,
                kindFilter = RecommendationKind.STORY
            ).isEmpty()
        )
        assertTrue(
            RecommendationEngine.rank(
                listOf(unrelated), history,
                kindFilter = RecommendationKind.STYLE
            ).isEmpty()
        )
    }

    @Test
    fun discoveryDoesNotFabricateWatchedAnime() {
        val fresh = anime("209", "Новое", listOf("Экшен"), score = 88)
        val found = RecommendationEngine.rank(
            listOf(fresh), emptyList(),
            kindFilter = RecommendationKind.DISCOVERY
        ).single()
        assertNull(found.sourceAnime)
        assertEquals(RecommendationKind.DISCOVERY, found.kind)
    }

    @Test
    fun ratingAndYearFiltersAreAppliedBeforeRanking() {
        val old = anime("210", "Старое", listOf("Комедия"), score = 90, year = 2002)
        val poor = anime("211", "Низкая оценка", listOf("Комедия"),
            score = 54, year = 2025)
        val good = anime("212", "Подходит", listOf("Комедия"),
            score = 81, year = 2024)
        val ranked = RecommendationEngine.rank(
            listOf(old, poor, good), emptyList(),
            minRating = 80, yearFrom = 2020
        )
        assertEquals(listOf(good.id), ranked.map { it.anime.id })
    }
}
