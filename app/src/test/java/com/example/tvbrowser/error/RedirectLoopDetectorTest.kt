package com.example.tvbrowser.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectLoopDetectorTest {

    private var timeMs = 0L

    private fun detector(
        windowMs: Long = 10_000,
        threshold: Int = 3
    ) = RedirectLoopDetector(windowMs = windowMs, threshold = threshold) { timeMs }

    @Test
    fun loginLoopDetectedAfterThreeHits() {
        val d = detector()
        repeat(3) { d.onUrl("https://svc.example/login") }
        assertTrue(d.isLooping())
    }

    @Test
    fun fewerThanThresholdHitsIsNotALoop() {
        val d = detector()
        d.onUrl("https://svc.example/login")
        d.onUrl("https://svc.example/login")
        assertFalse(d.isLooping())
    }

    @Test
    fun alternatingUrlsStillFlagsRepeatedTail() {
        val d = detector()
        // Ring holds the last 8 visits; /b appears 4 times inside the window,
        // which meets the ">= 3 same URL" rule of spec 09 §6.4.
        repeat(4) {
            d.onUrl("https://svc.example/a")
            d.onUrl("https://svc.example/b")
        }
        assertTrue(d.isLooping())
        assertEquals("https://svc.example/b", d.loopingUrl())
    }

    @Test
    fun uniqueUrlsNeverLoop() {
        val d = detector()
        repeat(8) { index -> d.onUrl("https://svc.example/page$index") }
        assertFalse(d.isLooping())
    }

    @Test
    fun hitsOutsideWindowDoNotCount() {
        val d = detector(windowMs = 10_000)
        d.onUrl("https://svc.example/login")
        d.onUrl("https://svc.example/login")
        timeMs += 11_000
        d.onUrl("https://svc.example/login")
        assertFalse(d.isLooping())
    }

    @Test
    fun loopClearsOnceWindowSlidesPastOldHits() {
        val d = detector(windowMs = 10_000)
        repeat(3) {
            d.onUrl("https://svc.example/login")
            timeMs += 100
        }
        assertTrue(d.isLooping())

        timeMs += 11_000
        d.onUrl("https://svc.example/other")
        assertFalse(d.isLooping())
    }

    @Test
    fun loopDetectionRecoversForLaterRepetition() {
        val d = detector(threshold = 3)
        repeat(3) { d.onUrl("https://svc.example/login") }
        assertTrue(d.isLooping())
        d.reset()

        repeat(2) { d.onUrl("https://svc.example/login") }
        assertFalse(d.isLooping())
        d.onUrl("https://svc.example/login")
        assertTrue(d.isLooping())
    }

    @Test
    fun loopingUrlReportsOffendingAddress() {
        val d = detector()
        repeat(2) { d.onUrl("https://svc.example/home") }
        d.onUrl("https://svc.example/login")
        d.onUrl("https://svc.example/login")
        d.onUrl("https://svc.example/login")

        assertEquals("https://svc.example/login", d.loopingUrl())
    }

    @Test
    fun emptyHistoryIsNotLooping() {
        assertNull(detector().loopingUrl())
        assertFalse(detector().isLooping())
    }

    @Test
    fun ringBufferCapsAtEightMainframeUrls() {
        val d = detector(threshold = 8)
        repeat(7) { index -> d.onUrl("https://svc.example/$index") }
        // Oldest entry evicted; the first URL no longer has 8 occurrences.
        repeat(7) { d.onUrl("https://svc.example/0") }
        assertFalse(d.isLooping())
        d.onUrl("https://svc.example/0")
        assertTrue(d.isLooping())
    }

    @Test
    fun emptyUrlsAreIgnored() {
        val d = detector()
        d.onUrl("")
        assertFalse(d.isLooping())
    }

    @Test
    fun customWindowAndThresholdHonored() {
        val strict = detector(windowMs = 1_000, threshold = 2)
        strict.onUrl("https://svc.example/login")
        strict.onUrl("https://svc.example/login")
        assertTrue(strict.isLooping())
    }
}
