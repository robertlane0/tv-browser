package com.example.tvbrowser.input

import android.webkit.WebView

class MediaKeyInjector(private val webView: WebView) {

    fun togglePlayPause() = eval(
        """(function(){var v=document.querySelector('video');if(!v)return;
             v.paused?v.play():v.pause();})();"""
    )

    fun pauseIfPlaying() = eval(
        """(function(){var v=document.querySelector('video');if(!v||v.paused)return;
             v.pause();})();"""
    )

    fun seekBy(deltaMs: Long) = eval(
        """(function(){var v=document.querySelector('video');if(!v)return;
             v.currentTime=Math.max(0,Math.min(v.duration||1e9,
               v.currentTime+($deltaMs/1000)));})();"""
    )

    private fun eval(js: String) {
        if (!webView.isAttachedToWindow) return
        webView.evaluateJavascript(js, null)
    }
}
