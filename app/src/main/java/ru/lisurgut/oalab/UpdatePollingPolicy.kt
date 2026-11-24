package ru.lisurgut.oalab

/** Screen refreshes every 5 seconds, remote checks have a safe GitHub cooldown. */
object UpdatePollingPolicy {
    const val UI_REFRESH_MS = 5_000L
    const val NETWORK_REFRESH_MS = 5 * 60_000L
    const val RETRY_ON_ERROR_MS = 2 * 60_000L

    fun shouldRequest(
        elapsedMs: Long,
        lastAttemptMs: Long,
        resultPresent: Boolean,
        lastError: Boolean,
        forced: Boolean
    ): Boolean {
        if (forced || !resultPresent) return true
        if (elapsedMs < lastAttemptMs) return true
        val cooldown = if (lastError) RETRY_ON_ERROR_MS else NETWORK_REFRESH_MS
        return elapsedMs - lastAttemptMs >= cooldown
    }

    fun isNewer(remote: String, installed: String): Boolean {
        val a = remote.removePrefix("v").substringBefore("-").split(".")
            .map { it.toIntOrNull() ?: 0 }
        val b = installed.removePrefix("v").substringBefore("-").split(".")
            .map { it.toIntOrNull() ?: 0 }
        for (index in 0..3) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
