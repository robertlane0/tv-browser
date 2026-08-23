package com.example.tvbrowser.gate

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewProviderGateTest {

    private fun result(available: Boolean, major: Int) = WebViewProviderGate.Result(
        available = available,
        packageName = if (available) "com.google.android.webview" else null,
        versionName = if (available) "$major.0.0.0" else null,
        majorVersion = major
    )

    @Test
    fun nullProviderBlocks() {
        assertEquals(
            WebViewProviderGate.Action.BLOCKING_ERROR,
            WebViewProviderGate.classify(result(available = false, major = 0))
        )
    }

    @Test
    fun majorBelowMinimumWarnsNonBlocking() {
        assertEquals(
            WebViewProviderGate.Action.WARNING,
            WebViewProviderGate.classify(result(available = true, major = 87))
        )
    }

    @Test
    fun minimumMajorBoundaryIsInclusiveProceed() {
        assertEquals(
            WebViewProviderGate.Action.PROCEED,
            WebViewProviderGate.classify(result(available = true, major = 110))
        )
    }

    @Test
    fun oneBelowBoundaryWarns() {
        assertEquals(
            WebViewProviderGate.Action.WARNING,
            WebViewProviderGate.classify(result(available = true, major = 109))
        )
    }

    @Test
    fun modernProviderProceedsSilently() {
        assertEquals(
            WebViewProviderGate.Action.PROCEED,
            WebViewProviderGate.classify(result(available = true, major = 126))
        )
    }

    @Test
    fun unparsableVersionTreatedAsZeroWarns() {
        val unparsable = WebViewProviderGate.Result(true, "pkg", "not-a-number", 0)
        assertEquals(
            WebViewProviderGate.Action.WARNING,
            WebViewProviderGate.classify(unparsable)
        )
    }
}
