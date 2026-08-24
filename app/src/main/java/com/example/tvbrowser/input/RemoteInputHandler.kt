package com.example.tvbrowser.input

import android.view.KeyEvent
import android.webkit.WebView
import com.example.tvbrowser.ui.browser.BrowserOverlayController
import com.example.tvbrowser.web.FullscreenController

class RemoteInputHandler(
    private val webView: WebView,
    private val overlay: BrowserOverlayController,
    private val mediaKeys: MediaKeyInjector,
    private val onExit: () -> Unit,
    private val fullscreen: FullscreenController? = null
) {

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> when {
            fullscreen?.isInFullscreen() == true -> {
                fullscreen.exitFullscreen()
                webView.requestFocus()
                true
            }
            overlay.isVisible -> {
                overlay.hide()
                webView.requestFocus()
                true
            }
            webView.canGoBack() -> {
                webView.goBack()
                true
            }
            else -> {
                onExit()
                true
            }
        }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> consumeUnrepeated(event) { mediaKeys.togglePlayPause() }
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> consumeUnrepeated(event) { mediaKeys.seekBy(SEEK_STEP_MS) }
        KeyEvent.KEYCODE_MEDIA_REWIND -> consumeUnrepeated(event) { mediaKeys.seekBy(-SEEK_STEP_MS) }
        KeyEvent.KEYCODE_MENU -> {
            overlay.toggle()
            if (!overlay.isVisible) webView.requestFocus()
            true
        }
        else -> false
    }

    private inline fun consumeUnrepeated(event: KeyEvent, action: () -> Unit): Boolean {
        if (event.repeatCount > 0) return true
        action()
        return true
    }

    fun isMediaKey(keyCode: Int): Boolean = keyCode in MEDIA_KEYS

    companion object {
        const val SEEK_STEP_MS = 10_000L

        val MEDIA_KEYS = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND
        )
    }
}
