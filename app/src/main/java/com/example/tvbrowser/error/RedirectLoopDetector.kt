package com.example.tvbrowser.error

/**
 * Login redirect loop detection per spec 09 §6.4: keeps a ring buffer of the
 * last 8 main-frame URLs; if the same URL appears >= threshold times within
 * windowMs, classify as [Category.BLOCKED] (cookie or UA problem).
 *
 * Pure JVM; inject [now] for deterministic tests.
 */
class RedirectLoopDetector(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val maxEntries: Int = RING_BUFFER_SIZE,
    private val now: () -> Long = System::currentTimeMillis
) {

    private data class Visit(val url: String, val at: Long)

    private val visits = ArrayDeque<Visit>()

    fun onUrl(url: String) {
        if (url.isEmpty()) return
        visits.addLast(Visit(url, now()))
        while (visits.size > maxEntries) {
            visits.removeFirst()
        }
    }

    /**
     * True when the most recent URL has been visited at least [threshold]
     * times inside the trailing [windowMs] window.
     */
    fun isLooping(): Boolean {
        val last = visits.lastOrNull() ?: return false
        val cutoff = now() - windowMs
        return visits.count { it.url == last.url && it.at >= cutoff } >= threshold
    }

    /** The URL currently considered looping, if any. */
    fun loopingUrl(): String? = if (isLooping()) visits.last().url else null

    fun reset() = visits.clear()

    private companion object {
        const val DEFAULT_WINDOW_MS = 10_000L
        const val DEFAULT_THRESHOLD = 3
        const val RING_BUFFER_SIZE = 8
    }
}
