package com.example.tvbrowser.input

import android.webkit.WebView

class CssInjector(private val assetLoader: (String) -> String) {

    fun injectFocusHighlight(webView: WebView) {
        if (!webView.isAttachedToWindow) return
        val css = assetLoader(FOCUS_CSS_ASSET)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
        webView.evaluateJavascript(
            """(function(){
                 var id='$GUARD_ELEMENT_ID';
                 if(!document.getElementById(id)){
                   var s=document.createElement('style');
                   s.id=id; s.textContent="$css";
                   document.head.appendChild(s);
                 }
               })();""", null
        )
    }

    companion object {
        const val FOCUS_CSS_ASSET = "tv_focus.css"
        const val GUARD_ELEMENT_ID = "tv-focus-style"
    }
}
