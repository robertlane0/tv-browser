package com.example.tvbrowser.ui.browser

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.tvbrowser.R
import com.example.tvbrowser.web.FullscreenController

class BrowserOverlay(
    private val bar: View,
    private val webView: WebView,
    private val fullscreen: FullscreenController?,
    private val isPlaybackActive: () -> Boolean,
    private val isCurrentPageBookmarked: () -> Boolean,
    private val onHomeRequested: () -> Unit,
    private val onAddressClicked: () -> Unit,
    private val onBookmarkToggled: () -> Unit,
    private val onSettingsRequested: () -> Unit
) : BrowserOverlayController {

    private val handler = Handler(Looper.getMainLooper())

    private val backButton = bar.findViewById<ImageButton>(R.id.overlay_back)
    private val forwardButton = bar.findViewById<ImageButton>(R.id.overlay_forward)
    private val refreshButton = bar.findViewById<ImageButton>(R.id.overlay_refresh)
    private val homeButton = bar.findViewById<ImageButton>(R.id.overlay_home)
    private val addressView = bar.findViewById<TextView>(R.id.overlay_address)
    private val securityIcon = bar.findViewById<android.widget.ImageView>(R.id.overlay_security)
    private val bookmarkButton = bar.findViewById<ImageButton>(R.id.overlay_bookmark_toggle)
    private val settingsButton = bar.findViewById<ImageButton>(R.id.overlay_settings)

    override var isVisible: Boolean = false
        private set

    override var isPinned: Boolean = false
        private set

    private val autoHideRunnable = Runnable { hide() }

    init {
        backButton.setOnClickListener {
            webView.goBack()
            refreshNavigationState()
        }
        forwardButton.setOnClickListener {
            webView.goForward()
            refreshNavigationState()
        }
        refreshButton.setOnClickListener { webView.reload() }
        homeButton.setOnClickListener { onHomeRequested() }
        addressView.setOnClickListener {
            setPinned(true)
            onAddressClicked()
        }
        bookmarkButton.setOnClickListener { onBookmarkToggled() }
        settingsButton.setOnClickListener { onSettingsRequested() }
        bar.visibility = View.GONE
    }

    override fun show() {
        if (fullscreen?.isInFullscreen() == true) return
        isVisible = true
        refresh()
        bar.visibility = View.VISIBLE
        focusInitialControl()
        scheduleAutoHide()
    }

    override fun hide() {
        handler.removeCallbacks(autoHideRunnable)
        if (!isVisible) return
        isVisible = false
        webView.requestFocus()
        bar.visibility = View.GONE
    }

    override fun toggle() {
        if (isVisible) hide() else show()
    }

    override fun setPinned(pinned: Boolean) {
        if (isPinned == pinned) return
        isPinned = pinned
        if (pinned) {
            handler.removeCallbacks(autoHideRunnable)
        } else {
            scheduleAutoHide()
        }
    }

    override fun onUserInteraction() {
        if (!isVisible || isPinned) return
        scheduleAutoHide()
    }

    fun refresh() {
        refreshNavigationState()

        val url = webView.url.orEmpty()
        addressView.text = originAndPath(url)

        val cleartext = url.startsWith(CLEARTEXT_SCHEME)
        securityIcon.setImageResource(
            if (cleartext) R.drawable.ic_security_cleartext else R.drawable.ic_security_secure
        )
        securityIcon.setColorFilter(
            ContextCompat.getColor(bar.context, if (cleartext) R.color.security_cleartext else android.R.color.white)
        )

        val bookmarked = isCurrentPageBookmarked()
        bookmarkButton.setImageResource(
            if (bookmarked) R.drawable.ic_bookmark_remove else R.drawable.ic_bookmark_add
        )
        bookmarkButton.contentDescription =
            bar.context.getString(if (bookmarked) R.string.overlay_bookmark_remove else R.string.overlay_bookmark_add)
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun refreshNavigationState() {
        backButton.isEnabled = webView.canGoBack()
        forwardButton.isEnabled = webView.canGoForward()
    }

    private fun focusInitialControl() {
        val order = listOf(backButton, forwardButton, refreshButton, homeButton, addressView, bookmarkButton, settingsButton)
        val target = order.firstOrNull { it.visibility == View.VISIBLE && it.isEnabled && it.isFocusable }
        (target ?: addressView).requestFocus()
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHideRunnable)
        if (!isVisible || isPinned) return
        if (!(isPlaybackActive() || fullscreen?.isInFullscreen() == true)) return
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun originAndPath(url: String): String {
        if (url.isEmpty()) return ""
        val uri = Uri.parse(url)
        val host = uri.host.orEmpty()
        val port = uri.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
        return "${uri.scheme.orEmpty()}://$host$port${uri.path.orEmpty()}"
    }

    companion object {
        const val AUTO_HIDE_DELAY_MS = 3_000L
        private const val CLEARTEXT_SCHEME = "http://"
    }
}
