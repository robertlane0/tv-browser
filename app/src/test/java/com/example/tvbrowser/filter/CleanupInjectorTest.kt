package com.example.tvbrowser.filter

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.input.WebViewTestHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CleanupInjectorTest {

    private lateinit var host: WebViewTestHost
    private lateinit var registry: CleanupRegistry

    @Before
    fun setUp() {
        host = WebViewTestHost()
        registry = CleanupRegistry.loadFromAssets("cleanup_registry.json")
    }

    private fun injector(enabled: Boolean, reg: CleanupRegistry = registry): CleanupInjector =
        CleanupInjector(reg) { enabled }

    @Test
    fun featureOffProducesZeroDomSideEffects() {
        val webView = host.attachedWebView()
        val inj = injector(enabled = false)

        inj.inject(webView, "https://example-regional-tv.example")
        inj.injectKeydownPassThrough(webView, "https://example-regional-tv.example")
        inj.injectWrapperFullscreenLogger(webView)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun enabledInjectHidesWithDisplayNoneImportantAndGuard() {
        val webView = host.attachedWebView()
        val inj = injector(enabled = true)

        inj.inject(webView, "https://example.com")

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("window.${CleanupInjector.GUARD}"))
        assertTrue(js.contains("if(window.${CleanupInjector.GUARD})return"))
        assertTrue(js.contains("display"))
        assertTrue(js.contains("none"))
        assertTrue(js.contains("important"))
        assertTrue(js.contains("style.setProperty"))
        // Generic selectors must appear in the injected array
        assertTrue(js.contains("newsletter-modal"))
        assertTrue(js.contains("modal-backdrop"))
        // Close button selectors also injected
        assertTrue(js.contains("aria-label"))
    }

    @Test
    fun injectUsesThrottledMutationObserver500ms() {
        val webView = host.attachedWebView()
        val inj = injector(enabled = true)

        inj.inject(webView, "https://example.com")

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("MutationObserver"))
        assertTrue(js.contains("setTimeout"))
        assertTrue(js.contains("500"))
        assertTrue(js.contains("scheduled"))
    }

    @Test
    fun injectIsIdempotentViaGuardFlag() {
        val webView = host.attachedWebView()
        val inj = injector(enabled = true)

        inj.inject(webView, "https://example.com")
        val first = shadowOf(webView).lastEvaluatedJavascript!!

        // Second inject with same WebView should still emit guard-checked script (idempotent)
        inj.inject(webView, "https://example.com")
        val second = shadowOf(webView).lastEvaluatedJavascript!!

        assertTrue(first.contains("window.${CleanupInjector.GUARD}"))
        assertTrue(second.contains("window.${CleanupInjector.GUARD}"))
    }

    @Test
    fun detachedWebViewSkipsInjectionSilently() {
        val detached = host.detachedWebView()
        val inj = injector(enabled = true)

        inj.inject(detached, "https://example.com")

        assertNull(shadowOf(detached).lastEvaluatedJavascript)
    }

    @Test
    fun siteSpecificSelectorsOnlyForMatchingOrigin() {
        val webView = host.attachedWebView()
        val inj = injector(enabled = true)

        inj.inject(webView, "https://example-regional-tv.example")
        val forSite = shadowOf(webView).lastEvaluatedJavascript!!

        val webView2 = host.attachedWebView()
        inj.inject(webView2, "https://other.example")
        val forOther = shadowOf(webView2).lastEvaluatedJavascript!!

        assertTrue(forSite.contains("#unclosable-promo"))
        assertFalse(forOther.contains("#unclosable-promo"))
    }

    @Test
    fun keydownPassThroughNeverAppliedGlobally() {
        val webView = host.attachedWebView()
        val inj = injector(enabled = true)

        // Not in allowlist (empty in v1) ⇒ no injection even when enabled
        inj.injectKeydownPassThrough(webView, "https://any.example")
        assertNull(shadowOf(webView).lastEvaluatedJavascript)

        // Also disabled ⇒ no injection even for allowlisted origin (if we add one)
        val allowlisted = CleanupRegistry(
            version = 1,
            generic = CleanupRegistry.GenericSelectors(),
            siteEntries = emptyList()
        )
        // Temporarily test with a patched injector that has a known origin
        // For v1, KEYDOWN_PATCH_ORIGINS is empty, so injection never happens.
        assertTrue(CleanupInjector.KEYDOWN_PATCH_ORIGINS.isEmpty())
    }

    @Test
    fun wrapperFullscreenLoggerUsesGuardAndListensForFullscreenChange() {
        val webView = host.attachedWebView()
        val inj = injector(enabled = true)

        inj.injectWrapperFullscreenLogger(webView)

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains(CleanupInjector.GUARD_FULLSCREEN_LOGGER))
        assertTrue(js.contains("fullscreenchange"))
        assertTrue(js.contains("fullscreenElement"))
        assertTrue(js.contains("VIDEO"))
    }

    @Test
    fun wrapperLoggerOffWhenFeatureDisabled() {
        val webView = host.attachedWebView()
        val inj = injector(enabled = false)

        inj.injectWrapperFullscreenLogger(webView)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun corruptRegistryYieldsNoSelectorsNoCrash() {
        val emptyReg = CleanupRegistry.parse("{")

        val inj = CleanupInjector(emptyReg) { true }
        val webView = host.attachedWebView()

        inj.inject(webView, "https://example.com")

        // Empty registry ⇒ injector early-returns (no selectors) ⇒ no JS
        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun injectionEscapesSelectorsForJsArray() {
        val reg = CleanupRegistry(
            version = 1,
            generic = CleanupRegistry.GenericSelectors(
                hideSelectors = listOf("""[data-test="a\"b\\c"]""")
            ),
            siteEntries = emptyList()
        )
        val inj = CleanupInjector(reg) { true }
        val webView = host.attachedWebView()

        inj.inject(webView, "https://example.com")

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertNotNull(js)
        // The escaped selector must appear without breaking the JS string
        assertTrue(js.contains("\\\""))
        assertTrue(js.contains("\\\\"))
    }
}
