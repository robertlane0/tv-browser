---
title: "Project Setup and Dependencies"
version: "1.1.0"
status: "Reviewed"
module: "platform"
last_updated: "2026-08-27"
---

# Project Setup and Dependencies

## 1. Purpose

Defines the build environment, SDK levels, manifest declarations, dependency
versions, and the runtime WebView-provider gate. Prerequisites for all runtime
specs, especially [03-webview-configuration.md](./03-webview-configuration.md).

## 2. Toolchain

| Tool | Version | Notes |
|------|---------|-------|
| Android Gradle Plugin | 8.5.x | Required for `compileSdk 34` |
| Gradle | 8.7+ | Wrapper-pinned |
| Kotlin | 2.0.x | `kotlin-android` plugin |
| JDK | 17 | AGP 8.x requirement |
| compileSdk / targetSdk | 34 | Google Play target-level compliance |
| minSdk | 21 | Maximum device coverage per plan §3.1 |

## 3. Version Catalog

`gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.0.20"
agp = "8.5.2"
leanback = "1.2.0-alpha04"
appcompat = "1.7.0"
webkit = "1.11.0"
tvprovider = "1.1.0-alpha01"
lifecycle = "2.8.4"
coroutines = "1.8.1"
room = "2.6.1"

[libraries]
leanback = { module = "androidx.leanback:leanback", version.ref = "leanback" }
leanback-preference = { module = "androidx.leanback:leanback-preference", version.ref = "leanback" }
appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
webkit = { module = "androidx.webkit:webkit", version.ref = "webkit" }
tvprovider = { module = "androidx.tvprovider:tvprovider", version.ref = "tvprovider" }
lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
```

Rationale per dependency:

- `androidx.leanback:leanback` — `BrowseSupportFragment`, `DetailsSupportFragment`,
  `SearchBar`, `GuidedStepSupportFragment` (home grid, overlay, TV keyboard; see
  [07](./07-ui-ux-leanback-design.md)).
- `androidx.tvprovider:tvprovider` — optional home-screen channel
  recommendations.
- `androidx.webkit:webkit` — `WebViewCompat`, `WebViewFeature` capability checks,
  modern API backports.
- `room` — bookmark and per-service settings persistence (see
  [08](./08-session-and-data-persistence.md)).

## 4. Manifest Contract

`AndroidManifest.xml` (normative excerpts):

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- TV device declarations: required for Play Store TV visibility -->
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />
    <uses-feature
        android:name="android.hardware.microphone"
        android:required="false" />

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:banner="@drawable/app_banner"
        android:hardwareAccelerated="true"
        android:usesCleartextTraffic="false"
        android:networkSecurityConfig="@xml/network_security_config"
        android:theme="@style/Theme.Leanback.Browse">

        <activity
            android:name=".ui.home.HomeActivity"
            android:exported="true"
            android:banner="@drawable/app_banner">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".ui.browser.BrowserActivity"
            android:exported="false"
            android:launchMode="singleTask"
            android:screenOrientation="landscape"
            android:configChanges="screenSize|screenLayout|keyboardHidden|orientation"
            android:theme="@style/Theme.Leanback.Immersive" />
    </application>
</manifest>
```

Rules:

1. `android.hardware.touchscreen` MUST be `required="false"`; otherwise the app
   is filtered out of the TV Play Store.
2. `android.software.leanback` MUST be `required="true"`.
3. `android:banner` (320×180 px xhdpi) MUST be present on `<application>` and
   the launcher activity; it is the TV home-screen tile.
4. `android:hardwareAccelerated="true"` MUST be set at application level;
   disabling it on any window breaks video rendering (see
   [03](./03-webview-configuration.md) §6).
5. `configChanges` on `BrowserActivity` MUST include the listed flags so the
   WebView is not recreated when a keyboard attaches or HDMI mode shifts.

## 5. Cleartext Traffic Policy

Default: `usesCleartextTraffic="false"`. Niche regional services occasionally
serve login or manifest endpoints over plain HTTP. Policy:

- Per-domain cleartext MUST be allowlisted in
  `res/xml/network_security_config.xml`, never globally:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <!-- Entries added only after manual review; see 11-security spec -->
        <domain includeSubdomains="true">example-regional-tv.example</domain>
    </domain-config>
</network-security-config>
```

Every allowlisted domain MUST be recorded in
[11-security-privacy-and-drm.md](./11-security-privacy-and-drm.md) §6 with a
review date. HTTP pages MUST surface a padlock-off indicator in the overlay
(see [07](./07-ui-ux-leanback-design.md) §4).

## 6. Build Variants and Hardening

| Variant | Purpose | Differences |
|---------|---------|-------------|
| `debug` | Development | `WebView.setWebContentsDebuggingEnabled(true)` gated by `FLAG_DEBUGGABLE`, cleartext allowed to `localhost`/`127.0.0.1`/`10.0.2.2` via `src/debug` overlay, no minify |
| `release` | Play distribution | R8 full mode (`isMinifyEnabled=true`, `isShrinkResources=true`, `proguard-android-optimize.txt`), debugging disabled (`FLAG_DEBUGGABLE==false` → `setWebContentsDebuggingEnabled` never called), Safe Browsing on, signing via Play App Signing |

Release audit (verified 2026-08-27):

- `WebViewProviderGate` build: `assembleRelease` succeeds; `aapt2 dump xmltree` confirms release `network_security_config.xml` has only `base-config false` (no `domain-config`), debug adds loopback exceptions.
- `JsBridge` bridge intact in release: `proguard-rules.pro` keeps `@JavascriptInterface` methods; `seeds.txt` after `minifyReleaseWithR8` lists `onDrmError` and `onMediaKey`, mapping shows class obfuscated but members kept.
- `setWebContentsDebuggingEnabled` guarded by `isDebuggable()` (`ApplicationInfo.FLAG_DEBUGGABLE`) in `WebViewFragment`; release APK has `FLAG_DEBUGGABLE==false` so path is dead.
- Safe Browsing: `WebViewConfigurator` calls `WebSettingsCompat.setSafeBrowsingEnabled(true)` when `WebViewFeature.SAFE_BROWSING_ENABLE` is supported (both variants; required on in release).
- R8 full mode: `app/build.gradle.kts` `release { isMinifyEnabled=true; isShrinkResources=true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }`.

R8/ProGuard rules MUST keep:

```proguard
-keepclassmembers class com.example.tvbrowser.bridge.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class androidx.leanback.** { *; }
```

Losing the `@JavascriptInterface` keep rule silently disables the media-key
bridge in release builds (see [05](./05-input-and-dpad-navigation.md) §6).

## 7. Runtime WebView Provider Gate

`Android System WebView` is a Play-updated system component; the app MUST NOT
bundle an engine (plan §3.2). On cold start of `HomeActivity`:

```kotlin
object WebViewProviderGate {

    data class Result(
        val available: Boolean,
        val packageName: String?,
        val versionName: String?,
        val majorVersion: Int
    )

    fun check(context: Context): Result {
        val info = WebViewCompat.getCurrentWebViewPackage(context)
            ?: return Result(false, null, null, 0)
        val major = info.versionName?.substringBefore('.')?.toIntOrNull() ?: 0
        return Result(true, info.packageName, info.versionName, major)
    }
}
```

Gate behavior:

| Condition | Behavior |
|-----------|----------|
| `getCurrentWebViewPackage` returns null (WebView disabled, common after a Chrome/WebView provider switch) | Blocking `GuidedStepSupportFragment` error: "Browser engine unavailable. Enable Android System WebView in Settings → Apps." with a deep link to `android.settings.MANAGE_APPLICATIONS_SETTINGS`. App MUST NOT attempt WebView instantiation — it throws `AndroidRuntimeException`. |
| Major version < 110 | Non-blocking warning card: DRM/codec risk; advise Play Store update. Continue. |
| Major version ≥ 110 | Proceed silently. |

The gate result MUST be logged (version only, no PII) to aid field diagnosis of
per-service playback failures tracked in
[12-testing-and-validation-matrix.md](./12-testing-and-validation-matrix.md).

## 8. Error Handling and Edge Cases

| Failure | Detection | Fallback |
|---------|-----------|----------|
| WebView provider disabled mid-session (OTA provider switch) | `onRenderProcessGone` at next navigation | Route to [09](./09-error-handling-and-recovery.md) §5 renderer-death flow |
| Device without Play Store (sideloaded boxes) | Gate §7 null result | Same blocking screen; document sideload of WebView APK as unsupported |
| `tvprovider` missing on non-Google-TV builds | Wrap channel writes in `try/catch` on `TvContractCompat` | Recommendations silently disabled; core app unaffected |
| AGP/SDK drift in CI | `gradle.properties` `android.suppressUnsupportedCompileSdk` forbidden | CI pins `compileSdk` via catalog; build fails fast |

## 9. Cross-References

- Runtime settings applied on top of this base:
  [03-webview-configuration.md](./03-webview-configuration.md)
- Security review obligations for cleartext and permissions:
  [11-security-privacy-and-drm.md](./11-security-privacy-and-drm.md)
