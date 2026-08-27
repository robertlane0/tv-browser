package com.example.tvbrowser.filter

import android.webkit.WebView

/**
 * Optional cosmetic cleanup layer (spec 10 §5).
 *
 * Hides unclosable modal overlays via `display:none !important` and watches
 * for SPA-injected pop-ups with a throttled MutationObserver (500 ms).
 * Opt-in only (`isEnabled` gate), guard-flag idempotent, CSP-safe (fail
 * silently), hide-never-remove. Disabled ⇒ zero DOM side effects.
 *
 * Additional site-specific patches hosted in the same pipeline:
 *  - Largest-video selector upgrade lives in [com.example.tvbrowser.input.MediaKeyInjector].
 *  - Keydown pass-through for D-Pad keys swallowed by page `preventDefault`.
 *  - Wrapper-fullscreen logger detecting fullscreen-on-div.
 */
class CleanupInjector(
    private val registry: CleanupRegistry,
    private val isEnabled: () -> Boolean
) {

    /**
     * Inject hide logic for [origin]. No-op when the feature is off, when the
     * WebView is detached, or when no selectors apply.
     */
    fun inject(webView: WebView, origin: String) {
        if (!isEnabled()) return
        if (!webView.isAttachedToWindow) return
        val hideSelectors = registryHideSelectors(origin)
        val closeSelectors = registryCloseSelectors(origin)
        if (hideSelectors.isEmpty() && closeSelectors.isEmpty()) return

        val hideArray = hideSelectors.joinToString(",") { "\"${escapeForJs(it)}\"" }
        val closeArray = closeSelectors.joinToString(",") { "\"${escapeForJs(it)}\"" }

        val js = buildString {
            append("(function(){")
            append("if(window.$GUARD)return;")
            append("window.$GUARD=true;")
            if (hideSelectors.isNotEmpty()) {
                append("var hideSel=[$hideArray];")
            } else {
                append("var hideSel=[];")
            }
            if (closeSelectors.isNotEmpty()) {
                append("var closeSel=[$closeArray];")
            } else {
                append("var closeSel=[];")
            }
            append("function hide(){")
            append("hideSel.forEach(function(s){")
            append("try{")
            append("document.querySelectorAll(s).forEach(function(el){")
            append("try{el.style.setProperty('display','none','important');}catch(e){}")
            append("});")
            append("}catch(e){}")
            append("});")
            append("}")
            // Click close buttons after hiding — sites that expect a click to clear state.
            append("function clickClose(){")
            append("closeSel.forEach(function(s){")
            append("try{")
            append("document.querySelectorAll(s).forEach(function(el){try{el.click();}catch(e){}});")
            append("}catch(e){}")
            append("});")
            append("}")
            append("function run(){try{hide();}catch(e){}try{clickClose();}catch(e){}}")
            append("try{run();}catch(e){}")
            append("var scheduled=false;")
            append("var throttled=function(){")
            append("if(scheduled)return;")
            append("scheduled=true;")
            append("setTimeout(function(){scheduled=false;try{run();}catch(e){}},500);")
            append("};")
            append("try{")
            append("new MutationObserver(throttled).observe(document.documentElement,{childList:true,subtree:true});")
            append("}catch(e){}")
            append("})();")
        }
        webView.evaluateJavascript(js, null)
    }

    /**
     * Site-specific removal of `preventDefault` handlers that swallow D-Pad
     * keys (spec 05 §8). MUST never be applied globally — only for origins
     * in the allowlist (spec 10 §5).
     */
    fun injectKeydownPassThrough(webView: WebView, origin: String) {
        if (!KEYDOWN_PATCH_ORIGINS.contains(origin)) return
        if (!isEnabled()) return
        if (!webView.isAttachedToWindow) return
        val js = """(function(){
            if(window.$GUARD_KEYDOWN)return;
            window.$GUARD_KEYDOWN=true;
            try{
              var origPrevent=Event.prototype.preventDefault;
              Event.prototype.preventDefault=function(){
                try{
                  var kc=this.keyCode||this.which||0;
                  // D-Pad keyCodes 19-22, CENTER 23, ENTER 66
                  if(kc>=19&&kc<=23) return;
                }catch(e){}
                return origPrevent.apply(this,arguments);
              };
            }catch(e){}
        })();"""
        webView.evaluateJavascript(js, null)
    }

    /**
     * Detects fullscreen-on-div pattern and logs domain for registry
     * follow-up (spec 06 §6, 10 §5). Gated by the opt-in switch so that
     * "feature off ⇒ zero DOM side effects" holds for all injections.
     */
    fun injectWrapperFullscreenLogger(webView: WebView) {
        if (!isEnabled()) return
        if (!webView.isAttachedToWindow) return
        val js = """(function(){
            if(window.$GUARD_FULLSCREEN_LOGGER)return;
            window.$GUARD_FULLSCREEN_LOGGER=true;
            try{
              document.addEventListener('fullscreenchange',function(){
                try{
                  var el=document.fullscreenElement;
                  if(el&&el.tagName!=='VIDEO'){
                    console.log('[TV Browser] wrapper fullscreen on div detected: '+location.origin+' tag='+el.tagName);
                  }
                }catch(e){}
              });
            }catch(e){}
        })();"""
        webView.evaluateJavascript(js, null)
    }

    private fun registryHideSelectors(origin: String): List<String> {
        val generic = registry.generic.hideSelectors
        val site = registry.siteEntries.find { it.origin == origin }
        return if (site != null) generic + site.hideSelectors else generic
    }

    private fun registryCloseSelectors(origin: String): List<String> {
        val generic = registry.generic.closeButtonSelectors
        val site = registry.siteEntries.find { it.origin == origin }
        return if (site != null) generic + site.closeButtonSelectors else generic
    }

    private fun escapeForJs(raw: String): String =
        raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")

    companion object {
        const val GUARD = "__tvCleanup"
        const val GUARD_KEYDOWN = "__tvKeydownPatched"
        const val GUARD_FULLSCREEN_LOGGER = "__tvFullscreenLogger"

        /** Origins that need the keydown `preventDefault` patch. Empty in v1; populated per registry review. */
        val KEYDOWN_PATCH_ORIGINS: Set<String> = emptySet()
    }
}
