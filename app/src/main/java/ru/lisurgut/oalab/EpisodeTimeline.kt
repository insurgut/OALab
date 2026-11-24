package ru.lisurgut.oalab

/** Pure arithmetic behind the merged playback timeline and screenshot sampling. */
data class EpisodeSeek(val index: Int, val positionMs: Long)

object EpisodeTimeline {
    fun totalDuration(durations: List<Long>): Long? {
        if (durations.isEmpty() || durations.any { it <= 0L }) return null
        var total = 0L
        for (duration in durations) {
            if (Long.MAX_VALUE - total < duration) return null
            total += duration
        }
        return total
    }

    /** Converts a virtual seek into an exact Media3 item index and local offset. */
    fun locate(durations: List<Long>, globalPosition: Long): EpisodeSeek? {
        val total = totalDuration(durations) ?: return null
        var remaining = globalPosition.coerceIn(0L, total)
        for (i in durations.indices) {
            if (remaining < durations[i] || i == durations.lastIndex)
                return EpisodeSeek(i, remaining.coerceAtMost(durations[i]))
            remaining -= durations[i]
        }
        return null
    }

    fun globalPosition(
        durations: List<Long>, index: Int, localPosition: Long
    ): Long? {
        if (index !in durations.indices) return null
        if (durations.take(index + 1).any { it <= 0L }) return null
        val offset = durations.take(index).sum()
        return offset + localPosition.coerceIn(0L, durations[index])
    }

    fun boundaryFractions(durations: List<Long>): List<Float> {
        val total = totalDuration(durations) ?: return emptyList()
        var cursor = 0L
        return durations.dropLast(1).map { duration ->
            cursor += duration
            cursor.toFloat() / total
        }
    }

    fun frameTimes(durationMs: Long): List<Long> {
        if (durationMs <= 0L) return emptyList()
        return (0 until 10).map { index ->
            ((index + 0.5) * durationMs / 10).toLong()
        }
    }
}
