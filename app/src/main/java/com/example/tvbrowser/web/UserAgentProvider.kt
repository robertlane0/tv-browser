package com.example.tvbrowser.web

import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.UaMode

class UserAgentProvider(private val webViewDefault: () -> String) {

    fun resolve(bookmark: Bookmark): String = when (bookmark.uaMode) {
        UaMode.DESKTOP -> DESKTOP_UA
        UaMode.MOBILE -> MOBILE_UA
        UaMode.NATIVE_TV -> webViewDefault()
    }

    companion object {
        private const val CHROME_MAJOR = 126
        val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$CHROME_MAJOR.0.0.0 Safari/537.36"
        val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$CHROME_MAJOR.0.0.0 Mobile Safari/537.36"
    }
}
