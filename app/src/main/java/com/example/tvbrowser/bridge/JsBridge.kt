package com.example.tvbrowser.bridge

import android.webkit.JavascriptInterface

class JsBridge {

    @JavascriptInterface
    fun onMediaKey(keyCode: Int) {
    }
}
