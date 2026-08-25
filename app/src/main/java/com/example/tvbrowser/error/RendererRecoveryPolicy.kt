package com.example.tvbrowser.error

/**
 * Gate for renderer-death auto-recovery per spec 09 §5 rule 3: if renderer
 * death recurs >= [maxDeaths] times within [windowMs], stop auto-recovery and
 * surface the `renderer` card with Home as default focus.
 *
 * Pure JVM; inject [now] for deterministic tests.
 */
class RendererRecoveryPolicy(
    private val maxDeaths: Int = DEFAULT_MAX_DEATHS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val now: () -> Long = System::currentTimeMillis
) {

    private val deathTimestamps = ArrayDeque<Long>()

    /**
     * Records a renderer death and answers whether automatic recovery may
     * proceed. Returns false once [maxDeaths] deaths have been observed
     * inside the trailing window.
     */
    fun shouldAutoRecover(): Boolean {
        prune()
        deathTimestamps.addLast(now())
        return deathTimestamps.size < maxDeaths
    }

    fun reset() = deathTimestamps.clear()

    private fun prune() {
        val cutoff = now() - windowMs
        while (deathTimestamps.isNotEmpty() && deathTimestamps.first() < cutoff) {
            deathTimestamps.removeFirst()
        }
    }

    private companion object {
        const val DEFAULT_MAX_DEATHS = 3
        const val DEFAULT_WINDOW_MS = 60_000L
    }
}
