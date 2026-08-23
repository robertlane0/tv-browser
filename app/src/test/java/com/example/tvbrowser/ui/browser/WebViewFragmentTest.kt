package com.example.tvbrowser.ui.browser

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.R
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.UaMode
import com.example.tvbrowser.web.UserAgentProvider
import org.junit.Assert.assertEquals
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
}

class FragmentHostActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = R.id.browser_container })
    }
}
