package com.example.tvbrowser.error

/**
 * Error taxonomy per spec 09 §3. The category selects the normative error-card
 * copy table (09 §4.2) and whether automatic retry may run (09 §4.3).
 */
enum class Category {
    NETWORK,
    HTTP_CLIENT,
    HTTP_SERVER,
    SSL,
    BLOCKED,
    DRM,
    RENDERER,
    SAFEBROWSING
}
