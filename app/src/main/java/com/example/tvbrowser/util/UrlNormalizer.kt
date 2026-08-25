package com.example.tvbrowser.util

import android.net.Uri

object UrlNormalizer {

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "empty input rejected at UI layer" }
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = Uri.parse(withScheme)
        require(!uri.host.isNullOrBlank()) { "no host" }
        return withScheme
    }
}
