package ru.lisurgut.oalab

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class RecommendationMode { PERSONAL, GENRES, DISCOVER }

/**
 * Six distinct recommendation explanations. All matched references come from
 * real local LibraryEntry data; no imagined viewing history or external AI.
 */
enum class RecommendationKind(val label: String) {
    GENRES("По жанрам"),
    STORY("По сюжету"),
    FAVORITE("На основе любимого"),
    WATCHING("Похоже на текущее"),
    STYLE("По стилю"),
    DISCOVERY("Новое для тебя")
}

data class AnimeRecommendation(
    val anime: Anime,
    val score: Double,
    val reason: String,
    val matchedGenres: List<String>,
    val kind: RecommendationKind = RecommendationKind.DISCOVERY,
    val sourceAnime: Anime? = null
)

object RecommendationEngine {
    private val locale = Locale.ROOT
    private val stopWords = setOf(
        "аниме", "серия", "серии", "история", "главный", "главная",
        "который", "которая", "которые", "только", "после", "время",
        "своего", "своей", "теперь", "когда", "чтобы", "может", "будет",
        "между", "жизнь", "жизни", "герой", "героиня", "аниме",
        "вместе", "часть", "однажды", "мире", "свою", "одна",
        "about", "this", "that", "with", "from", "have", "they",
        "when", "their", "world", "anime", "story", "main", "after"
    )
    private fun normalize(value: String): String = value
        .lowercase(locale).replace('ё', 'е').trim()
        .replace(Regex("\\s+"), " ")

    private fun genreKeys(anime: Anime): Set<String> =
        anime.genres.map(::normalize).filter { it.isNotBlank() }.toSet()

    private fun words(value: String): Set<String> =
        Regex("[\\p{L}]{4,}").findAll(normalize(value))
            .map { it.value }
            .filter { it !in stopWords }
            .take(120).toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0
        else a.intersect(b).size.toDouble() / a.union(b).size

    private fun weight(entry: LibraryEntry): Double = when {
        entry.status == "Брошено" -> -3.0
        entry.favorite && entry.status == "Просмотрено" -> 5.0
        entry.favorite -> 4.2
        entry.status == "Просмотрено" -> 3.5
        entry.status == "Смотрю" -> 3.0
        entry.lastPosition > 0 -> 2.5
        entry.status == "В планах" -> .7
        else -> 0.0
    }

    private data class Evidence(
        val kind: RecommendationKind,
        val strength: Double,
        val reason: String,
        val source: Anime? = null,
        val genres: List<String> = emptyList()
    )
    private data class Seed(
        val entry: LibraryEntry,
        val genres: Set<String>,
        val description: Set<String>
    )
    private data class Scored(
        val anime: Anime,
        val overall: Double,
        val genres: Set<String>,
        val evidence: List<Evidence>
    )

    fun rank(
        candidates: List<Anime>,
        history: List<LibraryEntry>,
        explicitGenres: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        mode: RecommendationMode = RecommendationMode.PERSONAL,
        limit: Int = 35,
        kindFilter: RecommendationKind? = null,
        minRating: Int = 0,
        yearFrom: Int? = null,
        yearTo: Int? = null
    ): List<AnimeRecommendation> {
        if (limit <= 0) return emptyList()

        // Never recommend a title already in the user's library, even if its
        // viewing status is unset. IDs are AniLibria-specific.
        val known = history.map { it.anime.id }.toSet()
        val chosenGenres = explicitGenres.map(::normalize).toSet()
        val pool = candidates.asSequence()
            .filter { it.id.startsWith("aniliberty:") }
            .filter { it.id !in known && it.id !in hidden }
            .filter { it.score >= minRating.coerceIn(0, 100) }
            .filter { yearFrom == null || it.year >= yearFrom }
            .filter { yearTo == null || it.year <= yearTo }
            .filter {
                mode != RecommendationMode.GENRES || chosenGenres.isEmpty() ||
                    genreKeys(it).any { genre -> genre in chosenGenres }
            }
            .distinctBy { it.id }.take(450).toList()
        if (pool.isEmpty()) return emptyList()

        val positive = history.filter {
            it.anime.id.startsWith("aniliberty:") && weight(it) > 0.0
        }.sortedWith(compareByDescending<LibraryEntry> { weight(it) }
            .thenByDescending { it.lastPosition }).take(30)
        val seeds = positive.map { Seed(it, genreKeys(it.anime), words(it.anime.description)) }
        val negative = history.filter { weight(it) < 0.0 }
            .map { genreKeys(it.anime) to (-weight(it)) }
        val freq = pool.flatMap { genreKeys(it) }
            .groupingBy { it }.eachCount()
        fun idf(genre: String): Double =
            1.0 + ln((pool.size + 1.0) / ((freq[genre] ?: 0) + 1.0))

        val taste = mutableMapOf<String, Double>()
        for (seed in seeds) {
            for (g in seed.genres) {
                taste[g] = (taste[g] ?: 0.0) + weight(seed.entry) * idf(g)
            }
        }
        for (g in chosenGenres) {
            taste[g] = (taste[g] ?: 0.0) + 7.0 * idf(g)
        }
        val tasteSum = taste.values.sum().coerceAtLeast(1.0)

        val scored = pool.mapNotNull { anime ->
            val gs = genreKeys(anime)
            val description = words(anime.description)
            val matches = anime.genres.filter { normalize(it) in taste }
                .distinctBy(::normalize)
                .sortedByDescending { taste[normalize(it)] ?: 0.0 }
            val likedAffinity = gs.sumOf { taste[it] ?: 0.0 } / tasteSum
            val unpopular = negative.sumOf { (dislikedGenres, penalty) ->
                jaccard(gs, dislikedGenres) * penalty * .14
            }.coerceAtMost(1.15)

            val evidence = mutableListOf<Evidence>()
            for (seedInfo in seeds) {
                val entry = seedInfo.entry
                val seed = entry.anime
                val seedGenres = seedInfo.genres
                val overlap = gs.intersect(seedGenres)
                val similarity = jaccard(gs, seedGenres)
                val seenWeight = weight(entry) / 5.0
                val matched = anime.genres.filter { normalize(it) in overlap }
                if (matched.isNotEmpty()) {
                    evidence += Evidence(
                        kind = RecommendationKind.GENRES,
                        strength = .60 + similarity * 1.3 + seenWeight * .4,
                        reason = "Предложено по жанрам аниме «" + seed.title + "»: " +
                            matched.take(3).joinToString(", "),
                        source = seed, genres = matched.take(3)
                    )
                    if (entry.favorite) {
                        evidence += Evidence(
                            kind = RecommendationKind.FAVORITE,
                            strength = .8 + similarity * 1.65 + seenWeight * .55,
                            reason = "По любимому аниме «" + seed.title + "»",
                            source = seed, genres = matched.take(3)
                        )
                    }
                    if (entry.status == "Смотрю") {
                        evidence += Evidence(
                            kind = RecommendationKind.WATCHING,
                            strength = .77 + similarity * 1.5 + seenWeight * .42,
                            reason = "Похоже на то, что смотришь: «" + seed.title + "»",
                            source = seed, genres = matched.take(3)
                        )
                    }
                }

                val seedWords = seedInfo.description
                val sharedWords = description.intersect(seedWords)
                if (sharedWords.size >= 2) {
                    val contentScore = sharedWords.size.toDouble() /
                        sqrt(max(1, description.size) * max(1, seedWords.size).toDouble())
                    if (contentScore >= .10) {
                        evidence += Evidence(
                            kind = RecommendationKind.STORY,
                            strength = .85 + min(1.4, contentScore * 4.0) +
                                seenWeight * .35,
                            reason = "Похожая история с «" + seed.title + "»",
                            source = seed, genres = matched.take(2)
                        )
                    }
                }
                val similarFormat = anime.format.isNotBlank() &&
                    seed.format.isNotBlank() &&
                    normalize(anime.format) == normalize(seed.format)
                val similarYear = anime.year in 1960..2100 &&
                    seed.year in 1960..2100 &&
                    abs(anime.year - seed.year) <= 4
                if (similarYear && similarFormat && (similarity > 0.0 || sharedWords.isNotEmpty())) {
                    evidence += Evidence(
                        kind = RecommendationKind.STYLE,
                        strength = .5 + seenWeight * .22 + similarity * .9,
                        reason = "Тот же формат и близкий год, что у «" + seed.title + "»",
                        source = seed, genres = matched.take(2)
                    )
                }
            }

            val chosenMatch = anime.genres.filter { normalize(it) in chosenGenres }
            if (chosenMatch.isNotEmpty()) {
                evidence += Evidence(
                    kind = RecommendationKind.GENRES,
                    strength = 1.6 + chosenMatch.size * .30,
                    reason = "Предложено по выбранным жанрам: " +
                        chosenMatch.take(3).joinToString(", "),
                    genres = chosenMatch.take(3)
                )
            }
            // Discovery is an honest fallback with no invented reference anime.
            val novelty = if (positive.isEmpty()) 1.0
                else 1.0 - (seeds.maxOfOrNull {
                    jaccard(gs, it.genres)
                } ?: 0.0)
            evidence += Evidence(
                kind = RecommendationKind.DISCOVERY,
                strength = .6 + novelty * .9,
                reason = if (positive.isEmpty() && chosenGenres.isEmpty())
                    "Открытие по каталогу AniLibria"
                else "Другой жанр для твоей подборки" +
                    anime.genres.firstOrNull()?.let { ": " + it }.orEmpty()
            )

            val availableEvidence = if (kindFilter == null) evidence
                else evidence.filter { it.kind == kindFilter }
            if (availableEvidence.isEmpty()) return@mapNotNull null

            val rating = if (anime.score > 0) anime.score / 100.0 else .38
            val freshness = ((anime.year - 2000) / 30.0).coerceIn(0.0, 1.0)
            val mainSignal = evidence.filter {
                it.kind != RecommendationKind.DISCOVERY
            }.maxOfOrNull { it.strength } ?: 0.0
            val base = when (mode) {
                RecommendationMode.PERSONAL ->
                    2.15 * likedAffinity + .70 * mainSignal +
                        .27 * rating + .06 * freshness - unpopular
                RecommendationMode.GENRES ->
                    2.7 * likedAffinity + .7 * mainSignal + .17 * rating - unpopular
                RecommendationMode.DISCOVER ->
                    1.65 * novelty + .35 * likedAffinity +
                        .18 * mainSignal + .49 * rating - unpopular
            }
            Scored(anime, base, gs, availableEvidence)
        }

        val remaining = scored.toMutableList()
        val result = mutableListOf<AnimeRecommendation>()
        val selectedGenres = mutableListOf<Set<String>>()
        val reasonCounts = mutableMapOf<RecommendationKind, Int>()
        while (remaining.isNotEmpty() && result.size < limit) {
            fun bestEvidence(item: Scored): Evidence {
                return item.evidence.maxWithOrNull(
                    compareBy<Evidence> {
                        // The first page should display a mix of evidence types,
                        // not only genre matches repeated thirty times.
                        val repeats = (reasonCounts[it.kind] ?: 0).coerceAtMost(6)
                        it.strength - repeats * .22 +
                            if (mode == RecommendationMode.DISCOVER &&
                                it.kind == RecommendationKind.DISCOVERY) .70 else 0.0
                    }.thenBy { it.kind.ordinal * -1 }
                )!!
            }
            val winner = remaining.maxWithOrNull(
                compareBy<Scored> { item ->
                    val redundancy = selectedGenres.maxOfOrNull {
                        jaccard(item.genres, it)
                    } ?: 0.0
                    val evidence = bestEvidence(item)
                    item.overall + evidence.strength * .28 -
                        .52 * redundancy -
                        .11 * (reasonCounts[evidence.kind] ?: 0).coerceAtMost(5)
                }.thenBy { it.anime.id }
            ) ?: break

            val why = bestEvidence(winner)
            result += AnimeRecommendation(
                anime = winner.anime,
                score = winner.overall,
                reason = why.reason,
                matchedGenres = why.genres,
                kind = why.kind,
                sourceAnime = why.source
            )
            selectedGenres += winner.genres
            reasonCounts[why.kind] = (reasonCounts[why.kind] ?: 0) + 1
            remaining.remove(winner)
        }
        return result
    }
}
