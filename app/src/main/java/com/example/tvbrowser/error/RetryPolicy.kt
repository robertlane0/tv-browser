package com.example.tvbrowser.error

/**
 * Automatic retry backoff per spec 09 §4.3: 2 s, 5 s, 15 s; maximum three
 * automatic attempts, then the card waits for a manual Retry.
 */
class RetryPolicy(private val backoffScheduleMs: List<Long> = DEFAULT_BACKOFF) {

    val maxAutomaticAttempts: Int = backoffScheduleMs.size

    /**
     * Delay before automatic attempt number [completedAttempts] (0-based),
     * or null when the cap is reached and only a manual retry remains.
     */
    fun nextDelayMs(completedAttempts: Int): Long? =
        backoffScheduleMs.getOrNull(completedAttempts)

    companion object {
        val DEFAULT_BACKOFF = listOf(2_000L, 5_000L, 15_000L)
    }
}
