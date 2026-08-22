---
title: "User Agent Strategy"
version: "1.0.0"
status: "Draft"
module: "webview-core"
last_updated: "2026-08-22"
---

# User Agent Strategy

## 1. Purpose

Many niche streaming sites serve a mobile or blocked view to TV devices
(plan §4.2). This spec defines the switchable User-Agent model, exact UA
strings, per-bookmark persistence contract, and switching semantics. Applied by
[03-webview-configuration.md](./03-webview-configuration.md) §3; stored per
[08-session-and-data-persistence.md](./08-session-and-data-persistence.md) §3.

## 2. UA Modes

| Mode | Enum | User Agent String | Use Case |
|------|------|-------------------|----------|
| Desktop (default) | `DESKTOP` | `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36` | Forces the full desktop player, typically more functional than the mobile site |
| Mobile | `MOBILE` | `Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36` | Fallback when the desktop layout is completely unusable with D-Pad |
| Native TV | `NATIVE_TV` | WebView default UA (do not override) | Debugging only; MUST be labeled "Debug" in UI |

Rules:

1. `DESKTOP` is the global default and the default for every new bookmark.
2. UA strings are **templates** with a pinned Chrome major version; the version
   token MUST be updated at least quarterly to avoid "outdated browser" walls.
   The update is a single constant change in `UserAgentProvider`.
3. `NATIVE_TV` MUST NOT be selectable as a bookmark default in release builds.

## 3. Data Model

The `Bookmark` entity (full schema in
[08-session-and-data-persistence.md](./08-session-and-data-persistence.md) §3)
carries:

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `uaMode` | `ENUM(DESKTOP, MOBILE, NATIVE_TV)` | `DESKTOP` | Per-bookmark override |
| `textZoomPercent` | `INT` | `100` | Co-located because both fix "unreadable site" |

## 4. Resolution Logic

```kotlin
class UserAgentProvider(private val webViewDefault: () -> String) {

    enum class UaMode { DESKTOP, MOBILE, NATIVE_TV }

    companion object {
        private const val CHROME_MAJOR = 126
        val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$CHROME_MAJOR.0.0.0 Safari/537.36"
        val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$CHROME_MAJOR.0.0.0 Mobile Safari/537.36"
    }

    fun resolve(bookmark: Bookmark): String = when (bookmark.uaMode) {
        UaMode.DESKTOP -> DESKTOP_UA
        UaMode.MOBILE -> MOBILE_UA
        UaMode.NATIVE_TV -> webViewDefault()
    }
}
```

## 5. Switching Semantics

1. Switching UA mode in Settings MUST:
   a. Persist `uaMode` to the bookmark immediately.
   b. Show a confirmation: "Reload page to apply? Playback will stop."
   c. On confirm: `webView.settings.userAgentString = resolve(bookmark)` then
      `webView.reload()`.
2. A UA change does **not** clear cookies. However, some services bind session
   tokens to UA; if post-switch the site logs the user out, that is expected
   behavior and MUST be mentioned in the confirmation copy.
3. Mid-session switches during active fullscreen playback are forbidden: the
   Settings overlay is unreachable while the fullscreen custom view is shown
   (see [06-video-playback-and-fullscreen.md](./06-video-playback-and-fullscreen.md) §6).

## 6. Self-Tests

Runnable JVM unit tests (no Android runtime needed for the provider):

```kotlin
class UserAgentProviderTest {

    private val provider = UserAgentProvider { "WebViewDefault/1.0" }

    private fun bookmark(mode: UserAgentProvider.UaMode) =
        Bookmark(id = 1, title = "T", url = "https://x.tv",
                 uaMode = mode, textZoomPercent = 100)

    @Test fun desktopIsDefaultAndWindows() {
        val ua = provider.resolve(bookmark(UserAgentProvider.UaMode.DESKTOP))
        assertTrue(ua.contains("Windows NT 10.0"))
        assertFalse(ua.contains("Mobile"))
    }

    @Test fun mobileContainsMobileToken() {
        val ua = provider.resolve(bookmark(UserAgentProvider.UaMode.MOBILE))
        assertTrue(ua.contains("Mobile Safari"))
    }

    @Test fun nativeTvUsesWebViewDefault() {
        assertEquals("WebViewDefault/1.0",
            provider.resolve(bookmark(UserAgentProvider.UaMode.NATIVE_TV)))
    }
}
```

## 7. Error Handling and Edge Cases

| Failure | Detection | Fallback |
|---------|-----------|----------|
| Site still blocks after UA switch (server-side feature detection) | Error page or endless login redirect | Error card copy: "This service may block TV browsers. Try switching User Agent in Settings." — raised by [09](./09-error-handling-and-recovery.md) §4 |
| Site serves desktop HTML but touch-only JS handlers | Buttons unreachable by D-Pad | Escalate to focus-CSS injection tuning in [05](./05-input-and-dpad-navigation.md) §5; UA cannot fix this |
| UA sniffing via `navigator.userAgent` vs HTTP header mismatch | Player detects "spoof" | WebView sets both consistently; no action possible, document per-service in [12](./12-testing-and-validation-matrix.md) |
| Chrome version token ages past site's minimum-supported check | "Update your browser" wall | Quarterly token bump process (§2 rule 2) |

## 8. Cross-References

- Settings application point: [03-webview-configuration.md](./03-webview-configuration.md) §3
- Persistence: [08-session-and-data-persistence.md](./08-session-and-data-persistence.md)
- Settings UI: [07-ui-ux-leanback-design.md](./07-ui-ux-leanback-design.md) §8
