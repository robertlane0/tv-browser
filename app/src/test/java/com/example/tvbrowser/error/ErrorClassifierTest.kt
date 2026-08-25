package com.example.tvbrowser.error

import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorClassifierTest {

    private val classifier = ErrorClassifier()

    @Test
    fun dnsFailureIsNetworkCategory() {
        assertEquals(Category.NETWORK, classifier.fromErrorCode(WebViewClient.ERROR_HOST_LOOKUP))
    }

    @Test
    fun connectTimeoutAndIoAreNetworkCategory() {
        assertEquals(Category.NETWORK, classifier.fromErrorCode(WebViewClient.ERROR_CONNECT))
        assertEquals(Category.NETWORK, classifier.fromErrorCode(WebViewClient.ERROR_TIMEOUT))
        assertEquals(Category.NETWORK, classifier.fromErrorCode(WebViewClient.ERROR_IO))
    }

    @Test
    fun unknownErrorCodesFallBackToNetwork() {
        assertEquals(Category.NETWORK, classifier.fromErrorCode(WebViewClient.ERROR_UNKNOWN))
        assertEquals(Category.NETWORK, classifier.fromErrorCode(-12345))
    }

    @Test
    fun sslHandshakeErrorCodeIsSslCategory() {
        assertEquals(Category.SSL, classifier.fromErrorCode(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE))
    }

    @Test
    fun redirectLoopErrorCodeIsBlockedCategory() {
        assertEquals(Category.BLOCKED, classifier.fromErrorCode(WebViewClient.ERROR_REDIRECT_LOOP))
    }

    @Test
    fun http403IsBlockedCategory() {
        assertEquals(Category.BLOCKED, classifier.fromHttpCode(403))
    }

    @Test
    fun otherHttpClientErrorsAreHttpClientCategory() {
        assertEquals(Category.HTTP_CLIENT, classifier.fromHttpCode(400))
        assertEquals(Category.HTTP_CLIENT, classifier.fromHttpCode(404))
        assertEquals(Category.HTTP_CLIENT, classifier.fromHttpCode(429))
    }

    @Test
    fun serverErrorsAreHttpServerCategory() {
        assertEquals(Category.HTTP_SERVER, classifier.fromHttpCode(500))
        assertEquals(Category.HTTP_SERVER, classifier.fromHttpCode(503))
        assertEquals(Category.HTTP_SERVER, classifier.fromHttpCode(599))
    }

    @Test
    fun networkCategoryIsAutoRetryable() {
        assertTrue(classifier.isAutoRetryable(Category.NETWORK))
    }

    @Test
    fun http500IsServerCategoryWithRetry() {
        val c = classifier.fromHttpCode(503)
        assertEquals(Category.HTTP_SERVER, c)
        assertTrue(classifier.isAutoRetryable(c))
    }

    @Test
    fun sslIsNeverRetryable() {
        assertFalse(classifier.isAutoRetryable(Category.SSL))
    }

    @Test
    fun onlyNetworkAndServerCategoriesAreRetryable() {
        Category.entries.filterNot { it == Category.NETWORK || it == Category.HTTP_SERVER }
            .forEach { category ->
                assertFalse("category $category must not auto-retry", classifier.isAutoRetryable(category))
            }
    }
}
