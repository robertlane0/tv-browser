---
title: "Specification Index — TV WebView Browser for Niche Streaming"
version: "1.0.0"
status: "Draft"
module: "governance"
last_updated: "2026-08-22"
---

# Specification Index

## 1. Purpose

This document is the entry point and governance record for the modular technical
specification set derived from `PLAN.md` ("Comprehensive Plan for an Android TV
WebView Browser for Niche Streaming Services"). It defines the document inventory,
traceability to the source plan, authoring conventions, and change-control rules
that apply to every sibling file in this directory.

## 2. Scope

The system under specification is a **curated, TV-optimized web container** for
Android TV that renders niche/regional streaming services (which ship only a
desktop web player) inside `Android System WebView`, with full D-Pad navigation,
forced viewport scaling, fullscreen HTML5 video, and persistent sessions.

Out of scope: general-purpose browsing, tab management, download management, and
bundling a private browser engine. See
[01-architecture-overview.md](./01-architecture-overview.md) §10 (Non-Goals).

## 3. Document Inventory

| # | File | Module | Covers (PLAN.md §) | Depends On |
|---|------|--------|--------------------|------------|
| 00 | `00-spec-index.md` | governance | — | — |
| 01 | `01-architecture-overview.md` | architecture | §1, §2 | 00 |
| 02 | `02-project-setup-and-dependencies.md` | platform | §3 | 01 |
| 03 | `03-webview-configuration.md` | webview-core | §4.1 | 02 |
| 04 | `04-user-agent-strategy.md` | webview-core | §4.2 | 03, 08 |
| 05 | `05-input-and-dpad-navigation.md` | input | §4.3 | 03 |
| 06 | `06-video-playback-and-fullscreen.md` | media | §4.4 | 03, 05 |
| 07 | `07-ui-ux-leanback-design.md` | ui | §5 | 01, 05 |
| 08 | `08-session-and-data-persistence.md` | data | §6 | 03 |
| 09 | `09-error-handling-and-recovery.md` | reliability | §6 | 03, 06 |
| 10 | `10-content-filtering-and-cleanup.md` | filtering | §6 | 03 |
| 11 | `11-security-privacy-and-drm.md` | security | §3.1, §4.4 | 02, 06 |
| 12 | `12-testing-and-validation-matrix.md` | qa | §4.4, §6 | all |

## 4. Authoring Conventions

- **Normative keywords** follow RFC 2119: MUST, MUST NOT, SHOULD, SHOULD NOT, MAY.
- **Code** is fenced with a language tag (`kotlin`, `xml`, `javascript`, `toml`).
- **Diagrams** use Mermaid (`graph TD` or `sequenceDiagram`) with flat node
  labels; labeled arrows use the `-- Label -->` form.
- **Cross-references** are relative Markdown links to sibling files.
- Every file carries YAML front matter: `title`, `version`, `status`,
  `module`, `last_updated`.
- `status` lifecycle: `Draft` → `Reviewed` → `Approved` → `Deprecated`.

## 5. Traceability Matrix

| PLAN.md Section | Requirement Summary | Spec File(s) |
|-----------------|---------------------|--------------|
| §1 Core Concept | Curated TV web container, D-Pad, zoom, fullscreen, persistence, launcher tiles | 01, 05, 06, 07, 08 |
| §2 Architecture | Activity/fragment topology, WebView clients, JS bridge, overlay | 01, 03, 05, 07 |
| §3.1 Environment | API 34 target, minSdk 21, leanback manifest flags | 02 |
| §3.2 WebView Dependency | System WebView as updatable provider | 02, 11 |
| §4.1 WebView Config | WebSettings, cookies, hardware acceleration | 03 |
| §4.2 User Agent | Desktop/Mobile/Native modes, per-bookmark toggle | 04, 08 |
| §4.3 D-Pad | Focus handling, focus CSS, key mapping | 05 |
| §4.4 Fullscreen | WebChromeClient custom view, Widevine limits | 06, 11 |
| §5 UI/UX | Home grid, overlay, Leanback keyboard, textZoom | 07 |
| §6 Session/Errors | Cookie flush, error cards, optional cleanup | 08, 09, 10 |

## 6. Change Control

1. Any change to a normative (MUST) requirement requires incrementing that
   file's `version` and updating `last_updated`.
2. Cross-file contract changes (e.g., the `Bookmark` entity fields shared by
   04, 07, 08) MUST be applied atomically across all referencing files in one
   revision.
3. Deviations from `PLAN.md` MUST be recorded as an explicit "Architecture
   Decision" note in the affected file (see 01 §3, AD-1, for the worked example).

## 7. Glossary

| Term | Definition |
|------|------------|
| D-Pad | Directional pad on an Android TV remote; emits `KEYCODE_DPAD_*` events |
| Leanback | AndroidX library family for 10-foot TV UI |
| 10-foot UI | UI legible and operable from ~3 m viewing distance |
| EME | Encrypted Media Extensions; the web DRM API surfaced by WebView |
| Widevine L1/L3 | Hardware-backed vs software-only DRM security levels |
| SPA | Single-page application; navigates without full page loads |
| Custom View | The fullscreen video `View` delivered via `WebChromeClient.onShowCustomView` |
