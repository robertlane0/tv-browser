package com.example.tvbrowser.ui.browser

import android.content.Context
import android.view.KeyEvent
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.R
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.UaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BrowserActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun launch(): ActivityController<BrowserActivity> {
        val bookmark = Bookmark(
            title = "Service",
            url = "https://service.example.tv/watch",
            origin = "https://service.example.tv",
            uaMode = UaMode.DESKTOP,
            textZoomPercent = 100
        )
        return Robolectric.buildActivity(
            BrowserActivity::class.java,
            BrowserActivity.createIntent(context, bookmark)
        ).setup().also { it.get().supportFragmentManager.executePendingTransactions() }
    }

    @Test
    fun mediaPlayPauseReachesWebViewThroughActivityDispatch() {
        val controller = launch()
        val webView = controller.get().findViewById<WebView>(R.id.web_view)

        val handled = controller.get().onKeyDown(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )

        assertTrue(handled)
        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("v.paused?v.play():v.pause()"))
    }

    @Test
    fun rewindSeeksBackWithClampGuardInJs() {
        val controller = launch()
        val webView = controller.get().findViewById<WebView>(R.id.web_view)

        controller.get().onKeyDown(
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_REWIND)
        )

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("(-10000/1000)"))
        assertTrue(js.contains("Math.max(0"))
    }

    @Test
    fun backWithoutHistoryFinishesBrowser() {
        val controller = launch()

        val handled = controller.get().onKeyDown(
            KeyEvent.KEYCODE_BACK,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        )

        assertTrue(handled)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun dpadKeysAreNeverIntercepted() {
        val controller = launch()

        assertFalse(
            controller.get().onKeyDown(
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
            )
        )
    }

    @Test
    fun activityDispatchInterceptsMediaKeyDownBeforeWebView() {
        val controller = launch()
        val webView = controller.get().findViewById<WebView>(R.id.web_view)

        val handled = controller.get().dispatchKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        )

        assertTrue(handled)
        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("(10000/1000)"))
    }

    @Test
    fun activityDispatchConsumesMediaKeyUpWithoutInjecting() {
        val controller = launch()
        val webView = controller.get().findViewById<WebView>(R.id.web_view)

        val handled = controller.get().dispatchKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_REWIND)
        )

        assertTrue(handled)
        assertEquals(null, shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun activityDispatchFallsThroughForNonMediaKeys() {
        val controller = launch()

        assertFalse(
            controller.get().dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
            )
        )
    }
}
