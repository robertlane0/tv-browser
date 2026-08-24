package com.example.tvbrowser.ui.browser

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.R
import com.example.tvbrowser.bridge.JsBridge
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.UaMode
import com.example.tvbrowser.web.TvWebViewClient
import com.example.tvbrowser.web.TvWebChromeClient
import com.example.tvbrowser.web.UserAgentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WebViewFragmentTest {

    private val bookmark = Bookmark(
        title = "Service",
        url = "https://service.example.tv/watch",
        origin = "https://service.example.tv",
        uaMode = UaMode.DESKTOP,
        textZoomPercent = 100
    )

    private fun launch(bm: Bookmark = bookmark): Pair<ActivityController<FragmentHostActivity>, WebView> {
        val controller = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
        val fragment = WebViewFragment.newInstance(bm)
        controller.get().supportFragmentManager.beginTransaction()
            .add(R.id.browser_container, fragment)
            .commitNow()
        val webView = controller.get().findViewById<WebView>(R.id.web_view)
        assertNotNull(webView)
        return controller to webView!!
    }

    @Test
    fun hostsSingleConfiguredWebViewWithDesktopUa() {
        val (_, webView) = launch()

        assertEquals(UserAgentProvider.DESKTOP_UA, webView.settings.userAgentString)
        assertTrue(webView.isFocusable)
    }

    @Test
    fun loadsBookmarkUrlAfterConfiguration() {
        val (_, webView) = launch()

        assertEquals(bookmark.url, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun pauseResumeDelegatesToWebView() {
        val (controller, webView) = launch()
        val shadow = shadowOf(webView)

        controller.pause()
        assertTrue(shadow.wasOnPauseCalled())

        controller.resume()
        assertTrue(shadow.wasOnResumeCalled())
    }

    @Test
    fun destroyTearsDownDetachedWebView() {
        val (controller, webView) = launch()

        controller.destroy()

        assertTrue(shadowOf(webView).wasDestroyCalled())
        assertEquals(null, webView.parent)
    }

    @Test
    fun mobileUaBookmarkResolvesMobileTemplate() {
        val (_, webView) = launch(bookmark.copy(uaMode = UaMode.MOBILE))

        assertEquals(UserAgentProvider.MOBILE_UA, webView.settings.userAgentString)
        assertEquals(bookmark.url, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun missingBookmarkArgumentsFinishesHostWithoutCrash() {
        val controller = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
        controller.get().supportFragmentManager.beginTransaction()
            .add(R.id.browser_container, WebViewFragment())
            .commitNow()

        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun installsTvWebViewClientForFocusInjection() {
        val (_, webView) = launch()

        assertTrue(shadowOf(webView).webViewClient is TvWebViewClient)
    }

    @Test
    fun mediaPlayPauseDispatchInjectsVideoToggleJs() {
        val (controller, webView) = launch()

        val handled = controller.get().supportFragmentManager.fragments
            .filterIsInstance<WebViewFragment>().first()
            .dispatchKeyDown(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )

        assertTrue(handled)
        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("v.paused?v.play():v.pause()"))
    }

    @Test
    fun dpadKeysFallThroughFragmentDispatch() {
        val (controller, _) = launch()

        assertFalse(
            controller.get().supportFragmentManager.fragments
                .filterIsInstance<WebViewFragment>().first()
                .dispatchKeyDown(
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
                )
        )
    }

    @Test
    fun keyEventDispatchRoutesMediaKeyDownToInjector() {
        val (controller, webView) = launch()

        val handled = controller.get().supportFragmentManager.fragments
            .filterIsInstance<WebViewFragment>().first()
            .dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )

        assertTrue(handled)
        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("v.paused?v.play():v.pause()"))
    }

    @Test
    fun keyEventDispatchConsumesMediaKeyUpWithoutInjecting() {
        val (controller, webView) = launch()

        val handled = controller.get().supportFragmentManager.fragments
            .filterIsInstance<WebViewFragment>().first()
            .dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )

        assertTrue(handled)
        assertEquals(null, shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun keyEventDispatchFallsThroughForNonMediaKeys() {
        val (controller, webView) = launch()

        intArrayOf(
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU
        ).forEach { keyCode ->
            assertFalse(
                "key $keyCode must fall through keyEvent dispatch",
                controller.get().supportFragmentManager.fragments
                    .filterIsInstance<WebViewFragment>().first()
                    .dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            )
        }
        assertEquals(null, shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun installsTvWebChromeClientForFullscreenHandling() {
        val (_, webView) = launch()

        assertTrue(shadowOf(webView).webChromeClient is TvWebChromeClient)
    }

    @Test
    fun backKeyExitsFullscreenBeforeHistoryThroughFragment() {
        val (controller, webView) = launch()
        val fragment = controller.get().supportFragmentManager.fragments
            .filterIsInstance<WebViewFragment>().first()
        shadowOf(webView).pushEntryToHistory("https://service.example.tv/first")
        val chrome = fragment.activeChromeClient!!
        chrome.onShowCustomView(
            View(controller.get()),
            object : WebChromeClient.CustomViewCallback {
                override fun onCustomViewHidden() {
                    chrome.onHideCustomView()
                }
            }
        )

        assertTrue(
            fragment.dispatchKeyDown(
                KeyEvent.KEYCODE_BACK,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
            )
        )

        assertFalse(
            controller.get().findViewById<View>(R.id.fullscreen_container).isVisible
        )
        assertEquals(0, shadowOf(webView).goBackInvocations)
    }

    @Test
    fun emeDrmCardStartsHiddenShowsOnHookAndDismissesWithBack() {
        val (controller, _) = launch()
        val activity = controller.get()
        val fragment = activity.supportFragmentManager.fragments
            .filterIsInstance<WebViewFragment>().first()
        val card = activity.findViewById<View>(R.id.drm_error_card)
        assertEquals(View.GONE, card.visibility)

        fragment.activeDrmCard!!.show()

        assertEquals(View.VISIBLE, card.visibility)
        assertTrue(card.isFocused)

        card.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        card.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertEquals(View.GONE, card.visibility)
        assertFalse(
            "focus must leave the dismissed DRM card",
            card.isFocused
        )
    }

    @Test
    fun registersTvBrowserJavascriptInterfaceForEmeReports() {
        val (_, webView) = launch()

        assertTrue(
            shadowOf(webView).getJavascriptInterface(JsBridge.JS_INTERFACE_NAME) is JsBridge
        )
    }

}

class FragmentHostActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
    }
}
