package com.example.tvbrowser.input

import android.webkit.WebView

class MediaKeyInjector(private val webView: WebView) {

    var isPlayingAssumed: Boolean = false
        private set

    fun togglePlayPause() {
        isPlayingAssumed = !isPlayingAssumed
        eval(pickLargestVideoJs("v.paused?v.play():v.pause();"))
    }

    fun pauseIfPlaying() {
        isPlayingAssumed = false
        eval(pickLargestVideoJs("if(v.paused)return;v.pause();"))
    }

    fun seekBy(deltaMs: Long) {
        isPlayingAssumed = true
        eval(
            pickLargestVideoJs(
                "v.currentTime=Math.max(0,Math.min(v.duration||1e9,v.currentTime+($deltaMs/1000)));"
            )
        )
    }

    /**
     * Returns JS that picks the visible `<video>` with the largest
     * `clientWidth × clientHeight` when multiple exist (spec 10 §5
     * largest-video selector), falling back to the first element when none
     * are visible. Uses `querySelectorAll` so the ad+content case no longer
     * seeks the wrong element.
     */
    private fun pickLargestVideoJs(action: String): String = """(function(){
        var vids=[].slice.call(document.querySelectorAll('video'));
        if(!vids.length)return;
        var vis=vids.filter(function(v){return v.clientWidth>0&&v.clientHeight>0;});
        var pool=vis.length?vis:vids;
        var v=pool.reduce(function(a,b){
          var aw=(a.clientWidth||0)*(a.clientHeight||0);
          var bw=(b.clientWidth||0)*(b.clientHeight||0);
          return bw>aw?b:a;
        });
        if(!v)return;
        $action})();"""

    private fun eval(js: String) {
        if (!webView.isAttachedToWindow) return
        webView.evaluateJavascript(js, null)
    }
}
