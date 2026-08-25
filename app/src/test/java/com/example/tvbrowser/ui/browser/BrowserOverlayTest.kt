package com.example.tvbrowser.ui.browser

import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.R
import com.example.tvbrowser.web.FullscreenController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BrowserOverlayTest {

    private class RecordingFocusWebView(context: android.content.Context) : WebView(context) {
        var focusRequests = 0
            private set

        override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean {
            focusRequests++
            return super.requestFocus(direction, previouslyFocusedRect)
        }
    }

    private class FakeFullscreen(var active: Boolean = false) : FullscreenController {
        override fun isInFullscreen(): Boolean = active
        override fun exitFullscreen() {
            active = false
        }

        override fun forceTeardown() {
            active = false
        }
    }

    private lateinit var activity: FragmentActivity
    private lateinit var webView: RecordingFocusWebView
    private lateinit var overlay: BrowserOverlay
    private lateinit var fullscreen: FakeFullscreen
    private var playing = false
    private var homeRequests = 0
    private var addressClicks = 0
    private var bookmarkToggles = 0
    private var settingsClicks = 0
    private var currentPageBookmarked = false

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val root = activity.findViewById<View>(android.R.id.content) as android.view.ViewGroup
        val bar = activity.layoutInflater.inflate(R.layout.view_browser_overlay, root, false)
        root.addView(bar)
        webView = RecordingFocusWebView(activity)
        root.addView(webView, 0)

        fullscreen = FakeFullscreen()
        overlay = BrowserOverlay(            bar = bar,
            webView = webView,
            fullscreen = fullscreen,
            isPlaybackActive = { playing },
            isCurrentPageBookmarked = { currentPageBookmarked },
            onHomeRequested = { homeRequests++ },
            onAddressClicked = { addressClicks++ },
            onBookmarkToggled = { bookmarkToggles++; currentPageBookmarked = !currentPageBookmarked },
            onSettingsRequested = { settingsClicks++ }
        )
    }

    private fun bar(): View = activity.findViewById(R.id.browser_overlay)

    private fun backButton(): ImageButton = activity.findViewById(R.id.overlay_back)

    private fun addressView(): TextView = activity.findViewById(R.id.overlay_address)

    private fun idle() {
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
    }

    private fun historyEntry(url: String) {
        shadowOf(webView).pushEntryToHistory(url)
    }

    @Test
    fun showMakesBarVisibleAndFocusesBackButton() {
        historyEntry("https://service.example.tv/a")
        historyEntry("https://service.example.tv/b")
        overlay.show()

        assertTrue(overlay.isVisible)
        assertEquals(View.VISIBLE, bar().visibility)
        assertTrue("first enabled control must take focus", backButton().isFocused)
    }

    @Test
    fun showSkipsDisabledControlsForInitialFocus() {
        overlay.show()

        assertFalse(backButton().isEnabled)
        assertFalse("disabled Back cannot hold focus", backButton().isFocused)
        assertTrue(
            "focus must land on an enabled control",
            activity.findViewById<View>(R.id.overlay_refresh).isFocused
        )
    }

    @Test
    fun hideIsImmediateAndReturnsFocusToWebViewFirst() {
        overlay.show()

        overlay.hide()

        assertFalse(overlay.isVisible)
        assertEquals(View.GONE, bar().visibility)
        assertTrue("focus must return to the WebView", webView.focusRequests >= 1)
    }

    @Test
    fun toggleFlipsVisibility() {
        overlay.toggle()
        assertTrue(overlay.isVisible)

        overlay.toggle()
        assertFalse(overlay.isVisible)
        assertTrue(webView.focusRequests >= 1)
    }

    @Test
    fun unreachableWhileFullscreenCustomViewAttached() {
        fullscreen.active = true

        overlay.show()
        overlay.toggle()

        assertFalse("overlay must not open over fullscreen", overlay.isVisible)
        assertEquals(View.GONE, bar().visibility)
    }

    @Test
    fun autoHidesAfterThreeSecondsOnlyDuringPlayback() {
        playing = true
        overlay.show()

        idle()

        assertFalse("overlay must auto-hide during playback", overlay.isVisible)
        assertTrue("auto-hide must return focus to the WebView", webView.focusRequests >= 1)
    }

    @Test
    fun overlayStaysUntilDismissedDuringOrdinaryBrowsing() {
        playing = false
        overlay.show()

        idle()

        assertTrue("no timer race while reading", overlay.isVisible)
    }

    @Test
    fun pinnedOverlaySuspendsAutoHideUntilUnpinned() {
        playing = true
        overlay.show()
        overlay.setPinned(true)

        idle()

        assertTrue("text entry must pin the overlay", overlay.isVisible)
        assertTrue(overlay.isPinned)

        overlay.setPinned(false)
        idle()

        assertFalse(overlay.isVisible)
    }

    @Test
    fun destroyRemovesPendingCallbacks() {
        playing = true
        overlay.show()

        overlay.destroy()
        idle()

        assertTrue("destroy must cancel the auto-hide runnable", overlay.isVisible)
    }

    @Test
    fun navigationButtonsReflectWebHistoryAvailability() {
        overlay.show()

        assertFalse(backButton().isEnabled)

        historyEntry("https://service.example.tv/a")
        historyEntry("https://service.example.tv/b")
        overlay.refresh()

        assertTrue(backButton().isEnabled)
    }

    @Test
    fun addressClickOpensEntryAndPinsOverlay() {
        overlay.show()

        addressView().performClick()

        assertEquals(1, addressClicks)
        assertTrue(overlay.isPinned)
    }

    @Test
    fun bookmarkToggleInvokesCallbackAndSwapsStarIcon() {
        overlay.show()
        val starButton = activity.findViewById<ImageButton>(R.id.overlay_bookmark_toggle)
        val addIcon = starButton.drawable

        starButton.performClick()
        overlay.refresh()

        assertEquals(1, bookmarkToggles)
        assertNotEquals("icon must swap between outline and filled star", addIcon, starButton.drawable)
    }

    @Test
    fun homeAndSettingsButtonsInvokeCallbacks() {
        overlay.show()

        activity.findViewById<ImageButton>(R.id.overlay_home).performClick()
        activity.findViewById<ImageButton>(R.id.overlay_settings).performClick()

        assertEquals(1, homeRequests)
        assertEquals(1, settingsClicks)
    }

    @Test
    fun addressShowsOriginAndPathWithoutQuery() {
        webView.loadUrl("https://service.example.tv/watch?token=secret#frag")

        overlay.show()

        val displayed = addressView().text.toString()
        assertTrue(displayed.startsWith("https://service.example.tv/watch"))
        assertFalse("query strings must not leak into the overlay", displayed.contains("token"))
    }

    @Test
    fun cleartextPageShowsDistinctInsecurePadlockIndicator() {
        webView.loadUrl("http://cleartext.example/video")
        overlay.show()
        val cleartextIcon = activity.findViewById<android.widget.ImageView>(R.id.overlay_security).drawable

        webView.loadUrl("https://secure.example/video")
        overlay.refresh()
        val secureIcon = activity.findViewById<android.widget.ImageView>(R.id.overlay_security).drawable

        assertNotEquals(cleartextIcon, secureIcon)
    }

    @Test
    fun interactionWhileBrowsingNeverSchedulesHide() {
        playing = false
        overlay.show()

        overlay.onUserInteraction()
        idle()

        assertTrue(overlay.isVisible)
    }
}
