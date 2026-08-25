package com.example.tvbrowser.error

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererRecoveryPolicyTest {

    private var timeMs = 0L

    private fun policy(windowMs: Long = 60_000) =
        RendererRecoveryPolicy(maxDeaths = 3, windowMs = windowMs) { timeMs }

    @Test
    fun firstTwoDeathsAutoRecover() {
        val p = policy()
        assertTrue(p.shouldAutoRecover())
        assertTrue(p.shouldAutoRecover())
    }

    @Test
    fun thirdDeathWithinSixtySecondsStopsAutoRecovery() {
        val p = policy()
        p.shouldAutoRecover()
        p.shouldAutoRecover()
        assertFalse(p.shouldAutoRecover())
    }

    @Test
    fun deathsOutsideWindowExpireAllowingRecoveryAgain() {
        val p = policy(windowMs = 60_000)
        assertTrue(p.shouldAutoRecover())
        timeMs += 61_000
        assertTrue(p.shouldAutoRecover())
        timeMs += 61_000
        assertTrue(p.shouldAutoRecover())
    }

    @Test
    fun slidingWindowCountsOnlyRecentDeaths() {
        val p = policy(windowMs = 60_000)
        assertTrue(p.shouldAutoRecover())          // t=0
        timeMs += 59_000                            // t=59s
        assertTrue(p.shouldAutoRecover())          // t=59s
        timeMs += 2_000                             // t=61s: first death expired
        assertTrue(p.shouldAutoRecover())
    }

    @Test
    fun resetRestoresAutoRecovery() {
        val p = policy()
        p.shouldAutoRecover()
        p.shouldAutoRecover()
        p.reset()
        assertTrue(p.shouldAutoRecover())
    }
}
