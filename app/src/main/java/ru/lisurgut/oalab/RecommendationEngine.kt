package ru.lisurgut.oalab

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

enum class RecommendationMode { PERSONAL, GENRES, DISCOVER }

data class AnimeRecommendation(
    val anime: Anime,
    val score: Double,
    val reason: String,
    val matchedGenres: List<String>
)

/**
 * Private, explainable, on-device content-based recommender.
 * Only AniLibria titles are allowed, and neither watch history nor preferences
 * are sent to a recommendation server.
 *
 * Signals: favorites, finished/watching/planned, real watch progress, dislikes,
 * explicit genres, rare-genre weighting, descriptions, year, catalog rating,
 * plus a diversity penalty so adjacent cards aren't near-identical.
 */
object RecommendationEngine {
    private val locale = Locale.ROOT
    private val commonWords = setOf(
        "аниме", "серия", "серии", "история", "главный", "главная", "очень",
        "который", "которая", "которые", "потому", "только", "после",
        "время", "своего", "своей", "теперь", "когда", "чтобы", "может",
        "будет", "между", "жизнь", "anime", "story", "their", "world",
        "this", "that", "with", "from", "have", "they", "when", "about"
    )

    private fun key(text: String): String = text.lowercase(locale).replace('ё','е')
        .trim().replace(Regex("\\s+"), " ")

    private fun tokens(text: String): Set<String> =
        Regex("[\\p{L}]{4,}").findAll(text.lowercase(locale).replace('ё','е'))
            .map { it.value }.filter { it !in commonWords }
            .take(140).toSet()

    private fun genres(anime: Anime): Set<String> =
        anime.genres.map(::key).filter(String::isNotBlank).toSet()

    private fun influence(entry: LibraryEntry): Double = when {
        entry.status == "Брошено" -> -1.5
        entry.favorite && entry.status == "Просмотрено" -> 5.0
        entry.favorite -> 4.0
        entry.status == "Просмотрено" -> 3.4
        entry.status == "Смотрю" -> 2.7
        entry.lastPosition > 0L -> 2.0
        entry.status == "В планах" -> 0.8
        else -> 0.0
    }

    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    fun rank(
        candidates: List<Anime>,
        history: List<LibraryEntry>,
        explicitGenres: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        mode: RecommendationMode = RecommendationMode.PERSONAL,
        limit: Int = 35
    ): List<AnimeRecommendation> {
        if (limit <= 0) return emptyList()
        val knownIds = history.filter {
            it.favorite || it.status != "Не смотрел" || it.lastPosition > 0L
        }.map { it.anime.id }.toSet()
        val pool = candidates.asSequence()
            .filter { it.id.startsWith("aniliberty:") }
            .filter { it.id !in knownIds && it.id !in hidden }
            .distinctBy { it.id }.take(450).toList()
        if (pool.isEmpty()) return emptyList()

        val positive = history.filter {
            it.anime.id.startsWith("aniliberty:") && influence(it) > 0.0
        }.sortedByDescending(::influence)
        val disliked = history.filter { influence(it) < 0.0 }
        val explicit = explicitGenres.map(::key).toSet()
        val freq = mutableMapOf<String, Int>()
        for (anime in pool) for (g in genres(anime)) freq[g] = (freq[g] ?: 0) + 1
        fun idf(genre: String): Double =
            1.0 + ln((pool.size + 1.0) / ((freq[genre] ?: 0) + 1.0))

        val weighted = mutableMapOf<String, Double>()
        for (entry in positive) {
            val weight = influence(entry)
            for (g in genres(entry.anime)) {
                weighted[g] = (weighted[g] ?: 0.0) + weight
            }
        }
        for (g in explicit) weighted[g] = (weighted[g] ?: 0.0) + 7.0
        val profile = weighted.mapValues { (g, count) -> count * idf(g) }
        val maxProfile = profile.values.sum().coerceAtLeast(1.0)
        val weightedSeeds = positive.take(20)
        val allSeedWords = weightedSeeds.asSequence().flatMap {
            tokens(it.anime.description).asSequence()
        }.groupingBy { it }.eachCount()
        val seedNorm = allSeedWords.values.sum().coerceAtLeast(1).toDouble()

        val centerYear = positive.filter { it.anime.year in 1960..2100 }
            .let { matches ->
                if (matches.isEmpty()) null
                else matches.sumOf { it.anime.year * influence(it) } /
                    matches.sumOf(::influence)
            }
        data class Candidate(
            val anime: Anime,
            val base: Double,
            val keyGenres: Set<String>,
            val reason: String,
            val matching: List<String>
        )
        val scored = pool.map { candidate ->
            val candidateGenres = genres(candidate)
            val matches = candidate.genres.filter { key(it) in profile.keys }
                .distinctBy(::key).sortedByDescending { profile[key(it)] ?: 0.0 }
            val weightedMatches = candidateGenres.sumOf {
                max(0.0, profile[it] ?: 0.0)
            } / maxProfile
            val negative = disliked.sumOf { entry ->
                overlap(candidateGenres, genres(entry.anime)) * 0.12
            }.coerceAtMost(.36)
            val explicitMatch = candidateGenres.count { it in explicit }
                .toDouble() / explicit.size.coerceAtLeast(1)
            val seed = weightedSeeds.maxByOrNull {
                overlap(candidateGenres, genres(it.anime)) *
                    (influence(it) / 5.0)
            }
            val seedSimilarity = seed?.let {
                overlap(candidateGenres, genres(it.anime))
            } ?: 0.0

            val candidateWords = tokens(candidate.description)
            val descriptions = candidateWords.sumOf {
                (allSeedWords[it] ?: 0).toDouble()
            } / seedNorm
            val yearAffinity = centerYear?.let {
                1.0 / (1.0 + abs(candidate.year - it) / 7.0)
            }?.takeIf { candidate.year in 1960..2100 } ?: 0.0
            val rating = (candidate.score / 100.0).coerceIn(0.0, 1.0)
            val freshness = ((candidate.year - 2000) / 30.0)
                .coerceIn(0.0, 1.0)
            val novelty = if (positive.isEmpty()) 1.0 else
                (1.0 - seedSimilarity).coerceIn(0.0, 1.0)

            val base = when (mode) {
                RecommendationMode.PERSONAL ->
                    3.1 * weightedMatches + .6 * seedSimilarity +
                        .40 * descriptions + .26 * yearAffinity +
                        .26 * rating + .08 * freshness + .8 * explicitMatch -
                        negative
                RecommendationMode.GENRES ->
                    3.7 * (if (explicit.isNotEmpty()) explicitMatch else weightedMatches) +
                        .4 * seedSimilarity + .24 * rating + .12 * freshness -
                        negative
                RecommendationMode.DISCOVER ->
                    1.8 * novelty + .45 * weightedMatches + .55 * rating +
                        .15 * freshness - negative
            }
            val reason = when {
                explicit.isNotEmpty() && matches.any { key(it) in explicit } ->
                    "Вы выбрали: " + matches.filter { key(it) in explicit }
                        .take(2).joinToString(", ")
                mode == RecommendationMode.DISCOVER && candidate.genres.isNotEmpty() ->
                    "Попробовать жанр: " + candidate.genres.take(2).joinToString(", ")
                matches.isNotEmpty() && seed != null && seedSimilarity >= .20 ->
                    "Похоже на «" + seed.anime.title + "»"
                matches.isNotEmpty() ->
                    "Ваши жанры: " + matches.take(2).joinToString(", ")
                positive.isEmpty() && explicit.isEmpty() ->
                    "Подборка по каталогу AniLibria"
                candidate.score >= 80 -> "Высокая оценка в каталоге"
                else -> "Другие жанры для знакомства"
            }
            Candidate(candidate, base, candidateGenres, reason, matches.take(3))
        }

        // Maximal marginal relevance: retain accuracy, but diversify recommendations
        // instead of presenting 30 titles of the same genre consecutively.
        val result = mutableListOf<Candidate>()
        val remaining = scored.toMutableList()
        while (remaining.isNotEmpty() && result.size < limit) {
            val picked = remaining.maxWithOrNull(
                compareBy<Candidate> {
                    val redundancy = result.maxOfOrNull { selected ->
                        overlap(it.keyGenres, selected.keyGenres)
                    } ?: 0.0
                    it.base - .40 * redundancy
                }.thenBy { it.anime.id }
            ) ?: break
            result += picked
            remaining.remove(picked)
        }
        return result.map {
            AnimeRecommendation(
                anime = it.anime,
                score = it.base,
                reason = it.reason,
                matchedGenres = it.matching
            )
        }
    }
}
