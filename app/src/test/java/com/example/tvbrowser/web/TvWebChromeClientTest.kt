package com.example.tvbrowser.web

import android.view.View
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.input.WebViewTestHost
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
class TvWebChromeClientTest {

    private class SilentCallback : WebChromeClient.CustomViewCallback {
        var hideCalls = 0
            private set

        override fun onCustomViewHidden() {
            hideCalls++
        }
    }

    private lateinit var host: WebViewTestHost
    private lateinit var container: FrameLayout
    private lateinit var webView: View
    private lateinit var progressBar: ProgressBar
    private val titles = mutableListOf<String>()
    private lateinit var client: TvWebChromeClient

    @Before
    fun setUp() {
        host = WebViewTestHost()
        val attached = host.attachedWebView()
        webView = attached
        container = FrameLayout(host.activity).also {
            root().addView(it)
            it.visibility = View.GONE
        }
        progressBar = ProgressBar(host.activity, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 100
        progressBar.isVisible = false
        client = TvWebChromeClient(
            host.activity,
            container,
            attached,
            progressBar,
            titleCallback = { titles.add(it) }
        )
    }

    private inner class PlatformCallback : WebChromeClient.CustomViewCallback {
        var hideCalls = 0
            private set

        override fun onCustomViewHidden() {
            hideCalls++
            client.onHideCustomView()
        }
    }

    private fun root(): FrameLayout = host.activity.findViewById(android.R.id.content)

    private fun window() = host.activity.window

    private fun keepScreenOnSet(): Boolean =
        shadowOf(window()).getFlag(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    @Test
    fun showCustomViewAttachesVideoAndHidesPageLayers() {
        val callback = SilentCallback()

        client.onShowCustomView(View(host.activity), callback)

        assertEquals(1, container.childCount)
        assertTrue(container.isVisible)
        assertFalse(webView.isVisible)
        assertTrue(client.isInFullscreen())
        assertEquals(0, callback.hideCalls)
    }

    @Test
    fun showCustomViewEntersImmersiveAndKeepsScreenOn() {
        client.onShowCustomView(View(host.activity), SilentCallback())

        assertTrue(keepScreenOnSet())
        val uiFlags = window().decorView.systemUiVisibility
        assertTrue(uiFlags and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY != 0)
        assertTrue(uiFlags and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
        assertTrue(uiFlags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
    }

    @Test
    fun secondShowCustomViewIsRejectedViaImmediateHiddenCallback() {
        client.onShowCustomView(View(host.activity), SilentCallback())
        val duplicateCallback = SilentCallback()
        val duplicateView = View(host.activity)

        client.onShowCustomView(duplicateView, duplicateCallback)

        assertEquals(1, duplicateCallback.hideCalls)
        assertEquals(1, container.childCount)
        assertNotAttached(duplicateView)
        assertTrue(client.isInFullscreen())
    }

    @Test
    fun exitFullscreenInvokesCallbackExactlyOnceThenWebViewHideCompletesTeardown() {
        val callback = PlatformCallback()
        client.onShowCustomView(View(host.activity), callback)

        client.exitFullscreen()

        assertEquals(1, callback.hideCalls)

        client.onHideCustomView()

        assertEquals("callback must not be invoked twice", 1, callback.hideCalls)
        assertTeardownComplete()
    }

    @Test
    fun exitFullscreenWithoutActiveCustomViewIsNoOp() {
        client.exitFullscreen()

        assertFalse(keepScreenOnSet())
        assertFalse(container.isVisible)
        assertFalse(client.isInFullscreen())
    }

    @Test
    fun webViewInitiatedHideDetachesWithoutInvokingCallback() {
        val callback = SilentCallback()
        client.onShowCustomView(View(host.activity), callback)

        client.onHideCustomView()

        assertEquals(0, callback.hideCalls)
        assertTeardownComplete()
    }

    @Test
    fun hideCustomViewRestoresSystemUiAndClearsKeepScreenOn() {
        client.onShowCustomView(View(host.activity), SilentCallback())

        client.onHideCustomView()

        assertFalse(keepScreenOnSet())
        assertEquals(View.SYSTEM_UI_FLAG_VISIBLE, window().decorView.systemUiVisibility)
    }

    @Test
    fun hideCustomViewWithoutActiveViewIsNoOp() {
        client.onHideCustomView()

        assertFalse(container.isVisible)
        assertEquals(0, container.childCount)
    }

    @Test
    fun forceTeardownDetachesWhenWebViewNeverResponds() {
        val callback = SilentCallback()
        client.onShowCustomView(View(host.activity), callback)

        client.forceTeardown()

        assertTeardownComplete()
        assertEquals("stuck-container guard still notifies the webview once", 1, callback.hideCalls)
        assertFalse(client.isInFullscreen())
    }

    @Test
    fun progressVisibleWhileLoadingAndGoneAtCompletion() {
        client.onProgressChanged(null, 40)
        assertTrue(progressBar.isVisible)
        assertEquals(40, progressBar.progress)

        client.onProgressChanged(null, 100)
        assertFalse(progressBar.isVisible)
        assertEquals(100, progressBar.progress)
    }

    @Test
    fun progressNeverRendersDuringFullscreen() {
        client.onShowCustomView(View(host.activity), SilentCallback())

        client.onProgressChanged(null, 30)

        assertFalse(progressBar.isVisible)
    }

    @Test
    fun receivedTitlePlumbsThroughCallback() {
        client.onReceivedTitle(null, "Example Stream")

        assertEquals(listOf("Example Stream"), titles)
    }

    @Test
    fun blankTitleIsIgnored() {
        client.onReceivedTitle(null, "")
        client.onReceivedTitle(null, null)

        assertTrue(titles.isEmpty())
    }

    private fun assertTeardownComplete() {
        assertFalse(container.isVisible)
        assertEquals(0, container.childCount)
        assertTrue(webView.isVisible)
        assertFalse(keepScreenOnSet())
        assertEquals(View.SYSTEM_UI_FLAG_VISIBLE, window().decorView.systemUiVisibility)
        assertFalse(client.isInFullscreen())
    }

    private fun assertNotAttached(view: View) {
        assertNull(view.parent)
    }
}
