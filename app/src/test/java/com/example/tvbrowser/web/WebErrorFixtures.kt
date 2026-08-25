package com.example.tvbrowser.web

import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.TestWebResourceError
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.tvbrowser.error.TvError

/**
 * Test fixtures for exercising spec 09 detection surfaces under Robolectric,
 * including hidden-constructor framework types (SslErrorHandler) and
 * interface/abstract request and error structs.
 */
object WebErrorFixtures {

    fun mainFrameRequest(url: String, method: String = "GET"): WebResourceRequest =
        FakeWebResourceRequest(url, mainFrame = true, method = method)

    fun subResourceRequest(url: String, method: String = "GET"): WebResourceRequest =
        FakeWebResourceRequest(url, mainFrame = false, method = method)

    fun resourceError(errorCode: Int, description: String = "desc$errorCode"): WebResourceError =
        TestWebResourceError(errorCode, description)

    fun httpErrorResponse(statusCode: Int): WebResourceResponse =
        WebResourceResponse("text/html", "utf-8", statusCode, "reason", mutableMapOf(), null)

    fun sslError(host: String = "https://bad.example"): SslError {
        val certificate = SslCertificate("CN=test", "CN=ca", java.util.Date(0), java.util.Date(4_102_444_799_000L))
        return SslError(SslError.SSL_UNTRUSTED, certificate, host)
    }

    /**
     * SslErrorHandler's constructor is package-private in the SDK stubs;
     * instantiate reflectively so Robolectric's ShadowSslErrorHandler can
     * record cancel()/proceed() calls.
     */
    fun sslErrorHandler(): SslErrorHandler =
        SslErrorHandler::class.java.getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as SslErrorHandler

    fun rendererGoneDetail(didCrash: Boolean): RenderProcessGoneDetail =
        FakeRendererGoneDetail(didCrash)

    class RecordingListener : TvWebViewClient.Listener {
        val navigations = mutableListOf<String>()
        val errors = mutableListOf<TvError>()
        val rendererCrashes = mutableListOf<Boolean>()

        override fun onMainFrameNavigation(url: String) {
            navigations.add(url)
        }

        override fun onWebError(error: TvError) {
            errors.add(error)
        }

        override fun onRendererGone(didCrash: Boolean) {
            rendererCrashes.add(didCrash)
        }
    }

    private class FakeWebResourceRequest(
        private val url: String,
        private val mainFrame: Boolean,
        private val method: String
    ) : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame(): Boolean = mainFrame
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = method
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }

    private class FakeRendererGoneDetail(private val crashed: Boolean) : RenderProcessGoneDetail() {
        override fun didCrash(): Boolean = crashed

        override fun rendererPriorityAtExit(): Int = 1
    }
}
