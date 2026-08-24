package com.example.tvbrowser.web

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.input.WebViewTestHost
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EmeErrorHookTest {

    private lateinit var host: WebViewTestHost
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        host = WebViewTestHost()
        webView = host.attachedWebView()
    }

    @Test
    fun scriptInstallsOncePerDocumentAndReportsThroughBridge() {
        assertTrue(EmeErrorHook.SCRIPT.contains(EmeErrorHook.GUARD_PROPERTY))
        assertTrue(EmeErrorHook.SCRIPT.contains("if(window.${EmeErrorHook.GUARD_PROPERTY})return"))
        assertTrue(EmeErrorHook.SCRIPT.contains("TvBrowser.onDrmError"))
        assertTrue(EmeErrorHook.SCRIPT.contains("requestMediaKeySystemAccess"))
        assertTrue(EmeErrorHook.SCRIPT.contains("setMediaKeys"))
        assertTrue("hook must rethrow so site logic sees the original failure",
            EmeErrorHook.SCRIPT.contains("throw err"))
    }

    @Test
    fun scriptStubsMissingEmeSoDrmSitesStillSurfaceTheCard() {
        assertTrue(EmeErrorHook.SCRIPT.contains("if(!navigator.requestMediaKeySystemAccess)"))
        assertTrue(EmeErrorHook.SCRIPT.contains("EME unavailable in this WebView build"))
        assertTrue("stub must reject with a standard NotSupportedError",
            EmeErrorHook.SCRIPT.contains("new DOMException('EME unavailable','NotSupportedError')"))
    }

    @Test
    fun injectionModeDecidesFallbackBehavior() {
        val hook = EmeErrorHook()
        hook.attach(webView)
        hook.injectIfNeeded(webView)

        val evaluated = shadowOf(webView).lastEvaluatedJavascript
        if (hook.usesDocumentStart()) {
            assertNull("document-start path must not double-inject", evaluated)
        } else {
            assertTrue(evaluated!!.contains(EmeErrorHook.GUARD_PROPERTY))
        }
    }

    @Test
    fun fallbackSkipsDetachedWebView() {
        val hook = EmeErrorHook()
        val detached = host.detachedWebView()
        hook.attach(detached)

        hook.injectIfNeeded(detached)

        assertNull(shadowOf(detached).lastEvaluatedJavascript)
    }
}
