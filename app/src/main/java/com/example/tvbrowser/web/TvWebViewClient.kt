package com.example.tvbrowser.web

import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.tvbrowser.input.CssInjector

class TvWebViewClient(private val cssInjector: CssInjector) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        cssInjector.injectFocusHighlight(view)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        cssInjector.injectFocusHighlight(view)
    }
}
