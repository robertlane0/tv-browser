package com.example.tvbrowser.web

import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.tvbrowser.data.Bookmark

class WebViewConfigurator(
    private val userAgentProvider: UserAgentProvider,
    private val acceptFirstPartyCookies: () -> Unit = {
        CookieManager.getInstance().setAcceptCookie(true)
    },
    private val acceptThirdPartyCookies: (WebView) -> Unit = {
        CookieManager.getInstance().setAcceptThirdPartyCookies(it, true)
    }
) {

    fun configure(webView: WebView, bookmark: Bookmark) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = bookmark.textZoomPercent
            userAgentString = userAgentProvider.resolve(bookmark)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(this, true)
            }
        }

        acceptFirstPartyCookies()
        acceptThirdPartyCookies(webView)

        WebStorage.getInstance()

        with(webView) {
            isFocusable = true
            isFocusableInTouchMode = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setInitialScale(0)
        }
    }
}
