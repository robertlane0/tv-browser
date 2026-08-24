package com.example.tvbrowser.web

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.isVisible
import com.example.tvbrowser.input.AudioFocusController

class TvWebChromeClient(
    private val activity: Activity,
    private val fullscreenContainer: FrameLayout,
    private val webView: WebView,
    private val progressBar: ProgressBar,
    private val titleCallback: (String) -> Unit,
    private val audioFocus: AudioFocusController? = null
) : WebChromeClient(), FullscreenController {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback

        webView.visibility = View.GONE
        progressBar.isVisible = false
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenContainer.visibility = View.VISIBLE

        activity.window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            decorView.systemUiVisibility = IMMERSIVE_FLAGS
        }
        audioFocus?.request()
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        customView = null
        customViewCallback = null

        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE

        activity.window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        audioFocus?.abandon()
    }

    override fun isInFullscreen(): Boolean = customView != null

    override fun exitFullscreen() {
        if (!isInFullscreen()) return
        val callback = customViewCallback ?: run {
            detachFullscreenView()
            return
        }
        customViewCallback = null
        callback.onCustomViewHidden()
    }

    override fun forceTeardown() {
        if (!isInFullscreen()) return
        customViewCallback?.onCustomViewHidden()
        detachFullscreenView()
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        if (isInFullscreen()) return
        progressBar.progress = newProgress
        progressBar.isVisible = newProgress < 100
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrEmpty()) titleCallback(title)
    }

    private fun detachFullscreenView() {
        val view = customView ?: return
        customView = null
        customViewCallback = null
        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        activity.window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        audioFocus?.abandon()
    }

    private companion object {
        val IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}
