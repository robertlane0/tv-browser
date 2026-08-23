package com.example.tvbrowser.input

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CssInjectorTest {

    private lateinit var host: WebViewTestHost
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        host = WebViewTestHost()
        webView = host.attachedWebView()
    }

    private fun injector(loader: (String) -> String = { loadBundledAsset(it) }) =
        CssInjector(loader)

    @Test
    fun bundledFocusCssMatchesSpecStyle() {
        val css = loadBundledAsset(CssInjector.FOCUS_CSS_ASSET)

        assertTrue(css.contains("*:focus"))
        assertTrue(css.contains("outline: 3px solid #00BFFF !important"))
        assertTrue(css.contains("box-shadow: 0 0 12px #00BFFF !important"))
    }

    @Test
    fun injectionInstallsGuardedStyleElement() {
        injector().injectFocusHighlight(webView)

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("var id='${CssInjector.GUARD_ELEMENT_ID}'"))
        assertTrue(js.contains("if(!document.getElementById(id))"))
        assertTrue(js.contains("createElement('style')"))
        assertTrue(js.contains("document.head.appendChild(s)"))
    }

    @Test
    fun repeatedInjectionsEmitSameGuardedScriptSoPageStaysIdempotent() {
        val scriptInjector = injector()

        scriptInjector.injectFocusHighlight(webView)
        val first = shadowOf(webView).lastEvaluatedJavascript!!

        scriptInjector.injectFocusHighlight(webView)
        val second = shadowOf(webView).lastEvaluatedJavascript!!

        assertTrue(first == second)
        assertTrue(second.contains("if(!document.getElementById(id))"))
    }

    @Test
    fun cssQuotesBackslashesAndNewlinesAreEscapedIntoScript() {
        injector { "a\"b\\c\nd" }.injectFocusHighlight(webView)

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("s.textContent=\"a\\\"b\\\\c d\""))
        assertTrue(!js.substringAfter("s.textContent=\"").substringBefore("\"").contains("\n"))
    }

    @Test
    fun detachedWebViewSkipsInjectionSilently() {
        val detached = host.detachedWebView()

        injector().injectFocusHighlight(detached)

        assertNull(shadowOf(detached).lastEvaluatedJavascript)
    }

    private fun loadBundledAsset(name: String): String =
        ApplicationProvider.getApplicationContext<Context>()
            .assets.open(name).bufferedReader().use { it.readText() }
}
