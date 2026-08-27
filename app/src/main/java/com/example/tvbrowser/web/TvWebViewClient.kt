package com.example.tvbrowser.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.example.tvbrowser.error.Category
import com.example.tvbrowser.error.ErrorClassifier
import com.example.tvbrowser.error.RedirectLoopDetector
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.error.TvError
import com.example.tvbrowser.filter.CleanupInjector
import com.example.tvbrowser.input.CssInjector
import java.util.Locale

/**
 * WebViewClient implementing the detection surfaces of spec 09 §2 and the
 * special flows of §6: strict SSL (never `proceed()`), main-frame error
 * classification, subresource silence, Safe Browsing delegation to the
 * platform interstitial, login redirect-loop detection (§6.4) and mandatory
 * renderer-death teardown (§5).
 */
class TvWebViewClient(
    private val cssInjector: CssInjector,
    private val fullscreen: FullscreenController? = null,
    private val onPageStartedInjection: ((WebView) -> Unit)? = null,
    private val loopDetector: RedirectLoopDetector? = null,
    private val classifier: ErrorClassifier = ErrorClassifier(),
    private val listener: Listener? = null,
    private val cleanupInjector: CleanupInjector? = null
) : WebViewClient() {

    interface Listener {
        /** Committed main-frame navigation (SPA history updates included). */
        fun onMainFrameNavigation(url: String)

        /** A classified failure that may warrant a TV error card (09 §4). */
        fun onWebError(error: TvError)

        /** Renderer death; teardown already performed by the client. */
        fun onRendererGone(didCrash: Boolean)
    }

    private var reportedLoopUrl: String? = null

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        fullscreen?.forceTeardown()
        onPageStartedInjection?.invoke(view)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        cssInjector.injectFocusHighlight(view)
        injectCleanupIfNeeded(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        cssInjector.injectFocusHighlight(view)
        injectCleanupIfNeeded(view, url)
        if (url.isNullOrEmpty() || isReload) return
        listener?.onMainFrameNavigation(url)
        trackLoginRedirectLoop(view, url)
    }

    private fun injectCleanupIfNeeded(view: WebView, url: String?) {
        val injector = cleanupInjector ?: return
        val origin = url?.takeUnless { it.isBlank() }?.let { runCatching { Bookmark.originOf(it) }.getOrNull() }
            ?: view.url?.let { runCatching { Bookmark.originOf(it) }.getOrNull() }
            ?: return
        injector.inject(view, origin)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        // No super call: the base implementation bridges to the deprecated
        // legacy callback, which would double-report the failure.
        if (!request.isForMainFrame) return
        reportNetworkFailure(request.method, error.errorCode)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        // Legacy engines (< API 23) dispatch main-frame failures here only.
        reportNetworkFailure(METHOD_GET, errorCode)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (!request.isForMainFrame) return
        val category = classifier.fromHttpCode(errorResponse.statusCode)
        listener?.onWebError(tvError(category, request.method, errorResponse.statusCode))
    }

    /**
     * Strict TLS policy (spec 09 §6.1, 11 §3): `proceed()` is forbidden in
     * every build; the load is cancelled and the `ssl` card is surfaced.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        listener?.onWebError(TvError(Category.SSL))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        fullscreen?.exitFullscreen()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        listener?.onRendererGone(detail.didCrash())
        return true
    }

    private fun trackLoginRedirectLoop(view: WebView, url: String) {
        val detector = loopDetector ?: return
        if (url == reportedLoopUrl) return
        reportedLoopUrl = null
        detector.onUrl(url)
        if (detector.isLooping()) {
            reportedLoopUrl = url
            runCatching { view.stopLoading() }
            listener?.onWebError(TvError(Category.BLOCKED))
        }
    }

    private fun reportNetworkFailure(method: String, errorCode: Int) {
        val category = classifier.fromErrorCode(errorCode)
        listener?.onWebError(tvError(category, method))
    }

    private fun tvError(category: Category, method: String?, httpCode: Int? = null): TvError =
        TvError(
            category = category,
            httpCode = httpCode,
            allowsAutoRetry = classifier.isAutoRetryable(category) && isReplayableMethod(method)
        )

    private companion object {
        const val METHOD_GET = "GET"
        const val METHOD_HEAD = "HEAD"

        /** POST guard of spec 09 §4.3 rule 4: never replay non-GET/HEAD. */
        fun isReplayableMethod(method: String?): Boolean {
            val normalized = method?.uppercase(Locale.US) ?: METHOD_GET
            return normalized == METHOD_GET || normalized == METHOD_HEAD
        }
    }
}
