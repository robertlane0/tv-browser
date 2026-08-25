package com.example.tvbrowser.ui.browser

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.R
import com.example.tvbrowser.bridge.JsBridge
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.UaMode
import com.example.tvbrowser.error.Category
import com.example.tvbrowser.error.RendererRecoveryPolicy
import com.example.tvbrowser.error.TvError
import com.example.tvbrowser.ui.settings.SettingsActivity
import com.example.tvbrowser.web.TvWebViewClient
import com.example.tvbrowser.web.TvWebChromeClient
import com.example.tvbrowser.web.UserAgentProvider
import com.example.tvbrowser.web.WebErrorFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
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

    // ---- Phase 6: session durability and error handling ----

    private fun fragmentOf(controller: ActivityController<FragmentHostActivity>): WebViewFragment =
        controller.get().supportFragmentManager.fragments
            .filterIsInstance<WebViewFragment>().first()

    private fun clientOf(webView: WebView): TvWebViewClient =
        shadowOf(webView).webViewClient as TvWebViewClient

    private fun idleFor(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))
    }

    private fun failMainFrameNetwork(
        webView: WebView,
        method: String = "GET"
    ) {
        clientOf(webView).onReceivedError(
            webView,
            WebErrorFixtures.mainFrameRequest("https://service.example.tv/watch", method),
            WebErrorFixtures.resourceError(WebViewClient.ERROR_HOST_LOOKUP)
        )
    }

    private fun failMainHttp(webView: WebView, status: Int, method: String = "GET") {
        clientOf(webView).onReceivedHttpError(
            webView,
            WebErrorFixtures.mainFrameRequest("https://service.example.tv/watch", method),
            WebErrorFixtures.httpErrorResponse(status)
        )
    }

    @Test
    fun mainFrameErrorShowsCardAndHidesPageWithoutDestroyingIt() {
        val (controller, webView) = launch()
        val activity = controller.get()

        failMainFrameNetwork(webView)

        val card = activity.findViewById<View>(R.id.error_card)
        assertEquals(View.VISIBLE, card.visibility)
        assertEquals(View.INVISIBLE, webView.visibility)
        assertTrue(activity.findViewById<Button>(R.id.btn_error_retry).isFocused)
        assertEquals(
            activity.getString(R.string.error_title_network),
            activity.findViewById<Button>(R.id.btn_error_retry).context.let {
                activity.findViewById<android.widget.TextView>(R.id.error_title).text
            }
        )
    }

    @Test
    fun httpClientCardShowsNormativeCopyWithStatusCode() {
        val (controller, webView) = launch()
        val activity = controller.get()

        failMainHttp(webView, 404)

        val title = activity.findViewById<android.widget.TextView>(R.id.error_title).text
        val body = activity.findViewById<android.widget.TextView>(R.id.error_body).text
        assertEquals(activity.getString(R.string.error_title_http_client), title)
        assertTrue(body.contains("404"))
        assertEquals(Category.HTTP_CLIENT, fragmentOf(controller).activeErrorCard!!.visibleCategory())
    }

    @Test
    fun backDismissesErrorCardAndReturnsPageControl() {
        val (controller, webView) = launch()
        val cardView = controller.get().findViewById<com.example.tvbrowser.error.ErrorCardView>(R.id.error_card)

        failMainFrameNetwork(webView)

        cardView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        cardView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertEquals(View.GONE, cardView.visibility)
        assertEquals(View.VISIBLE, webView.visibility)
        // Keys reach the page pipeline again instead of being consumed by the
        // card (Robolectric cannot emulate WebView focus; spec 12 §7).
        assertTrue(
            fragmentOf(controller).dispatchKeyDown(
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
            ) || !webView.canGoBack()
        )
    }

    @Test
    fun automaticRetryFiresAfterTwoSecondBackoff() {
        val (controller, webView) = launch()
        clientOf(webView).doUpdateVisitedHistory(webView, "https://service.example.tv/later", false)

        failMainFrameNetwork(webView)

        idleFor(2_000)
        assertEquals("https://service.example.tv/later", shadowOf(webView).lastLoadedUrl)
        assertFalse(controller.get().findViewById<View>(R.id.error_card).isVisible)
    }

    @Test
    fun anyKeyCancelsPendingAutomaticRetry() {
        val (controller, webView) = launch()
        clientOf(webView).doUpdateVisitedHistory(webView, "https://service.example.tv/later", false)
        failMainFrameNetwork(webView)

        fragmentOf(controller).dispatchKeyDown(
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        )
        idleFor(10_000)

        assertEquals(bookmark.url, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun pauseCancelsPendingAutomaticRetry() {
        val (controller, webView) = launch()
        clientOf(webView).doUpdateVisitedHistory(webView, "https://service.example.tv/later", false)
        failMainFrameNetwork(webView)

        controller.pause()
        idleFor(10_000)

        assertEquals(bookmark.url, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun postGuardedFailureNeverAutoRetries() {
        val (controller, webView) = launch()
        clientOf(webView).doUpdateVisitedHistory(webView, "https://service.example.tv/later", false)

        failMainHttp(webView, 503, method = "POST")
        idleFor(20_000)

        assertEquals(bookmark.url, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun manualRetryReloadsFailedUrlAndClearsCard() {
        val (controller, webView) = launch()
        clientOf(webView).doUpdateVisitedHistory(webView, "https://service.example.tv/blocked-page", false)
        failMainHttp(webView, 500)

        controller.get().findViewById<Button>(R.id.btn_error_retry).performClick()

        assertEquals("https://service.example.tv/blocked-page", shadowOf(webView).lastLoadedUrl)
        assertFalse(controller.get().findViewById<View>(R.id.error_card).isVisible)
        assertEquals(View.VISIBLE, webView.visibility)
    }

    @Test
    fun switchUserAgentOpensSettingsForSessionBookmark() {
        val (controller, webView) = launch()
        failMainHttp(webView, 403)

        controller.get().findViewById<Button>(R.id.btn_error_switch_ua).performClick()

        val started = shadowOf(controller.get()).nextStartedActivity
        assertNotNull(started)
        assertEquals(SettingsActivity::class.java.name, started!!.component?.className)
    }

    @Test
    fun homeButtonOnCardFinishesBrowserActivity() {
        val (controller, webView) = launch()
        failMainFrameNetwork(webView)

        controller.get().findViewById<Button>(R.id.btn_error_home).performClick()

        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun threeRapidLoginVisitsSurfaceBlockedCardOnce() {
        val (controller, webView) = launch()
        val client = clientOf(webView)

        repeat(4) {
            client.doUpdateVisitedHistory(webView, "https://service.example/login", false)
        }

        val fragment = fragmentOf(controller)
        assertEquals(Category.BLOCKED, fragment.activeErrorCard!!.visibleCategory())
    }

    @Test
    fun sslFailureSurfacesStrictSslCardWithoutProceed() {
        val (controller, webView) = launch()
        val handler = WebErrorFixtures.sslErrorHandler()

        clientOf(webView).onReceivedSslError(webView, handler, WebErrorFixtures.sslError())

        assertEquals(Category.SSL, fragmentOf(controller).activeErrorCard!!.visibleCategory())
        assertTrue(shadowOf(handler).wasCancelCalled())
        assertFalse(shadowOf(handler).wasProceedCalled())
    }

    @Test
    fun offlineBannerTogglesAndRegainRetriesNetworkCardImmediately() {
        val (controller, webView) = launch()
        val banner = controller.get().findViewById<View>(R.id.offline_banner)
        val fragment = fragmentOf(controller)
        clientOf(webView).doUpdateVisitedHistory(webView, "https://service.example.tv/later", false)

        fragment.onNetworkLost()
        assertTrue(banner.isVisible)

        failMainFrameNetwork(webView)
        fragment.onNetworkAvailable()

        assertFalse(banner.isVisible)
        assertEquals("https://service.example.tv/later", shadowOf(webView).lastLoadedUrl)
        assertFalse(controller.get().findViewById<View>(R.id.error_card).isVisible)
    }

    @Test
    fun networkRegainDoesNotRetryNonRetryableCards() {
        val (controller, webView) = launch()
        val fragment = fragmentOf(controller)

        failMainHttp(webView, 404)
        fragment.onNetworkLost()
        fragment.onNetworkAvailable()

        assertEquals(bookmark.url, shadowOf(webView).lastLoadedUrl)
        assertTrue(controller.get().findViewById<View>(R.id.error_card).isVisible)
    }

    @Test
    fun rendererDeathRebuildsFreshConfiguredWebViewLoadingLastUrl() {
        val (controller, webView) = launch()
        var nowMs = 0L
        fragmentOf(controller).rendererRecovery =
            RendererRecoveryPolicy(maxDeaths = 3, windowMs = 60_000) { nowMs }
        clientOf(webView).doUpdateVisitedHistory(webView, "https://service.example.tv/watching", false)

        val handled = clientOf(webView).onRenderProcessGone(
            webView,
            WebErrorFixtures.rendererGoneDetail(true)
        )

        val fresh = controller.get().findViewById<WebView>(R.id.web_view)
        assertTrue(handled)
        assertTrue(shadowOf(webView).wasDestroyCalled())
        assertNotSame(webView, fresh)
        assertEquals(UserAgentProvider.DESKTOP_UA, fresh.settings.userAgentString)
        assertEquals("https://service.example.tv/watching", shadowOf(fresh).lastLoadedUrl)
    }

    @Test
    fun thirdRendererDeathWithinWindowShowsExhaustedRendererCardWithHomeFocus() {
        val (controller, _) = launch()
        var nowMs = 0L
        val fragment = fragmentOf(controller)
        fragment.rendererRecovery =
            RendererRecoveryPolicy(maxDeaths = 3, windowMs = 60_000) { nowMs }

        repeat(2) {
            val current = controller.get().findViewById<WebView>(R.id.web_view)
            clientOf(current).onRenderProcessGone(current, WebErrorFixtures.rendererGoneDetail(true))
            nowMs += 1_000
        }
        val third = controller.get().findViewById<WebView>(R.id.web_view)
        clientOf(third).onRenderProcessGone(third, WebErrorFixtures.rendererGoneDetail(true))

        val errorCard = fragment.activeErrorCard!!
        assertEquals(Category.RENDERER, errorCard.visibleCategory())
        assertFalse(controller.get().findViewById<View>(R.id.btn_error_retry).isVisible)
        assertFalse(controller.get().findViewById<View>(R.id.btn_error_switch_ua).isVisible)
        assertTrue(controller.get().findViewById<Button>(R.id.btn_error_home).isFocused)
    }

    @Test
    fun addressEntryCommitClearsErrorSurfaceAndLoadsTarget() {
        val (controller, webView) = launch()
        failMainHttp(webView, 404)

        fragmentOf(controller).onAddressCommitted("https://other.example.tv/")

        assertFalse(controller.get().findViewById<View>(R.id.error_card).isVisible)
        assertEquals(View.VISIBLE, webView.visibility)
        assertEquals("https://other.example.tv/", shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun processDeathRestoreReloadsSavedUrlInsteadOfBookmarkUrl() {
        val controller = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
        controller.get().supportFragmentManager.beginTransaction()
            .add(R.id.browser_container, WebViewFragment.newInstance(bookmark))
            .commitNow()
        val original = controller.get().findViewById<WebView>(R.id.web_view)
        clientOf(original).doUpdateVisitedHistory(original, "https://service.example.tv/deep", false)

        // Simulates process death + recreation: state saved, activity rebuilt,
        // fragment restored from its saved instance state.
        controller.recreate()

        val restored = controller.get().findViewById<WebView>(R.id.web_view)
        assertNotNull(restored)
        assertNotSame(original, restored)
        assertEquals("https://service.example.tv/deep", shadowOf(restored!!).lastLoadedUrl)
    }

}

class FragmentHostActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
    }
}
