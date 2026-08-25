package com.example.tvbrowser.error

import android.os.Handler

/**
 * Drives the automatic retry policy of spec 09 §4.3 on the main thread:
 * backoff 2 s / 5 s / 15 s with at most three automatic attempts; cancelled
 * on key events, connectivity change, pause, or a new loadUrl.
 */
class AutoRetryController(
    private val classifier: ErrorClassifier,
    private val policy: RetryPolicy,
    private val handler: Handler,
    private val onRetry: () -> Unit
) {

    private var completedAttempts = 0
    private var pending: Runnable? = null

    /** Schedules the next automatic retry unless category/guard forbids it. */
    fun scheduleAfterFailure(error: TvError) {
        cancelPending()
        if (!classifier.isAutoRetryable(error.category) || !error.allowsAutoRetry) return
        val delayMs = policy.nextDelayMs(completedAttempts) ?: return
        val runnable = Runnable {
            pending = null
            completedAttempts++
            onRetry()
        }
        pending = runnable
        handler.postDelayed(runnable, delayMs)
    }

    /**
     * Immediate automatic retry for the connectivity-regain path of
     * spec 09 §6.3. Respects the attempt cap; no-op when exhausted.
     */
    fun retryNowIfEligible(category: Category) {
        cancelPending()
        if (!classifier.isAutoRetryable(category)) return
        if (policy.nextDelayMs(completedAttempts) == null) return
        completedAttempts++
        onRetry()
    }

    /** Manual Retry resets the backoff counter (spec 09 §4.3 rule 2). */
    fun reset() {
        cancelPending()
        completedAttempts = 0
    }

    fun cancelPending() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}
