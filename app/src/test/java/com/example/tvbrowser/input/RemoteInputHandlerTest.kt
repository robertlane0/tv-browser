package com.example.tvbrowser.input

import android.graphics.Rect
import android.view.KeyEvent
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.ui.browser.BrowserOverlayController
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
class RemoteInputHandlerTest {

    private class FakeOverlay : BrowserOverlayController {
        override var isVisible: Boolean = false
        var hideCount = 0
            private set

        override fun show() {
            isVisible = true
        }

        override fun hide() {
            isVisible = false
            hideCount++
        }

        override fun toggle() {
            isVisible = !isVisible
        }
    }

    private class RecordingFocusWebView(context: android.content.Context) : WebView(context) {
        var requestFocusCalls = 0
            private set

        override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
            requestFocusCalls++
            return super.requestFocus(direction, previouslyFocusedRect)
        }
    }

    private lateinit var host: WebViewTestHost
    private lateinit var webView: RecordingFocusWebView
    private lateinit var overlay: FakeOverlay
    private var exitCount = 0
    private lateinit var handler: RemoteInputHandler

    @Before
    fun setUp() {
        host = WebViewTestHost()
        webView = host.attach(RecordingFocusWebView(host.activity))
        overlay = FakeOverlay()
        exitCount = 0
        handler = RemoteInputHandler(webView, overlay, MediaKeyInjector(webView)) { exitCount++ }
    }

    private fun keyDown(keyCode: Int, repeatCount: Int = 0): Boolean =
        handler.onKeyDown(
            keyCode,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, repeatCount)
        )

    @Test
    fun dpadDirectionsAndCenterFallThroughToWebView() {
        val passThroughKeys = intArrayOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER
        )

        passThroughKeys.forEach { keyCode ->
            assertFalse("key $keyCode must not be intercepted", keyDown(keyCode))
        }
        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun backHidesVisibleOverlayAndRequestsWebViewFocus() {
        overlay.isVisible = true

        assertTrue(keyDown(KeyEvent.KEYCODE_BACK))

        assertEquals(1, overlay.hideCount)
        assertEquals(1, webView.requestFocusCalls)
        assertEquals(0, exitCount)
    }

    @Test
    fun backNavigatesHistoryWhenOverlayHidden() {
        val shadow = shadowOf(webView)
        shadow.pushEntryToHistory("https://service.example.tv/first")
        shadow.pushEntryToHistory("https://service.example.tv/watch")

        assertTrue(keyDown(KeyEvent.KEYCODE_BACK))

        assertEquals(1, shadow.goBackInvocations)
        assertFalse(overlay.isVisible)
        assertEquals(0, exitCount)
    }

    @Test
    fun backExitsWhenNoHistoryRemains() {
        assertTrue(keyDown(KeyEvent.KEYCODE_BACK))

        assertEquals(1, exitCount)
        assertEquals(0, shadowOf(webView).goBackInvocations)
    }

    @Test
    fun menuTogglesOverlayVisibility() {
        assertTrue(keyDown(KeyEvent.KEYCODE_MENU))
        assertTrue(overlay.isVisible)

        assertTrue(keyDown(KeyEvent.KEYCODE_MENU))
        assertFalse(overlay.isVisible)
    }

    @Test
    fun menuCloseRequestsWebViewFocusButMenuOpenDoesNot() {
        keyDown(KeyEvent.KEYCODE_MENU)
        assertEquals(0, webView.requestFocusCalls)

        keyDown(KeyEvent.KEYCODE_MENU)

        assertEquals(1, webView.requestFocusCalls)
    }

    @Test
    fun playPauseInjectsToggleJs() {
        assertTrue(keyDown(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))

        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("v.paused?v.play():v.pause()"))
    }

    @Test
    fun fastForwardSeeksForwardTenSeconds() {
        assertTrue(keyDown(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))

        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("(10000/1000)"))
    }

    @Test
    fun rewindSeeksBackTenSecondsWithClamp() {
        assertTrue(keyDown(KeyEvent.KEYCODE_MEDIA_REWIND))

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("(-10000/1000)"))
        assertTrue(js.contains("Math.max(0"))
    }

    @Test
    fun repeatedMediaKeyEventsAreIgnoredWithoutAction() {
        keyDown(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val scriptAfterFirstPress = shadowOf(webView).lastEvaluatedJavascript

        assertTrue(keyDown(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, repeatCount = 1))
        assertTrue(keyDown(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, repeatCount = 3))
        assertTrue(keyDown(KeyEvent.KEYCODE_MEDIA_REWIND, repeatCount = 2))

        assertEquals(scriptAfterFirstPress, shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun mediaKeyClassifierMatchesOnlyMediaKeys() {
        RemoteInputHandler.MEDIA_KEYS.forEach { keyCode ->
            assertTrue("key $keyCode must be classified as media key", handler.isMediaKey(keyCode))
        }

        intArrayOf(
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_ENTER
        ).forEach { keyCode ->
            assertFalse("key $keyCode must not be classified as media key", handler.isMediaKey(keyCode))
        }
    }
}
