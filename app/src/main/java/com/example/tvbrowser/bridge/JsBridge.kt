package com.example.tvbrowser.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class JsBridge(private val drmErrorListener: (String) -> Unit = {}) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMediaKey(keyCode: Int) {
    }

    @JavascriptInterface
    fun onDrmError(message: String?) {
        val detail = message ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchDrmError(detail)
        } else {
            mainHandler.post { dispatchDrmError(detail) }
        }
    }

    private fun dispatchDrmError(detail: String) {
        drmErrorListener(detail)
    }

    companion object {
        const val JS_INTERFACE_NAME = "TvBrowser"
    }
}
