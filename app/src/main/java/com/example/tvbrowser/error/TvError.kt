package com.example.tvbrowser.error

/**
 * A classified, user-facing failure surfaced as a TV error card (spec 09 §4).
 *
 * [httpCode] carries the offending status for `http_client`/`http_server`
 * copy interpolation. [allowsAutoRetry] already folds in the POST guard of
 * 09 §4.3 rule 4: non-GET/HEAD main frames never auto-retry.
 */
data class TvError(
    val category: Category,
    val httpCode: Int? = null,
    val allowsAutoRetry: Boolean = false
)
