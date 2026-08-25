package com.example.tvbrowser.error

import android.webkit.WebViewClient

/**
 * Maps WebView failure callbacks onto the taxonomy of spec 09 §3.
 * Pure JVM: WebViewClient error codes are compile-time constants.
 */
class ErrorClassifier {

    fun fromErrorCode(errorCode: Int): Category = when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_TIMEOUT,
        WebViewClient.ERROR_IO -> Category.NETWORK
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> Category.SSL
        WebViewClient.ERROR_REDIRECT_LOOP -> Category.BLOCKED
        else -> Category.NETWORK
    }

    fun fromHttpCode(httpStatusCode: Int): Category = when (httpStatusCode) {
        BLOCKED_HTTP_CODE -> Category.BLOCKED
        in HTTP_CLIENT_RANGE -> Category.HTTP_CLIENT
        else -> Category.HTTP_SERVER
    }

    fun isAutoRetryable(category: Category): Boolean =
        category == Category.NETWORK || category == Category.HTTP_SERVER

    private companion object {
        const val BLOCKED_HTTP_CODE = 403
        val HTTP_CLIENT_RANGE = 400..499
    }
}
