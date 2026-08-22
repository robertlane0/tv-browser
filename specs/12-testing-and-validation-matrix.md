---
title: "Testing and Validation Matrix"
version: "1.0.0"
status: "Draft"
module: "qa"
last_updated: "2026-08-22"
---

# Testing and Validation Matrix

## 1. Purpose

Defines the test layers, the per-service compatibility matrix that operationalizes
the Widevine limitation (plan §4.4), device coverage, and the release checklist.
Validates every requirement in specs
[01](./01-architecture-overview.md)–[11](./11-security-privacy-and-drm.md).

## 2. Test Layers

| Layer | Tooling | Scope | Specs Covered |
|-------|---------|-------|---------------|
| Unit (JVM) | JUnit4 | `UserAgentProvider`, `UrlNormalizer`, `ErrorClassifier`, `RedirectLoopDetector`, registry guards | [04](./04-user-agent-strategy.md) §6, [07](./07-ui-ux-leanback-design.md) §5, [09](./09-error-handling-and-recovery.md) §7, [10](./10-content-filtering-and-cleanup.md) §7 |
| Unit (Robolectric) | Robolectric 4.x | Settings matrix assertion, Room DAO/migrations, DataStore defaults | [03](./03-webview-configuration.md) §8, [08](./08-session-and-data-persistence.md) §3 |
| Instrumented | AndroidX Test + Espresso on TV emulator | Key dispatch, overlay state machine, fullscreen enter/exit, error cards | [05](./05-input-and-dpad-navigation.md), [06](./06-video-playback-and-fullscreen.md), [07](./07-ui-ux-leanback-design.md), [09](./09-error-handling-and-recovery.md) |
| Manual on hardware | Checklist §6 | Real D-Pad feel, CEC, audio focus, per-service playback | all |

## 3. Normative Test Cases (Instrumented)

| ID | Scenario | Steps | Expected |
|----|----------|-------|----------|
| T-01 | Fullscreen round trip | Load test page with `<video>`, request fullscreen, press Back | Custom view attached, system UI hidden; Back restores WebView and system UI ([06](./06-video-playback-and-fullscreen.md) §4) |
| T-02 | Back precedence | Enter fullscreen, open nothing, press Back ×2 | First Back exits fullscreen; second Back calls `goBack()` or exits |
| T-03 | Media keys | During playback, inject `KEYCODE_MEDIA_PLAY_PAUSE` | Video toggles paused state ([05](./05-input-and-dpad-navigation.md) §6) |
| T-04 | Seek clamping | `KEYCODE_MEDIA_REWIND` at `currentTime < 10` | `currentTime` clamps to 0, no exception |
| T-05 | Overlay auto-hide | Start playback, show overlay, wait 3.5 s idle | Overlay hidden; browsing (no playback) keeps overlay visible ([07](./07-ui-ux-leanback-design.md) §4.2) |
| T-06 | Focus return | Open overlay, close via Menu | `webView.hasFocus()` is true |
| T-07 | Cookie persistence | Log in on a test site, `onPause`, kill process, relaunch | Session still authenticated ([08](./08-session-and-data-persistence.md) §4) |
| T-08 | Renderer death | `adb shell kill` the renderer process mid-playback | App recovers per [09](./09-error-handling-and-recovery.md) §5; no crash; URL reloaded |
| T-09 | SSL strictness | Navigate to a bad-cert host | `ssl` card; no proceed affordance ([09](./09-error-handling-and-recovery.md) §6.1) |
| T-10 | Settings matrix | Robolectric: configure WebView, read back every setting | Each value equals [03](./03-webview-configuration.md) §2 |
| T-11 | UA reload confirm | Change UA in settings | Confirmation shown; only on confirm does reload occur ([04](./04-user-agent-strategy.md) §5) |
| T-12 | GuidedStep fallback | Disable IME, open address entry | D-Pad key grid appears ([07](./07-ui-ux-leanback-design.md) §9) |
| T-13 | Cleanup guard | Enable filter, load registry test page | Overlays hidden; player unaffected ([10](./10-content-filtering-and-cleanup.md) §7) |
| T-14 | DB migration | Install v1 DB, upgrade app | Bookmarks preserved, new columns defaulted ([08](./08-session-and-data-persistence.md) §3.4) |
| T-15 | Login loop detection | Stub 3 rapid redirects to `/login` | `blocked` card, loop stopped ([09](./09-error-handling-and-recovery.md) §6.4) |

## 4. Per-Service Compatibility Matrix

Maintained per release; this is the operational record for the Widevine
limitation (plan §4.4). Every supported service gets a row:

| Service | UA Mode | Login Persists | Fullscreen OK | DRM Level Observed | Max Quality | Notes |
|---------|---------|----------------|---------------|--------------------|-------------|-------|
| Service A (template) | DESKTOP | Yes | Yes | Widevine L3 | 1080p | — |
| Service B (template) | MOBILE | Yes | Yes | Widevine L3 | 720p | Desktop layout unusable |
| Service C (template) | DESKTOP | Yes | No | L1 required | Fails | Listed as unsupported; DRM card shown |

Process: each release re-runs T-01…T-08 against every row; regressions move the
service to "unsupported" with a registry note
([10](./10-content-filtering-and-cleanup.md) §4).

## 5. Device Matrix

| Class | Example | Focus |
|-------|---------|-------|
| Reference TV | Chromecast with Google TV (4K) | Full pass, all T-IDs |
| Low-RAM box | 1 GB Android TV 11 stick | T-08 (OOM recovery), playback smoothness ([03](./03-webview-configuration.md) §6) |
| Old provider | Device with WebView < 110 | Gate warning path ([02](./02-project-setup-and-dependencies.md) §7) |
| Minimal remote | No media keys | Overlay transport parity ([05](./05-input-and-dpad-navigation.md) §8) |
| Emulator | API 34 TV image | CI instrumented suite |

## 6. Release Checklist

1. All T-IDs pass on reference TV and emulator.
2. Per-service matrix §4 re-validated; `cleanup_registry.json` version bumped
   if selectors changed.
3. Cleartext review log current ([11](./11-security-privacy-and-drm.md) §6).
4. Chrome token in `UserAgentProvider` current-quarter
   ([04](./04-user-agent-strategy.md) §2).
5. Release build verified: contents debugging off, R8 keep rules intact
   (bridge works), Safe Browsing on.
6. Play Data Safety form matches [11](./11-security-privacy-and-drm.md) §4.
7. Spec `version`/`last_updated` bumped for any file whose normative content
   changed ([00](./00-spec-index.md) §6).

## 7. Error Handling and Edge Cases (QA Process)

| Failure | Handling |
|---------|----------|
| Emulator cannot exercise CEC/HDMI | Those cases are manual-only on hardware; marked as such, never auto-passed |
| Flaky WebView timing in CI | Instrumented tests use `IdlingResource` on page-finished, not fixed sleeps |
| Service matrix rot (site redesign) | Row owner re-runs within 2 weeks of a field report; matrix is a living document |
| Robolectric/WebView behavior drift vs hardware | Any conflict resolves in favor of hardware results; Robolectric tests are for regression speed, not truth |

## 8. Cross-References

- All specs [00](./00-spec-index.md)–[11](./11-security-privacy-and-drm.md);
  traceability back to `PLAN.md` is maintained in
  [00-spec-index.md](./00-spec-index.md) §5.
