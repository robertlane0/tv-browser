# AGENTS.md — TV WebView Browser for Niche Streaming Services

> Agent-facing guide: what this project is, the rules that cannot be broken, and the
> phased implementation plan derived from `PLAN.md` and the modular specs in `specs/`.
> **Read `specs/00-spec-index.md` before touching anything.**

---

## 1. Project Summary

A curated, **TV-optimized web container** (not a general-purpose browser) for Android TV.
It renders niche/regional streaming services that ship only a desktop web player inside
`Android System WebView`, adding:

- Full D-Pad spatial navigation with a high-visibility injected focus highlight
- Forced viewport scaling + per-bookmark `textZoom` (10-foot legibility)
- Fullscreen HTML5 video via a custom `WebChromeClient.onShowCustomView`
- Persistent login sessions (cookies + DOM storage, flushed on pause)
- Leanback home grid of service bookmarks; auto-hiding browser overlay
- Switchable User-Agent (Desktop / Mobile / Native TV) per bookmark
- Optional, cosmetic-only pop-up cleanup layer

**Explicit non-goals** (spec 01 §10): tabs, download manager, history UI beyond the
session back-stack, bundling a browser engine, phone/touch UX, circumventing DRM/paywalls.

---

## 2. Sources of Truth and Precedence

| Document | Role |
|---|---|
| `PLAN.md` | Product intent and rationale (informal) |
| `specs/00-spec-index.md` | Governance: inventory, traceability, authoring rules |
| `specs/01`–`12` | **Normative** technical specifications (RFC 2119 keywords) |

**Precedence:** specs override `PLAN.md`. Note spec 01 §3 (AD-1): the plan says
"single-activity" but the architecture is **two activities** (`HomeActivity`,
`BrowserActivity`) — the diagram and AD-1 win.

**Change control** (spec 00 §6):
1. Changing any normative MUST bumps that spec file's `version` and `last_updated`.
2. Cross-file contracts (notably the `Bookmark` entity used by specs 04, 07, 08) must
   change **atomically** across all referencing files.
3. Any deviation from `PLAN.md` must be recorded as an explicit "Architecture Decision"
   note in the affected spec file.

Spec map (spec 00 §3): 01 architecture · 02 platform/build · 03 WebView config ·
04 UA strategy · 05 input/D-Pad · 06 video/fullscreen · 07 UI/Leanback ·
08 persistence · 09 errors/recovery · 10 content filtering · 11 security/privacy/DRM ·
12 testing matrix.

---

## 3. Toolchain and Quickstart

| Item | Value |
|---|---|
| Android Gradle Plugin | 8.5.x |
| Gradle | 8.7+ (wrapper-pinned) |
| Kotlin | 2.0.x |
| JDK | 17 |
| compileSdk / targetSdk | 34 |
| minSdk | 21 |
| Base package | `com.example.tvbrowser` |

Version catalog pins (spec 02 §3): `leanback 1.2.0-alpha04`, `appcompat 1.7.0`,
`webkit 1.11.0`, `tvprovider 1.1.0-alpha01`, `lifecycle 2.8.4`,
`coroutines 1.8.1`, `room 2.6.1`.

```bash
./gradlew assembleDebug               # dev build (WebView debugging on)
./gradlew testDebugUnitTest           # JVM tests (JUnit4 + Robolectric)
./gradlew connectedDebugAndroidTest   # instrumented tests on TV emulator (API 34 TV image)
./gradlew lintDebug
./gradlew assembleRelease             # R8 full mode; verify bridge keep rules afterward
```

There is no shipped Gradle project yet — **Phase 0 below scaffolds it.**

---

## 4. Non-Negotiable Guardrails (do not violate)

1. **Never bundle a browser engine.** Use the system `Android System WebView`; gate on
   its presence/version at startup (spec 02 §7).
2. **SSL is strict.** `onReceivedSslError` always `handler.cancel()`; `proceed()` is
   forbidden in all builds; no "continue anyway" affordance (specs 09 §6.1, 11 §3).
3. **Never intercept `KEYCODE_DPAD_*`** for custom JS navigation on the hot path —
   WebView native spatial navigation is authoritative (spec 05 §4).
4. **`onRenderProcessGone` must destroy the WebView** and return `true`; keeping a dead
   WebView is forbidden (spec 09 §5).
5. **Release builds:** no `setWebContentsDebuggingEnabled`, R8 keep rule for
   `@JavascriptInterface` methods on `JsBridge` must survive (spec 02 §6).
6. **No `fallbackToDestructiveMigration`** in release — bookmarks are user data (spec 08 §3.4).
7. **Cleartext HTTP:** never global. Only per-domain entries in
   `res/xml/network_security_config.xml`, each logged in spec 11 §6 with a review date.
8. **Cleanup layer is cosmetic only:** hide unclosable modal overlays. Never block/skip
   video ads, paywall gates, or age-verification logic. Opt-in, off by default (specs 10 §2, 11 §5).
9. **No telemetry.** No analytics/crash/ad SDKs. Local diagnostics only: version +
   category + domain — never full URLs with query strings, never credentials (spec 11 §4).
10. **No URL entry via bare `EditText`** — use `GuidedStepSupportFragment` / Leanback
    `SearchBar`, with a D-Pad key-grid fallback for boxes without an IME (spec 07 §5, §9).
11. **Do not recreate the WebView on configuration change** — `BrowserActivity` locks
    landscape and declares `configChanges` (specs 01 §8, 02 §4).
12. **DRM stance:** Widevine L3 only (WebView limitation). Never work around L1
    requirements, CDM patching, key extraction, or geo/paywall blocks (specs 06 §5, 11 §5).

---

## 5. Target Repository Layout

```
app/
├── src/main/
│   ├── java/com/example/tvbrowser/
│   │   ├── ui/home/        HomeActivity (BrowseSupportFragment)          [07 §3]
│   │   ├── ui/browser/     BrowserActivity, WebViewFragment,
│   │   │                   BrowserOverlayController                       [01, 05, 07]
│   │   ├── ui/settings/    SettingsActivity (LeanbackPreferenceFragment) [07 §8]
│   │   ├── web/            WebViewConfigurator, TvWebViewClient,
│   │   │                   TvWebChromeClient, UserAgentProvider           [03, 04, 06, 09]
│   │   ├── input/          RemoteInputHandler, MediaKeyInjector,
│   │   │                   CssInjector                                    [05]
│   │   ├── data/           Bookmark entity, BookmarkDao, AppDatabase,
│   │   │                   BookmarkRepository, PreferencesRepository     [08]
│   │   ├── error/          ErrorClassifier, RedirectLoopDetector,
│   │   │                   ErrorCardController                            [09]
│   │   ├── filter/         CleanupRegistry, CleanupInjector               [10]
│   │   ├── gate/           WebViewProviderGate                            [02 §7]
│   │   └── util/           UrlNormalizer                                  [07 §5]
│   ├── assets/             tv_focus.css, cleanup_registry.json            [05 §5, 10 §4]
│   └── res/xml/            network_security_config.xml                    [02 §5]
│   ├── src/test/           JVM + Robolectric tests                        [12 §2]
│   └── src/androidTest/    Instrumented TV tests T-01…T-15                [12 §3]
gradle/libs.versions.toml   Version catalog                                [02 §3]
specs/                      Normative specifications (existing)
```

---

## 6. Shared Contracts (change atomically)

**`Bookmark` entity** (spec 08 §3; consumed by 04, 07, 09, 12):

| Field | Type | Default |
|---|---|---|
| `id` | Long PK auto | — |
| `title`, `url`, `origin` | String | required |
| `uaMode` | enum `DESKTOP` / `MOBILE` / `NATIVE_TV` | `DESKTOP` |
| `textZoomPercent` | Int | `100` |
| `bannerUri` | String? | null |
| `isPreset` | Boolean | false |
| `sortOrder` | Int | 0 |
| `createdAt`, `lastLaunchedAt` | Long / Long? | now / null |

**Other cross-module contracts:**
- **Back-key precedence:** exit fullscreen → hide overlay → `goBack()` → finish (06 §4 amends 05 §3).
- **Overlay state machine:** Hidden ⇄ Visible ⇄ Pinned; auto-hide 3 s only during
  playback/fullscreen; unreachable while custom view attached (07 §4.2, 06 §4).
- **Error taxonomy:** `network`, `http_client`, `http_server`, `ssl`, `blocked`, `drm`,
  `renderer`, `safebrowsing`; auto-retry only for `network`/`http_server` (09 §3–4).
- **Injection timing:** `onPageFinished` **and** `doUpdateVisitedHistory` (SPA support),
  idempotent via guard element/id; fail silently under strict CSP (05 §5, 10 §3).
- **UA templates:** pinned Chrome major (`CHROME_MAJOR`), bumped quarterly (04 §2).
- **WebView gate:** null provider → blocking screen; major < 110 → non-blocking warning;
  ≥ 110 → proceed (02 §7).

---

## 7. Implementation Plan (phased)

> Each phase lists tasks, governing specs, and exit criteria. Do not start a phase until
> the previous phase's exit criteria pass. Record every deviation as an AD note (spec 00 §6).

### Phase 0 — Project scaffold
- [ ] Init Gradle project: wrapper ≥ 8.7, AGP 8.5.x, Kotlin 2.0.x, JDK 17; create
      `gradle/libs.versions.toml` exactly per spec 02 §3.
- [ ] `compileSdk`/`targetSdk` 34, `minSdk` 21.
- [ ] `AndroidManifest.xml` per spec 02 §4: touchscreen `required="false"`,
      leanback `required="true"`, mic `required="false"`, `INTERNET` +
      `ACCESS_NETWORK_STATE` only, `android:banner` on app + launcher activity,
      `hardwareAccelerated="true"`, `usesCleartextTraffic="false"` +
      `networkSecurityConfig`, both activities with `HomeActivity` as
      `LEANBACK_LAUNCHER`, `BrowserActivity` `singleTask` + landscape + `configChanges`.
- [ ] Themes: `Theme.Leanback.Browse` (home), dark immersive variant (browser).
- [ ] R8 baseline keep rules incl. `JsBridge @JavascriptInterface` (02 §6).
- **Exit:** `assembleDebug` and `assembleRelease` build clean; manifest rules 1–5 verified.

### Phase 1 — Data layer + home screen
- [ ] Room: `Bookmark` entity/DAO/DB per spec 08 §3; migrations with defaults; no
      destructive fallback. Seed preset bookmarks.
- [ ] `BookmarkRepository` + Preferences DataStore keys per 08 §5; all I/O on `Dispatchers.IO`.
- [ ] `HomeActivity` + `BrowseSupportFragment`: "Your Services" row (300×170 dp cards,
      placeholder on image failure) and "Manage" row; empty-state ghost card;
      long-press context row (Edit / Delete / Change UA) consuming the click (07 §3).
- [ ] WebView provider gate on cold start with blocking / warning / proceed branches (02 §7).
- **Exit:** JVM/Robolectric tests for DAO + migrations; home grid renders on TV emulator;
      gate branches testable.

### Phase 2 — WebView core
- [ ] `BrowserActivity` + `WebViewFragment` hosting one `WebView` instance.
- [ ] `WebViewConfigurator` applying the full settings matrix **before first `loadUrl`**
      (03 §2–3): JS, DOM storage, `mediaPlaybackRequiresUserGesture=false`, wide
      viewport + overview, file/content access off, mixed-content compat, single window,
      no auto popups, hardware layer, focusable, cookies + third-party cookies on, UA from
      `UserAgentProvider`, `textZoom` from bookmark, Safe Browsing in release.
- [ ] `UserAgentProvider` with exact DESKTOP/MOBILE templates and `NATIVE_TV` passthrough
      (04 §4); `NATIVE_TV` debug-only in release UI.
- [ ] Lifecycle contract: `onPause` → `webView.onPause()` + `CookieManager.flush()`;
      `onDestroy` → exit fullscreen, detach, destroy (01 §8).
- [ ] Debug-only `setWebContentsDebuggingEnabled(true)` (03 §7).
- **Exit:** Robolectric settings-matrix test asserting every value in 03 §2 (T-10);
      a desktop-UA page loads and stays logged in across pause/resume.

### Phase 3 — Input and focus
- [ ] `RemoteInputHandler` key map per 05 §3–4; D-Pad/Center pass through; ignore
      `repeatCount > 0` for media keys.
- [ ] `CssInjector` + `assets/tv_focus.css` (3 px `#00BFFF` outline + glow), injected on
      `onPageFinished` and `doUpdateVisitedHistory`, CSP-safe, idempotent (05 §5).
- [ ] `MediaKeyInjector`: play/pause toggle; ±10 s seek clamped to `[0, duration]` (05 §6).
- [ ] Focus contract: overlay open steals focus to first button; overlay close returns
      `webView.requestFocus()` (05 §7).
- **Exit:** instrumented T-03, T-04, T-06 pass; focus ring visible on a real test page.

### Phase 4 — Video playback and fullscreen
- [ ] `TvWebChromeClient` per 06 §3: `onShowCustomView`/`onHideCustomView`, double-enter
      guard, single callback invocation, immersive flags + `FLAG_KEEP_SCREEN_ON` add/clear
      symmetry, progress bar + title plumbing.
- [ ] Back-key precedence override: fullscreen exit before overlay/goBack (06 §4.1).
- [ ] Force `exitFullscreen()` in `onCreate` (restore) and `TvWebViewClient.onPageStarted`
      (stuck-container guard) (06 §6).
- [ ] Audio focus listener: pause on LOSS/LOSS_TRANSIENT, no auto-resume on GAIN (06 §6).
- [ ] EME failure hook → distinct DRM error card category (06 §5, 09 §3).
- **Exit:** T-01, T-02 pass on emulator; manual smoke test plays an HTML5 video fullscreen.

### Phase 5 — Overlay, text input, settings
- [ ] `BrowserOverlay`: 96 dp top bar, scrim `#CC000000`, controls in order Back / Forward /
      Refresh / Home / Address / security indicator / Bookmark toggle / Settings; disabled
      states for back/forward (07 §4.1).
- [ ] Show/hide state machine incl. 3 s auto-hide only during playback, pinned state during
      text entry, immediate hide (no exit animation) on auto-hide, unreachable in fullscreen
      (07 §4.2).
- [ ] Address input via `GuidedStepSupportFragment` (native TV IME/voice) with REQUIRED
      D-Pad key-grid fallback; `UrlNormalizer` prepends `https://`, never downgrades,
      invalid input stays in dialog (07 §5, §9).
- [ ] Bookmark add/edit two-step GuidedStep with validation (07 §6).
- [ ] `SettingsActivity` (`LeanbackPreferenceFragmentCompat`): default UA, text zoom,
      cleanup switch, clear session data, engine info, about; confirm-then-reload for UA/zoom
      changes (04 §5, 07 §8).
- **Exit:** T-05, T-06, T-11, T-12 pass; overlay hidden during fullscreen.

### Phase 6 — Session durability and error handling
- [ ] Cookie flush contract and `onSaveInstanceState` URL restore (08 §4).
- [ ] "Clear session data" flow: cookies + WebStorage only, bookmarks untouched, explicit
      sign-out warning, forced reload after clearing (08 §6–7).
- [ ] `ErrorClassifier` + `RedirectLoopDetector` (≥3 same login URLs in 10 s → `blocked`)
      with JVM tests (09 §6.4, §7).
- [ ] TV error cards per 09 §4: layout, normative copy table, Retry/Switch UA/Home
      buttons, auto-retry backoff 2 s/5 s/15 s max 3, POST guard, cancel on input.
- [ ] Renderer-death recovery: exit fullscreen → remove → destroy → fresh configured
      WebView → reload last URL; ≥3 deaths in 60 s stops auto-recovery (09 §5).
- [ ] Offline banner via `ConnectivityManager` (09 §6.3).
- **Exit:** T-07, T-08, T-09, T-15 pass; unit tests green.

### Phase 7 — Content filtering (optional layer)
- [ ] `cleanup_registry.json` v1: generic hide/close selectors + per-origin entries; no
      selector may contain `player`, `video`, or `player-container` (10 §4, §7).
- [ ] `CleanupInjector`: opt-in gate, `display:none !important` (hide, never remove),
      throttled MutationObserver (500 ms), guard flag, CSP-safe (10 §5).
- [ ] Hook largest-video selector upgrade into `MediaKeyInjector`; site-specific keydown
      pass-through and wrapper-fullscreen logger (10 §5 table).
- [ ] Build-time guard test validating registry (10 §7).
- **Exit:** T-13 passes; feature off ⇒ zero DOM side effects.

### Phase 8 — Security hardening and release
- [ ] Verify spec 11 §2–4: permissions footprint, transport rules, privacy copy in
      About/Play listing, no telemetry, Data Safety form alignment.
- [ ] Release build audit: contents debugging off, bridge keep rule intact (media keys work
      in release), Safe Browsing on, R8 full mode (02 §6, 12 §6).
- [ ] Cleartext allowlist: every domain entry has a row in spec 11 §6 with review date.
- **Exit:** release APK passes a full manual pass on the reference TV.

### Phase 9 — Validation matrix and per-service QA
- [ ] Run full T-01…T-15 suite on API 34 TV emulator + reference hardware (12 §3, §5).
- [ ] Populate the per-service compatibility matrix (UA mode, login persistence,
      fullscreen, observed DRM level, max quality) (12 §4).
- [ ] Device matrix: reference TV, 1 GB low-RAM box (OOM path), old WebView provider
      (gate warning), minimal remote (overlay parity) (12 §5).
- **Exit:** release checklist in spec 12 §6 fully green; spec versions/`last_updated`
      bumped where normative content changed.

---

## 8. Testing Map

| Layer | Tooling | What it covers |
|---|---|---|
| JVM unit | JUnit4 | `UserAgentProvider` (04 §6), `UrlNormalizer` (07 §5), `ErrorClassifier`/`RedirectLoopDetector` (09 §7), registry guards (10 §7) |
| Robolectric | 4.x | Settings matrix (03 §8), Room DAO/migrations, DataStore defaults (08 §3) |
| Instrumented | AndroidX Test + Espresso on TV emulator | T-01…T-15 (12 §3) |
| Manual hardware | Checklist | CEC/HDMI, audio focus, real D-Pad feel, per-service playback |

Rules (12 §7): use `IdlingResource` on page-finished, never fixed sleeps; hardware results
override Robolectric conflicts; CEC/HDMI cases are manual-only, never auto-passed.

---

## 9. Gotchas Checklist (recurring failure modes)

- Missing R8 keep rule silently kills the JS bridge **only in release** — verify media keys
  in release builds (02 §6).
- SPAs via `history.pushState` never fire `onPageFinished` — injection must also run in
  `doUpdateVisitedHistory` (05 §5).
- `CookieManager.flush()` is async; last-seconds cookie loss on hard kill is accepted and
  documented, do not "fix" with synchronous hacks (08 §4.1).
- Calling `onCustomViewHidden()` twice throws on some WebView versions; null the callback
  exactly once (06 §4).
- `textZoom` needs a reload below API 33 for consistent layout (03 §5).
- Third-party cookie blocking is the #1 "login button does nothing" cause — keep third-party
  cookies enabled (03 §4).
- Never wrap the WebView in a `ScrollView`/`NestedScrollView` (05 §7).
- Fullscreen container must have no background drawable during playback (03 §6).
- `getCurrentWebViewPackage` can return null after provider switches — gate before any
  WebView instantiation or it throws `AndroidRuntimeException` (02 §7).
- Bump `CHROME_MAJOR` quarterly or sites will show "update your browser" walls (04 §2).

---

## 10. Release Checklist (mirror of spec 12 §6)

1. All T-IDs pass on reference TV and emulator.
2. Per-service matrix re-validated; `cleanup_registry.json` version bumped if changed.
3. Cleartext review log current (11 §6).
4. UA Chrome token current-quarter (04 §2).
5. Release build: debugging off, R8 keep rules intact, Safe Browsing on.
6. Play Data Safety form matches 11 §4.
7. Spec `version`/`last_updated` bumped for any normative change (00 §6).

---

## 11. When Editing Specs

- Preserve YAML front matter (`title`, `version`, `status`, `module`, `last_updated`);
  lifecycle: Draft → Reviewed → Approved → Deprecated.
- Use RFC 2119 keywords; fence code with language tags; Mermaid diagrams with flat labels.
- Keep spec 00 §5 traceability aligned with any PLAN.md mapping changes.
- Cross-file contract edits are atomic (00 §6 rule 2).
