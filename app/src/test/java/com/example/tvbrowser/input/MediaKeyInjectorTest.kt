package com.example.tvbrowser.input

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaKeyInjectorTest {

    private lateinit var host: WebViewTestHost
    private lateinit var webView: WebView
    private lateinit var injector: MediaKeyInjector

    @Before
    fun setUp() {
        host = WebViewTestHost()
        webView = host.attachedWebView()
        injector = MediaKeyInjector(webView)
    }

    @Test
    fun togglePlayPauseEvaluatesToggleJs() {
        injector.togglePlayPause()

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("v.paused?v.play():v.pause()"))
        assertTrue(js.contains("document.querySelector('video')"))
    }

    @Test
    fun fastForwardSeeksPlusTenSeconds() {
        injector.seekBy(RemoteInputHandler.SEEK_STEP_MS)

        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("(10000/1000)"))
    }

    @Test
    fun rewindSeeksMinusTenSeconds() {
        injector.seekBy(-RemoteInputHandler.SEEK_STEP_MS)

        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("(-10000/1000)"))
    }

    @Test
    fun seekJsClampsToLowerAndUpperBound() {
        injector.seekBy(RemoteInputHandler.SEEK_STEP_MS)

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("Math.max(0"))
        assertTrue(js.contains("Math.min(v.duration||1e9"))
    }

    @Test
    fun detachedWebViewSilentlyIgnoresMediaKeys() {
        val detached = host.detachedWebView()
        val detachedInjector = MediaKeyInjector(detached)

        detachedInjector.togglePlayPause()
        detachedInjector.seekBy(RemoteInputHandler.SEEK_STEP_MS)

        assertNull(shadowOf(detached).lastEvaluatedJavascript)
    }
}
