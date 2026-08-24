package com.example.tvbrowser.web

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.input.CssInjector
import com.example.tvbrowser.input.WebViewTestHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TvWebViewClientTest {

    private lateinit var host: WebViewTestHost
    private lateinit var webView: WebView
    private lateinit var client: TvWebViewClient

    @Before
    fun setUp() {
        host = WebViewTestHost()
        webView = host.attachedWebView()
        client = TvWebViewClient(CssInjector { "#00BFFF" })
    }

    @Test
    fun onPageFinishedInjectsFocusHighlight() {
        client.onPageFinished(webView, "https://service.example.tv/watch")

        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains(CssInjector.GUARD_ELEMENT_ID))
    }

    @Test
    fun spaHistoryUpdatesAlsoInjectFocusHighlight() {
        client.doUpdateVisitedHistory(webView, "https://service.example.tv/watch#page2", false)

        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("getElementById(id)"))
    }

    @Test
    fun bothTimingHooksEmitGuardedScript() {
        client.onPageFinished(webView, "https://service.example.tv/watch")
        val afterPageFinished = shadowOf(webView).lastEvaluatedJavascript!!

        client.doUpdateVisitedHistory(webView, "https://service.example.tv/watch#page2", false)
        val afterHistoryUpdate = shadowOf(webView).lastEvaluatedJavascript!!

        assertTrue(afterPageFinished.contains("if(!document.getElementById(id))"))
        assertTrue(afterHistoryUpdate.contains("if(!document.getElementById(id))"))
    }

    @Test
    fun injectionSkippedForDetachedWebView() {
        val detached = host.detachedWebView()

        client.onPageFinished(detached, "https://service.example.tv/watch")
        client.doUpdateVisitedHistory(detached, "https://service.example.tv", false)

        assertNull(shadowOf(detached).lastEvaluatedJavascript)
    }

    @Test
    fun onPageStartedForcesFullscreenTeardownForStuckContainer() {
        val fullscreen = RecordingFullscreen()
        val guarded = TvWebViewClient(CssInjector { "#00BFFF" }, fullscreen)

        guarded.onPageStarted(webView, "https://service.example.tv/next", null)

        assertEquals(1, fullscreen.teardownCalls)
    }

    @Test
    fun onPageStartedRunsInjectedFallbackInjection() {
        val injectedViews = mutableListOf<WebView>()
        val hooked = TvWebViewClient(
            CssInjector { "#00BFFF" },
            onPageStartedInjection = { injectedViews.add(it) }
        )

        hooked.onPageStarted(webView, "https://service.example.tv/next", null)

        assertEquals(listOf(webView), injectedViews)
    }

    private class RecordingFullscreen : FullscreenController {
        var teardownCalls = 0
            private set

        override fun isInFullscreen(): Boolean = true

        override fun exitFullscreen() {
            teardownCalls++
        }

        override fun forceTeardown() {
            teardownCalls++
        }
    }
}
