---
title: "Security, Privacy, and DRM Position"
version: "1.1.0"
status: "Reviewed"
module: "security"
last_updated: "2026-08-27"
---

# Security, Privacy, and DRM Position

## 1. Purpose

Consolidates the security posture (manifest, transport, SSL, WebView provider),
the privacy disclosures implied by cookie and storage policy, and the legal
position on DRM. Cross-cutting: it constrains
[02-project-setup-and-dependencies.md](./02-project-setup-and-dependencies.md),
[03-webview-configuration.md](./03-webview-configuration.md),
[08-session-and-data-persistence.md](./08-session-and-data-persistence.md), and
[10-content-filtering-and-cleanup.md](./10-content-filtering-and-cleanup.md).

## 2. Threat Model

| Threat | Mitigation | Spec |
|--------|-----------|------|
| Malicious page exfiltrates app files | `allowFileAccess=false`, `allowContentAccess=false` | [03](./03-webview-configuration.md) §2 |
| Popup/redirect abuse steals focus | `setSupportMultipleWindows(false)`, no auto window open | [03](./03-webview-configuration.md) §2 |
| MITM on login | HTTPS default, strict SSL (no proceed), Safe Browsing on | [09](./09-error-handling-and-recovery.md) §6 |
| Legacy HTTP-only regional service | Per-domain cleartext allowlist, never global | [02](./02-project-setup-and-dependencies.md) §5, §6 below |
| Stale WebView with known CVEs | Provider gate with version floor and update prompt | [02](./02-project-setup-and-dependencies.md) §7 |
| Release-build bridge exposure | R8 keep rules audited; debugging disabled in release | [02](./02-project-setup-and-dependencies.md) §6 |

## 3. Transport Security Rules

1. `usesCleartextTraffic="false"` globally; exceptions only via per-domain
   `network_security_config.xml` entries.
2. `onReceivedSslError` always cancels; no user bypass
   ([09](./09-error-handling-and-recovery.md) §6.1).
3. URL normalization never downgrades HTTPS to HTTP
   ([07](./07-ui-ux-leanback-design.md) §5).
4. Safe Browsing enabled in release
   ([03](./03-webview-configuration.md) §2).

## 4. Privacy Posture

1. **Third-party cookies are accepted** (justified in
   [03](./03-webview-configuration.md) §4). Disclosure required in the Play
   listing and in-app About: "Login sessions for streaming services are kept
   on your device. Third-party cookies are allowed because some services need
   them to sign you in."
2. **No telemetry.** The app collects no analytics, no crash-reporting SDK, no
   ad SDK. Diagnostics are local logs only (WebView version, error category,
   domain — never URLs with query strings, never credentials).
3. **Data at rest:** cookies and DOM storage live in the WebView profile;
   bookmarks in Room; settings in DataStore
   ([08](./08-session-and-data-persistence.md) §2). All are within the app's
   sandbox; no `WRITE_EXTERNAL_STORAGE` is requested.
4. **User controls:** "Clear session data"
   ([08](./08-session-and-data-persistence.md) §6) and per-bookmark deletion
   ([07](./07-ui-ux-leanback-design.md) §3.2) are the complete data controls.
5. **Permissions footprint:** `INTERNET`, `ACCESS_NETWORK_STATE` only
   ([02](./02-project-setup-and-dependencies.md) §4). Microphone is declared
   `required="false"` and never requested at runtime; voice input goes through
   the system IME, not the app.

## 5. DRM and Content-Access Position

1. The app renders services the user has lawful access to; it does not
   circumvent DRM, paywalls, geo-restrictions, or bot detection.
2. Widevine is used exactly as exposed by WebView EME (L3 only; L1 unsupported
   — [06-video-playback-and-fullscreen.md](./06-video-playback-and-fullscreen.md) §5).
   No CDM patching, no key extraction, no output capture.
3. The cleanup layer hides modal overlays only; it MUST NOT hide or skip
   video ads, paywall gates, or age-verification logic that gates content
   access ([10](./10-content-filtering-and-cleanup.md) §2 rule 3). The age-gate
   example in the registry hides only the *unclosable wrapper* after the site
   itself has accepted input — patches that bypass verification are rejected
   in review.
4. UA spoofing ([04](./04-user-agent-strategy.md)) presents a standard desktop
   browser identity; it does not forge device attestations or DRM identities.

## 6. Cleartext Allowlist Review Log

Every entry in `network_security_config.xml` MUST have a row here:

| Domain | Justification | Added | Review By | Removal Condition |
|--------|---------------|-------|-----------|-------------------|
| _No active cleartext exceptions — verified 2026-08-27_ | — | — | 2026-08-27 | — |

Template for future per-domain exceptions (illustrative — not shipped in this
release):

| Domain | Justification | Added | Review By | Removal Condition |
|--------|---------------|-------|-----------|-------------------|
| `example-regional-tv.example` | Login endpoint HTTP-only as of 2026-06 | 2026-06-10 | 2026-12-10 | Site deploys HTTPS |

Release verification (2026-08-27): `app/src/main/res/xml/network_security_config.xml`
contains only `<base-config cleartextTrafficPermitted="false" />`; no
`<domain-config>` entries are shipped. Debug variant
`app/src/debug/res/xml/network_security_config.xml` adds loopback exceptions
(`127.0.0.1`, `localhost`, `10.0.2.2`) for emulator QA only and is never included
in release APKs (verified via `aapt2 dump xmltree` of `res/8G.xml`).

Review cadence: every entry re-validated within 6 months; expired entries are
removed in the next release. Next scheduled review: 2027-02-27.

## 7. Error Handling and Edge Cases

| Failure | Symptom | Handling |
|---------|---------|----------|
| Play Data Safety form drift | Store rejection | Form reviewed each release against §4; any new data type requires spec amendment |
| Cleartext domain later redirects to HTTPS | Stale exception | Review log §6 removes it; no runtime change needed |
| Service demands Widevine L1 | Playback refusal | Documented limitation surfaced via DRM card ([09](./09-error-handling-and-recovery.md) §4.2); NOT worked around |
| Rooted device | DRM L3 may downgrade or fail | No root detection or blocking; degraded playback is the natural consequence and is documented |
| Sideloaded modified WebView | Unknown engine behavior | Provider gate reports package name; non-Google providers are warned about but not blocked (user's device, user's choice) |

## 8. Cross-References

- Manifest and provider gate: [02-project-setup-and-dependencies.md](./02-project-setup-and-dependencies.md)
- Cookie/storage mechanics: [08-session-and-data-persistence.md](./08-session-and-data-persistence.md)
- DRM technical constraints: [06-video-playback-and-fullscreen.md](./06-video-playback-and-fullscreen.md) §5
- Cleanup boundaries: [10-content-filtering-and-cleanup.md](./10-content-filtering-and-cleanup.md) §2
