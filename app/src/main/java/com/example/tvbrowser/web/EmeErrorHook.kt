package com.example.tvbrowser.web

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class EmeErrorHook {

    private var documentStartSupported = false

    fun attach(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentStartSupported = true
            WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, setOf("*"))
        } else {
            documentStartSupported = false
        }
    }

    fun injectIfNeeded(view: WebView) {
        if (!documentStartSupported && view.isAttachedToWindow) {
            view.evaluateJavascript(SCRIPT, null)
        }
    }

    internal fun usesDocumentStart(): Boolean = documentStartSupported

    companion object {
        const val GUARD_PROPERTY = "__tvBrowserEmeHookInstalled"

        val SCRIPT = """
            (function(){
              if(window.$GUARD_PROPERTY)return;
              window.$GUARD_PROPERTY=true;
              function report(name,message){
                try{
                  if(window.TvBrowser&&window.TvBrowser.onDrmError){
                    window.TvBrowser.onDrmError(name+': '+message);
                  }
                }catch(e){}
              }
              if(!navigator.requestMediaKeySystemAccess){
                navigator.requestMediaKeySystemAccess=function(){
                  report('NotSupportedError','EME unavailable in this WebView build');
                  return Promise.reject(new DOMException('EME unavailable','NotSupportedError'));
                };
              } else {
                var origRequest=navigator.requestMediaKeySystemAccess.bind(navigator);
                navigator.requestMediaKeySystemAccess=function(){
                  try{
                    var p=origRequest.apply(null,arguments);
                    if(p&&typeof p.catch==='function'){
                      return p.catch(function(err){
                        report(err&&err.name||'NotSupportedError',err&&err.message||'requestMediaKeySystemAccess rejected');
                        throw err;
                      });
                    }
                    return p;
                  }catch(e){report(e.name,e.message);throw e;}
                };
              }
              var origSetMediaKeys=HTMLMediaElement.prototype.setMediaKeys;
              if(origSetMediaKeys){
                HTMLMediaElement.prototype.setMediaKeys=function(mediaKeys){
                  try{
                    var p=origSetMediaKeys.apply(this,arguments);
                    if(p&&typeof p.catch==='function'){
                      return p.catch(function(err){
                        report(err&&err.name||'NotSupportedError',err&&err.message||'setMediaKeys failed');
                        throw err;
                      });
                    }
                    return p;
                  }catch(e){report(e.name,e.message);throw e;}
                };
              }
            })();
        """.trimIndent()
    }
}
