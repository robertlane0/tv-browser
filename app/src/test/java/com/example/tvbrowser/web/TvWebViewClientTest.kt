package com.example.tvbrowser.web

import android.os.Build
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.error.Category
import com.example.tvbrowser.error.ErrorClassifier
import com.example.tvbrowser.error.RedirectLoopDetector
import com.example.tvbrowser.error.TvError
import com.example.tvbrowser.input.CssInjector
import com.example.tvbrowser.input.WebViewTestHost
import com.example.tvbrowser.web.WebErrorFixtures.RecordingListener
import com.example.tvbrowser.web.WebErrorFixtures.httpErrorResponse
import com.example.tvbrowser.web.WebErrorFixtures.mainFrameRequest
import com.example.tvbrowser.web.WebErrorFixtures.rendererGoneDetail
import com.example.tvbrowser.web.WebErrorFixtures.resourceError
import com.example.tvbrowser.web.WebErrorFixtures.sslErrorHandler
import com.example.tvbrowser.web.WebErrorFixtures.sslError
import com.example.tvbrowser.web.WebErrorFixtures.subResourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ---- spec 09 §2–§6 detection surfaces ----

    private fun clientWith(listener: RecordingListener): TvWebViewClient =
        TvWebViewClient(
            CssInjector { "#00BFFF" },
            classifier = ErrorClassifier(),
            listener = listener
        )

    @Test
    fun mainFrameNetworkErrorIsClassifiedWithAutoRetryAllowed() {
        val listener = RecordingListener()
        val reporting = clientWith(listener)

        reporting.onReceivedError(
            webView,
            mainFrameRequest("https://service.example.tv/watch"),
            resourceError(WebViewClient.ERROR_HOST_LOOKUP)
        )

        assertEquals(listOf(TvError(Category.NETWORK, null, true)), listener.errors)
    }

    @Test
    fun subresourceErrorsAreNeverSurfaced() {
        val listener = RecordingListener()
        val reporting = clientWith(listener)

        reporting.onReceivedError(
            webView,
            subResourceRequest("https://cdn.example/avatar.png"),
            resourceError(WebViewClient.ERROR_IO)
        )
        reporting.onReceivedHttpError(
            webView,
            subResourceRequest("https://cdn.example/beacon"),
            httpErrorResponse(500)
        )

        assertTrue(listener.errors.isEmpty())
    }

    @Test
    fun legacyMainFrameErrorStillClassifiesAsNetwork() {
        val listener = RecordingListener()
        val reporting = clientWith(listener)

        @Suppress("DEPRECATION")
        reporting.onReceivedError(
            webView,
            WebViewClient.ERROR_TIMEOUT,
            "timeout",
            "https://service.example.tv/watch"
        )

        assertEquals(listOf(TvError(Category.NETWORK, null, true)), listener.errors)
    }

    @Test
    fun http403MainFrameIsBlockedWithoutRetry() {
        val listener = RecordingListener()
        val reporting = clientWith(listener)

        reporting.onReceivedHttpError(webView, mainFrameRequest("https://svc.example"), httpErrorResponse(403))

        assertEquals(listOf(TvError(Category.BLOCKED, 403, false)), listener.errors)
    }

    @Test
    fun httpClientErrorsAreNotAutoRetried() {
        val listener = RecordingListener()
        val reporting = clientWith(listener)

        reporting.onReceivedHttpError(webView, mainFrameRequest("https://svc.example"), httpErrorResponse(404))

        assertEquals(listOf(TvError(Category.HTTP_CLIENT, 404, false)), listener.errors)
    }

    @Test
    fun httpServerErrorMainFrameAllowsAutoRetryForGet() {
        val listener = RecordingListener()
        val reporting = clientWith(listener)

        reporting.onReceivedHttpError(webView, mainFrameRequest("https://svc.example"), httpErrorResponse(503))

        assertEquals(listOf(TvError(Category.HTTP_SERVER, 503, true)), listener.errors)
    }

    @Test
    fun postGuardSuppressesAutoRetryEvenForRetryableCategory() {
        val listener = RecordingListener()

        clientWith(listener).onReceivedHttpError(
            webView,
            mainFrameRequest("https://svc.example/checkout", method = "POST"),
            httpErrorResponse(503)
        )

        assertEquals(listOf(TvError(Category.HTTP_SERVER, 503, false)), listener.errors)
    }

    @Test
    fun headMethodMayBeReplayed() {
        val listener = RecordingListener()
        val reporting = TvWebViewClient(
            CssInjector { "#00BFFF" },
            classifier = ErrorClassifier(),
            listener = listener
        )

        reporting.onReceivedHttpError(
            webView,
            mainFrameRequest("https://svc.example", method = "HEAD"),
            httpErrorResponse(503)
        )

        assertEquals(listOf(TvError(Category.HTTP_SERVER, 503, true)), listener.errors)
    }

    @Test
    fun sslErrorsAreCancelledStrictlyAndSurfaceSslCard() {
        val listener = RecordingListener()
        val reporting = clientWith(listener)
        val handler = sslErrorHandler()

        reporting.onReceivedSslError(webView, handler, sslError())

        assertEquals(listOf(TvError(Category.SSL)), listener.errors)
    }

    @Test
    fun loginRedirectLoopReportsBlockedOncePerOffendingUrl() {
        var timeMs = 0L
        val detector = RedirectLoopDetector { timeMs }
        val listener = RecordingListener()
        val reporting = TvWebViewClient(
            CssInjector { "#00BFFF" },
            loopDetector = detector,
            classifier = ErrorClassifier(),
            listener = listener
        )

        repeat(4) {
            reporting.doUpdateVisitedHistory(webView, "https://svc.example/login", false)
            timeMs += 100
        }
        reporting.doUpdateVisitedHistory(webView, "https://svc.example/home", false)

        assertEquals(listOf(TvError(Category.BLOCKED)), listener.errors)
        assertEquals(5, listener.navigations.size)
    }

    @Test
    fun reloadsDoNotFeedTheLoopDetector() {
        val detector = RedirectLoopDetector()
        val listener = RecordingListener()
        val reporting = TvWebViewClient(
            CssInjector { "#00BFFF" },
            loopDetector = detector,
            classifier = ErrorClassifier(),
            listener = listener
        )

        repeat(3) {
            reporting.doUpdateVisitedHistory(webView, "https://svc.example/watch", true)
        }

        assertTrue(listener.errors.isEmpty())
        assertFalse(detector.isLooping())
    }

    @Test
    fun rendererGoneExitsFullscreenDestroysAndReportsWithoutCrashingApp() {
        val fullscreen = RecordingExitFullscreen()
        val listener = RecordingListener()
        val reporting = TvWebViewClient(
            CssInjector { "#00BFFF" },
            fullscreen,
            classifier = ErrorClassifier(),
            listener = listener
        )
        val doomed = host.attachedWebView()

        val handled = reporting.onRenderProcessGone(doomed, rendererGoneDetail(didCrash = true))

        assertTrue(handled)
        assertEquals(1, fullscreen.exitCalls)
        assertNull(doomed.parent)
        assertTrue(shadowOf(doomed).wasDestroyCalled())
        assertEquals(listOf(true), listener.rendererCrashes)
    }

    private class RecordingExitFullscreen : FullscreenController {
        var exitCalls = 0
            private set

        override fun isInFullscreen(): Boolean = true

        override fun exitFullscreen() {
            exitCalls++
        }

        override fun forceTeardown() {}
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
