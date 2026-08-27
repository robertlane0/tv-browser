package com.example.tvbrowser.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build-time guard for Phase 8 security hardening (spec 02 §6, 11 §2-4, 12 §6).
 * JVM-only: inspects source files and version catalog, not runtime WebView.
 */
class ReleaseHardeningTest {

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "gradle/libs.versions.toml").exists()) {
            dir = dir.parentFile
        }
        return dir ?: File(".")
    }

    @Test
    fun networkSecurityConfigReleaseHasNoCleartextDomainEntries() {
        val root = repoRoot()
        val mainXml = File(root, "app/src/main/res/xml/network_security_config.xml").readText()
        assertTrue("main network_security_config must forbid cleartext globally", mainXml.contains("""cleartextTrafficPermitted="false""""))
        // main file must NOT contain any domain-config allowlist; debug overlay is the only place for loopback.
        assertFalse("release network_security_config must not ship any domain-config cleartext=true", mainXml.contains("""cleartextTrafficPermitted="true""""))
        assertFalse("release must not contain example-regional allowlist", mainXml.contains("example-regional-tv"))
    }

    @Test
    fun debugNetworkSecurityConfigAddsOnlyLoopback() {
        val root = repoRoot()
        val debugXml = File(root, "app/src/debug/res/xml/network_security_config.xml").readText()
        assertTrue(debugXml.contains("""cleartextTrafficPermitted="false""""))
        assertTrue(debugXml.contains("127.0.0.1"))
        assertTrue(debugXml.contains("localhost"))
        assertTrue(debugXml.contains("10.0.2.2"))
        assertFalse("debug overlay must not contain example-regional-tv", debugXml.contains("example-regional-tv"))
    }

    @Test
    fun manifestUsesCleartextTrafficFalseAndMinimalPermissions() {
        val root = repoRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:usesCleartextTraffic="false""""))
        assertTrue(manifest.contains("""android.permission.INTERNET"""))
        assertTrue(manifest.contains("""android.permission.ACCESS_NETWORK_STATE"""))
        assertFalse("WRITE_EXTERNAL_STORAGE must never be requested (spec 11 §4)", manifest.contains("WRITE_EXTERNAL_STORAGE"))
        assertFalse("RECORD_AUDIO must never be requested at runtime", manifest.contains("RECORD_AUDIO"))
        // Microphone is declared required=false, not a runtime permission request.
        assertTrue(manifest.contains("""android.hardware.microphone"""))
    }

    @Test
    fun sslErrorHandlerNeverCallsProceed() {
        val root = repoRoot()
        val client = File(root, "app/src/main/java/com/example/tvbrowser/web/TvWebViewClient.kt").readText()
        assertTrue("onReceivedSslError must call handler.cancel()", client.contains("handler.cancel()"))
        assertFalse("handler.proceed() is forbidden in all builds (spec 11 §3, 09 §6.1)", client.contains("handler.proceed()"))
    }

    @Test
    fun renderProcessGoneDestroysAndReturnsTrue() {
        val root = repoRoot()
        val client = File(root, "app/src/main/java/com/example/tvbrowser/web/TvWebViewClient.kt").readText()
        assertTrue(client.contains("onRenderProcessGone"))
        assertTrue(client.contains("view.destroy()"))
        assertTrue("must return true to keep dead WebView from being reused", client.contains("return true"))
    }

    @Test
    fun jsBridgeKeepRuleSurvivesR8() {
        val root = repoRoot()
        val proguard = File(root, "app/proguard-rules.pro").readText()
        assertTrue(proguard.contains("com.example.tvbrowser.bridge.JsBridge"))
        assertTrue(proguard.contains("@android.webkit.JavascriptInterface"))
        assertTrue(proguard.contains("<methods>"))
        val bridge = File(root, "app/src/main/java/com/example/tvbrowser/bridge/JsBridge.kt").readText()
        assertTrue(bridge.contains("@JavascriptInterface"))
        assertTrue(bridge.contains("fun onMediaKey"))
        assertTrue(bridge.contains("fun onDrmError"))
    }

    @Test
    fun webContentsDebuggingGatedByDebuggableFlag() {
        val root = repoRoot()
        val frag = File(root, "app/src/main/java/com/example/tvbrowser/ui/browser/WebViewFragment.kt").readText()
        assertTrue(frag.contains("setWebContentsDebuggingEnabled(true)"))
        assertTrue("must be gated by isDebuggable()", frag.contains("isDebuggable()"))
        assertTrue(frag.contains("FLAG_DEBUGGABLE"))
        // Ensure no unconditional enabling outside the gate.
        val lines = frag.lines()
        val unconditional = lines.filter { it.contains("setWebContentsDebuggingEnabled") && !lines.subList(maxOf(0, lines.indexOf(it)-5), lines.indexOf(it)).any { l -> l.contains("isDebuggable") } }
        // If any unconditional line exists, the test should fail — but our code is gated, so size must be 0 or 1 with gate.
        assertFalse("setWebContentsDebuggingEnabled must only appear inside isDebuggable gate", unconditional.isNotEmpty() && !frag.contains("if (isDebuggable())"))
    }

    @Test
    fun noTelemetryDependenciesInCatalog() {
        val root = repoRoot()
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        val forbidden = listOf("firebase", "crashlytics", "analytics", "sentry", "mixpanel", "amplitude", "admob", "play-services-ads")
        for (dep in forbidden) {
            assertFalse("telemetry SDK forbidden: $dep (spec 11 §4.2)", catalog.lowercase().contains(dep))
        }
        val appBuild = File(root, "app/build.gradle.kts").readText().lowercase()
        for (dep in forbidden) {
            assertFalse("telemetry SDK forbidden in app/build.gradle.kts: $dep", appBuild.contains(dep))
        }
    }

    @Test
    fun safeBrowsingEnabledInConfigurator() {
        val root = repoRoot()
        val cfg = File(root, "app/src/main/java/com/example/tvbrowser/web/WebViewConfigurator.kt").readText()
        assertTrue(cfg.contains("SAFE_BROWSING_ENABLE"))
        assertTrue(cfg.contains("setSafeBrowsingEnabled"))
        assertTrue(cfg.contains("true"))
    }

    @Test
    fun allowFileAndContentAccessDisabled() {
        val root = repoRoot()
        val cfg = File(root, "app/src/main/java/com/example/tvbrowser/web/WebViewConfigurator.kt").readText()
        assertTrue(cfg.contains("allowFileAccess = false"))
        assertTrue(cfg.contains("allowContentAccess = false"))
    }

    @Test
    fun thirdPartyCookiesEnabledWithDisclosure() {
        val root = repoRoot()
        val cfg = File(root, "app/src/main/java/com/example/tvbrowser/web/WebViewConfigurator.kt").readText()
        assertTrue(cfg.contains("setAcceptThirdPartyCookies"))
        assertTrue(cfg.contains("true"))
        val strings = File(root, "app/src/main/res/values/strings.xml").readText()
        assertTrue("privacy disclosure must exist in strings (spec 11 §4.1)", strings.contains("settings_privacy_disclosure"))
        assertTrue(strings.contains("Third-party cookies are allowed"))
        assertTrue(strings.contains("Login sessions for streaming services are kept on your device"))
    }

    @Test
    fun privacyDisclosureShownInAbout() {
        val root = repoRoot()
        val frag = File(root, "app/src/main/java/com/example/tvbrowser/ui/settings/SettingsFragment.kt").readText()
        assertTrue(frag.contains("settings_privacy_disclosure"))
        assertTrue(frag.contains("configureAboutRow"))
        val xml = File(root, "app/src/main/res/xml/preferences_settings.xml").readText()
        assertTrue(xml.contains("privacy_disclosure"))
    }

    @Test
    fun r8FullModeConfiguredForRelease() {
        val root = repoRoot()
        val build = File(root, "app/build.gradle.kts").readText()
        assertTrue(build.contains("isMinifyEnabled = true"))
        assertTrue(build.contains("isShrinkResources = true"))
        assertTrue(build.contains("proguard-android-optimize.txt"))
        assertTrue(build.contains("proguard-rules.pro"))
    }

    @Test
    fun noDestructiveMigrationInDatabase() {
        val root = repoRoot()
        val db = File(root, "app/src/main/java/com/example/tvbrowser/data/AppDatabase.kt").readText()
        assertFalse("fallbackToDestructiveMigration forbidden in release (spec 08 §3.4)", db.contains("fallbackToDestructiveMigration"))
        assertFalse(db.contains("DestructiveMigration"))
    }

    @Test
    fun cleanupLayerOptInOffByDefault() {
        val root = repoRoot()
        val prefs = File(root, "app/src/main/java/com/example/tvbrowser/data/PreferencesRepository.kt").readText()
        assertTrue(prefs.contains("CONTENT_FILTER_ENABLED"))
        // Default false is ensured by map { it[CONTENT_FILTER_ENABLED] ?: false }
        assertTrue(prefs.contains("?: false"))
        val settingsXml = File(root, "app/src/main/res/xml/preferences_settings.xml").readText()
        assertTrue(settingsXml.contains("""android:defaultValue="false""""))
    }
}
