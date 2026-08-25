package com.example.tvbrowser.ui.browser

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.R
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.UaMode
import com.example.tvbrowser.web.TvWebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

    private fun fragment(controller: ActivityController<BrowserActivity>): WebViewFragment =
        controller.get().supportFragmentManager
            .findFragmentById(R.id.browser_container) as WebViewFragment

    private fun keepScreenOnSet(controller: ActivityController<BrowserActivity>): Boolean =
        shadowOf(controller.get().window)
            .getFlag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    private fun enterFullscreen(
        controller: ActivityController<BrowserActivity>
    ): Pair<View, WebChromeClient.CustomViewCallback> {
        val activity = controller.get()
        val chrome = fragment(controller).activeChromeClient!!
        val customView = View(activity)
        val callback = object : WebChromeClient.CustomViewCallback {
            var hideCalls = 0
                private set

            override fun onCustomViewHidden() {
                hideCalls++
                chrome.onHideCustomView()
            }
        }
        chrome.onShowCustomView(customView, callback)
        return customView to callback
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

    @Test
    fun t01FullscreenRoundTripAttachesCustomViewThenBackRestoresEverything() {
        val controller = launch()
        val activity = controller.get()
        val customView = enterFullscreen(controller).first

        val fullscreenContainer = activity.findViewById<FrameLayout>(R.id.fullscreen_container)
        assertTrue(fullscreenContainer.isVisible)
        assertSame(customView, fullscreenContainer.getChildAt(0))
        assertFalse(activity.findViewById<WebView>(R.id.web_view).isVisible)
        assertTrue(keepScreenOnSet(controller))

        val handled = activity.onKeyDown(
            KeyEvent.KEYCODE_BACK,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        )

        assertTrue(handled)
        assertFalse("T-01: Back exits fullscreen instead of the browser", activity.isFinishing)
        assertFalse(fullscreenContainer.isVisible)
        assertEquals(View.VISIBLE, activity.findViewById<WebView>(R.id.web_view).visibility)
        assertFalse(keepScreenOnSet(controller))
    }

    @Test
    fun t02FirstBackExitsFullscreenSecondBackFinishes() {
        val controller = launch()
        val activity = controller.get()

        enterFullscreen(controller)

        assertTrue(
            activity.onKeyDown(
                KeyEvent.KEYCODE_BACK,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
            )
        )
        assertFalse(activity.isFinishing)

        assertTrue(
            activity.onKeyDown(
                KeyEvent.KEYCODE_BACK,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
            )
        )
        assertTrue(activity.isFinishing)
    }

    @Test
    fun backInFullscreenNeverNavigatesHistoryFirst() {
        val controller = launch()
        val activity = controller.get()
        val shadow = shadowOf(activity.findViewById<WebView>(R.id.web_view))
        shadow.pushEntryToHistory("https://service.example.tv/first")
        shadow.pushEntryToHistory("https://service.example.tv/watch")
        enterFullscreen(controller)

        activity.onKeyDown(
            KeyEvent.KEYCODE_BACK,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        )

        assertEquals("fullscreen owns Back before goBack", 0, shadow.goBackInvocations)
    }

    @Test
    fun onPageStartNavigationClearsStuckFullscreenContainer() {
        val controller = launch()
        val activity = controller.get()
        val webView = activity.findViewById<WebView>(R.id.web_view)
        enterFullscreen(controller)

        (shadowOf(webView).webViewClient as TvWebViewClient)
            .onPageStarted(webView, "https://service.example.tv/next", null)

        assertFalse(activity.findViewById<FrameLayout>(R.id.fullscreen_container).isVisible)
    }

    @Test
    fun onDestroyClearsKeepScreenOnDefensively() {
        val controller = launch()
        enterFullscreen(controller)

        controller.destroy()

        assertFalse(keepScreenOnSet(controller))
    }

    @Test
    fun recreationAfterProcessDeathStartsWithoutStuckContainer() {
        val bookmark = Bookmark(
            title = "Service",
            url = "https://service.example.tv/watch",
            origin = "https://service.example.tv",
            uaMode = UaMode.DESKTOP,
            textZoomPercent = 100
        )
        val intent = BrowserActivity.createIntent(context, bookmark)
        val first = Robolectric.buildActivity(BrowserActivity::class.java, intent).setup()
        val savedState = Bundle().also { first.saveInstanceState(it) }
        first.destroy()

        val restored = Robolectric.buildActivity(BrowserActivity::class.java, intent)
            .create(savedState).start().resume().visible()

        assertFalse(restored.get().findViewById<FrameLayout>(R.id.fullscreen_container).isVisible)
        restored.destroy()
    }

    @Test
    fun t07PausePersistsSessionPointWithoutClearingCookies() {
        val controller = launch()
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setCookie("https://service.example.tv", "sid=secret")

        // Durability point (spec 08 §4.1): onPause() performs the cookie
        // flush contract and never clears cookies (logout-free design).
        controller.pause()

        assertEquals("sid=secret", cookieManager.getCookie("https://service.example.tv"))
    }

}
