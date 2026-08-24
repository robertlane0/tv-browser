package com.example.tvbrowser.web

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.tvbrowser.input.CssInjector

class TvWebViewClient(
    private val cssInjector: CssInjector,
    private val fullscreen: FullscreenController? = null,
    private val onPageStartedInjection: ((WebView) -> Unit)? = null
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        fullscreen?.forceTeardown()
        onPageStartedInjection?.invoke(view)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        cssInjector.injectFocusHighlight(view)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        cssInjector.injectFocusHighlight(view)
    }
}
