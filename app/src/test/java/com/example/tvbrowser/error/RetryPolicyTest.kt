package com.example.tvbrowser.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun backoffFollowsSpecSchedule() {
        val policy = RetryPolicy()
        assertEquals(2_000L, policy.nextDelayMs(0))
        assertEquals(5_000L, policy.nextDelayMs(1))
        assertEquals(15_000L, policy.nextDelayMs(2))
    }

    @Test
    fun afterThreeAutomaticAttemptsOnlyManualRemains() {
        val policy = RetryPolicy()
        assertNull(policy.nextDelayMs(3))
        assertNull(policy.nextDelayMs(99))
    }

    @Test
    fun maxAutomaticAttemptsIsThree() {
        assertEquals(3, RetryPolicy().maxAutomaticAttempts)
    }

    @Test
    fun customScheduleSupported() {
        val policy = RetryPolicy(listOf(100L))
        assertEquals(100L, policy.nextDelayMs(0))
        assertNull(policy.nextDelayMs(1))
    }
}
