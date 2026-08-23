package com.example.tvbrowser.web

import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.UaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WebViewConfiguratorTest {

    private lateinit var webView: WebView
    private val provider = UserAgentProvider { "WebViewDefault/1.0" }

    private var firstPartyCookiesEnabled = false
    private val thirdPartyCookieTargets = mutableListOf<WebView>()

    private fun configurator(): WebViewConfigurator = WebViewConfigurator(
        provider,
        acceptFirstPartyCookies = { firstPartyCookiesEnabled = true },
        acceptThirdPartyCookies = { thirdPartyCookieTargets.add(it) }
    )

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        webView = WebView(activity)
        configurator().configure(webView, bookmark(UaMode.DESKTOP))
    }

    private fun bookmark(mode: UaMode, zoom: Int = 100) = Bookmark(
        title = "T",
        url = "https://x.tv",
        origin = "https://x.tv",
        uaMode = mode,
        textZoomPercent = zoom
    )

    private fun reconfigureWith(bm: Bookmark) {
        configurator().configure(webView, bm)
    }

    @Test
    fun javaScriptEnabled() {
        assertTrue(webView.settings.javaScriptEnabled)
    }

    @Test
    fun domStorageEnabled() {
        assertTrue(webView.settings.domStorageEnabled)
    }

    @Test
    fun legacyDatabaseEnabled() {
        assertTrue(webView.settings.databaseEnabled)
    }

    @Test
    fun mediaPlaybackNeedsNoUserGesture() {
        assertFalse(webView.settings.mediaPlaybackRequiresUserGesture)
    }

    @Test
    fun wideViewportHonored() {
        assertTrue(webView.settings.useWideViewPort)
    }

    @Test
    fun overviewModeOnFirstPaint() {
        assertTrue(webView.settings.loadWithOverviewMode)
    }

    @Test
    fun fileAccessDisabled() {
        assertFalse(webView.settings.allowFileAccess)
    }

    @Test
    fun contentAccessDisabled() {
        assertFalse(webView.settings.allowContentAccess)
    }

    @Test
    fun mixedContentUsesCompatibilityMode() {
        assertEquals(
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE,
            webView.settings.mixedContentMode
        )
    }

    @Test
    fun multipleWindowsUnsupported() {
        assertFalse(webView.settings.supportMultipleWindows())
    }

    @Test
    fun jsCannotOpenWindowsAutomatically() {
        assertFalse(webView.settings.javaScriptCanOpenWindowsAutomatically)
    }

    @Test
    fun cacheModeIsDefault() {
        assertEquals(WebSettings.LOAD_DEFAULT, webView.settings.cacheMode)
    }

    @Test
    fun textZoomDefaultsToHundred() {
        assertEquals(100, webView.settings.textZoom)
    }

    @Test
    fun textZoomFollowsBookmarkOverride() {
        reconfigureWith(bookmark(UaMode.DESKTOP, zoom = 150))
        assertEquals(150, webView.settings.textZoom)
    }

    @Test
    fun desktopUaApplied() {
        assertEquals(UserAgentProvider.DESKTOP_UA, webView.settings.userAgentString)
    }

    @Test
    fun mobileUaApplied() {
        reconfigureWith(bookmark(UaMode.MOBILE))
        assertEquals(UserAgentProvider.MOBILE_UA, webView.settings.userAgentString)
    }

    @Test
    fun nativeTvUaPassesThrough() {
        reconfigureWith(bookmark(UaMode.NATIVE_TV))
        assertEquals("WebViewDefault/1.0", webView.settings.userAgentString)
    }

    @Test
    fun reconfigurationIsIdempotentAndGuardedCallNeverThrows() {
        reconfigureWith(bookmark(UaMode.MOBILE, zoom = 120))
        assertEquals(UserAgentProvider.MOBILE_UA, webView.settings.userAgentString)
        assertEquals(120, webView.settings.textZoom)
        assertFalse(webView.settings.javaScriptCanOpenWindowsAutomatically)
    }

    @Test
    fun firstPartyCookiesEnabled() {
        assertTrue(firstPartyCookiesEnabled)
    }

    @Test
    fun thirdPartyCookiesEnabledForThisWebView() {
        assertTrue(thirdPartyCookieTargets.contains(webView))
        assertEquals(1, thirdPartyCookieTargets.size)
    }

    @Test
    fun productionDefaultsEnableBothCookieClassesWithoutError() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val fresh = WebView(activity)
        WebViewConfigurator(provider).configure(fresh, bookmark(UaMode.DESKTOP))
        assertTrue(CookieManager.getInstance().acceptCookie())
    }

    @Test
    fun webViewFocusableForDPad() {
        assertTrue(webView.isFocusable)
        assertTrue(webView.isFocusableInTouchMode)
    }

    @Test
    fun hardwareLayerTypeForVideo() {
        assertEquals(View.LAYER_TYPE_HARDWARE, webView.layerType)
    }
}
