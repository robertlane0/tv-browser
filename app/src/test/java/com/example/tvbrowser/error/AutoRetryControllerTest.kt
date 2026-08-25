package com.example.tvbrowser.error

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AutoRetryControllerTest {

    private val classifier = ErrorClassifier()
    private val handler = Handler(Looper.getMainLooper())
    private var fired = 0

    private fun controller(): AutoRetryController =
        AutoRetryController(classifier, RetryPolicy(), handler) { fired++ }

    private fun idleFor(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    @Test
    fun backoffFollowsTwoFiveFifteenSecondsAndCapsAtThree() {
        val c = controller()

        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        idleFor(2_000)
        assertEquals(1, fired)

        c.scheduleAfterFailure(TvError(Category.HTTP_SERVER, allowsAutoRetry = true))
        idleFor(4_999)
        assertEquals("no fire before the 5 s mark", 1, fired)
        idleFor(1)
        assertEquals(2, fired)

        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        idleFor(15_000)
        assertEquals(3, fired)

        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        idleFor(60_000)
        assertEquals("cap reached; manual retry only", 3, fired)
    }

    @Test
    fun postGuardSuppressesAutomaticRetry() {
        val c = controller()

        c.scheduleAfterFailure(TvError(Category.HTTP_SERVER, allowsAutoRetry = false))
        idleFor(20_000)

        assertEquals(0, fired)
    }

    @Test
    fun nonRetryableCategoriesAreNeverScheduled() {
        val c = controller()

        Category.entries
            .filterNot { classifier.isAutoRetryable(it) }
            .forEach { category ->
                c.scheduleAfterFailure(TvError(category, allowsAutoRetry = true))
            }
        idleFor(60_000)

        assertEquals(0, fired)
    }

    @Test
    fun cancelPendingStopsScheduledFire() {
        val c = controller()

        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        c.cancelPending()
        idleFor(10_000)

        assertEquals(0, fired)
    }

    @Test
    fun manualResetRestartsBackoffFromFirstStep() {
        val c = controller()
        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        idleFor(2_000)
        assertEquals(1, fired)

        c.reset()
        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        idleFor(1_999)
        assertEquals("reset restarts at the 2 s step", 1, fired)
        idleFor(1)
        assertEquals(2, fired)
    }

    @Test
    fun connectivityRegainFiresImmediatelyAndConsumesAttempt() {
        val c = controller()

        c.retryNowIfEligible(Category.NETWORK)
        assertEquals(1, fired)

        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        idleFor(4_999)
        assertEquals("next automatic retry waits the 5 s step", 1, fired)
        idleFor(1)
        assertEquals(2, fired)
    }

    @Test
    fun connectivityRegainRespectsAttemptCap() {
        val c = controller()
        repeat(3) { c.retryNowIfEligible(Category.NETWORK) }
        assertEquals(3, fired)

        c.retryNowIfEligible(Category.NETWORK)
        assertEquals("exhausted", 3, fired)
    }

    @Test
    fun regainIgnoredForNonNetworkCategories() {
        val c = controller()

        c.retryNowIfEligible(Category.SSL)

        assertEquals(0, fired)
    }

    @Test
    fun reschedulingReplacesPendingRetryExactlyOnce() {
        val c = controller()

        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        c.scheduleAfterFailure(TvError(Category.NETWORK, allowsAutoRetry = true))
        idleFor(2_000)

        assertEquals("second schedule must not double-fire", 1, fired)
        assertTrue(fired <= 1)
    }
}
