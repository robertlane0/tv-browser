---
title: "WebView Configuration"
version: "1.0.0"
status: "Draft"
module: "webview-core"
last_updated: "2026-08-22"
---

# WebView Configuration

## 1. Purpose

Normative configuration of the `WebView` instance and its `WebSettings`,
`CookieManager`, and rendering layers. Incorrect configuration breaks video
playback, login persistence, or navigation (plan §4.1). Requires the build base
from [02-project-setup-and-dependencies.md](./02-project-setup-and-dependencies.md).

## 2. Settings Matrix

| Setting | Required Value | Rationale | Failure Mode if Wrong |
|---------|---------------|-----------|------------------------|
| `javaScriptEnabled` | `true` | All modern players are JS-driven | Player never initializes; blank page |
| `domStorageEnabled` | `true` | Login tokens and SPA state use `localStorage` | Login lost per navigation; SPA white-screens |
| `mediaPlaybackRequiresUserGesture` | `false` | Allow play() after remote click and autoplay into fullscreen | Some players stall awaiting a synthetic gesture |
| `useWideViewPort` | `true` | Honor viewport meta on desktop layouts | Desktop pages render at 980 px unreadably |
| `loadWithOverviewMode` | `true` | Zoom out to fit width on first paint | Horizontal scrolling on 10-foot display |
| `allowFileAccess` | `false` | Attack-surface reduction | File-URL XSS vector if enabled |
| `allowContentAccess` | `false` | Attack-surface reduction | Content-provider exfiltration vector |
| `mixedContentMode` | `MIXED_CONTENT_COMPATIBILITY_MODE` | Legacy niche CDNs serve HTTP subresources | Strict mode blocks their player outright |
| `setSupportMultipleWindows` | `false` | `target=_blank` links routed to same WebView | Popups spawn invisible windows |
| `javaScriptCanOpenWindowsAutomatically` | `false` | Popup suppression | Ad popups steal focus |
| `textZoom` | Per-bookmark, default 100 | 10-foot legibility override (see [07](./07-ui-ux-leanback-design.md) §7) | Tiny fonts on dense desktop layouts |
| `userAgentString` | From `UserAgentProvider` | See [04-user-agent-strategy.md](./04-user-agent-strategy.md) | Mobile/blocked view served |
| `databaseEnabled` | `true` | Legacy players using WebSQL | Rare players fail to cache metadata |
| `cacheMode` | `LOAD_DEFAULT` | Normal HTTP cache semantics | `LOAD_NO_CACHE` doubles startup bandwidth |
| `safeBrowsingEnabled` | `true` (release) | Google Safe Browsing in WebView | Phishing pages render unflagged |

## 3. Reference Implementation

```kotlin
class WebViewConfigurator(
    private val userAgentProvider: UserAgentProvider
) {
    fun configure(webView: WebView, bookmark: Bookmark) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = bookmark.textZoomPercent
            userAgentString = userAgentProvider.resolve(bookmark)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebViewCompat.setSafeBrowsingEnabled(this, true)
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        WebStorage.getInstance() // forces early allocation of DOM storage backend

        with(webView) {
            isFocusable = true
            isFocusableInTouchMode = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setInitialScale(0) // overview mode governs first paint
        }
    }
}
```

## 4. Cookie Policy

- First-party cookies: always accepted.
- Third-party cookies: accepted. Justification: niche players frequently host
  auth iframes and CDN token endpoints on sibling domains; blocking third-party
  cookies is the single most common cause of "login button does nothing."
  The privacy trade-off and its disclosure are specified in
  [11-security-privacy-and-drm.md](./11-security-privacy-and-drm.md) §4.
- Persistence and flush timing: see
  [08-session-and-data-persistence.md](./08-session-and-data-persistence.md) §4.

## 5. Applying Settings at Runtime

1. Settings MUST be applied **before the first `loadUrl`** on a fresh WebView.
2. Changing `userAgentString` or `textZoom` on a live WebView requires
   `webView.reload()`; the Settings screen (see
   [07](./07-ui-ux-leanback-design.md) §8) MUST trigger reload only after the
   user confirms, because reload interrupts playback.
3. `textZoom` changes apply without reload only on API 33+ behavior; on older
   levels a reload is REQUIRED for consistent layout.

## 6. Rendering and Hardware Acceleration

- Application-level `android:hardwareAccelerated="true"` (declared in
  [02](./02-project-setup-and-dependencies.md) §4) is a hard prerequisite.
- The WebView MUST use `View.LAYER_TYPE_HARDWARE`. Software layering makes
  1080p video drop frames on low-end TV SoCs (Amlogic S905-class).
- The fullscreen container (see
  [06-video-playback-and-fullscreen.md](./06-video-playback-and-fullscreen.md))
  MUST NOT set a background during playback; an opaque black `FrameLayout`
  with no drawable avoids an extra composition pass.

## 7. Debugging Hooks

| Build | Hook |
|-------|------|
| debug | `WebView.setWebContentsDebuggingEnabled(true)` in `WebViewFragment.onViewCreated`; inspect via `chrome://inspect` |
| release | MUST NOT enable contents debugging |

## 8. Error Handling and Edge Cases

| Failure | Symptom | Handling |
|---------|---------|----------|
| `mediaPlaybackRequiresUserGesture=true` regression after refactor | Video element loads but never plays | Settings-matrix unit test (Robolectric) asserting each value in §2 |
| Third-party cookie blocking by future WebView default | Login iframe loops | Detect 401-retry loop in `TvWebViewClient` and surface UA/cookie hint card (see [09](./09-error-handling-and-recovery.md) §4) |
| Mixed content still blocked (compat mode is not "always allow") | Player assets 404 in console | `onReceivedError` for `ERROR_FAILED_SSL_HANDSHAKE`/blocked resource → error card with cleartext allowlist instructions ([02](./02-project-setup-and-dependencies.md) §5) |
| `setInitialScale` ignored by site with `viewport` meta | Over-zoomed first paint | Acceptable; overview mode corrects on next layout. Do NOT fight it with JS zoom. |
| WebView renderer OOM on 1 GB devices | `onRenderProcessGone(true)` | Recovery flow in [09](./09-error-handling-and-recovery.md) §5 |

## 9. Cross-References

- UA strings: [04-user-agent-strategy.md](./04-user-agent-strategy.md)
- Key/focus behavior layered on this instance:
  [05-input-and-dpad-navigation.md](./05-input-and-dpad-navigation.md)
- Clients installed on this instance:
  [06-video-playback-and-fullscreen.md](./06-video-playback-and-fullscreen.md),
  [09-error-handling-and-recovery.md](./09-error-handling-and-recovery.md)
