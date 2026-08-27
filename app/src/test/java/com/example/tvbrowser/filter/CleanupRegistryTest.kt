package com.example.tvbrowser.filter

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CleanupRegistryTest {

    private val registry = CleanupRegistry.loadFromAssets("cleanup_registry.json")

    @Test
    fun noSelectorTargetsPlayerElements() {
        val banned = listOf("player", "video", "player-container")
        registry.allSelectors().forEach { sel ->
            banned.forEach { token ->
                assertFalse(
                    "selector '$sel' targets '$token'",
                    sel.contains(token, ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun registryHasVersionAndValidOrigins() {
        assertTrue(registry.version >= 1)
        registry.sites().forEach {
            assertTrue("origin '${it.origin}' must start with https://", it.origin.startsWith("https://"))
        }
    }

    @Test
    fun genericAndSiteSelectorsCombinedForOrigin() {
        val genericCount = registry.generic.hideSelectors.size + registry.generic.closeButtonSelectors.size
        assertTrue(genericCount > 0)
        val forKnown = registry.selectorsFor("https://example-regional-tv.example")
        // site-specific selector must be included
        assertTrue(forKnown.any { it == "#unclosable-promo" })
        val forUnknown = registry.selectorsFor("https://unknown.example")
        assertTrue(forUnknown.size == genericCount)
        assertFalse(forUnknown.contains("#unclosable-promo"))
    }

    @Test
    fun corruptJsonDisablesFeatureWithoutThrowing() {
        val parsed = CleanupRegistry.parse("not json at all")
        assertTrue(parsed.version == 0)
        assertTrue(parsed.allSelectors().isEmpty())
        assertTrue(parsed.selectorsFor("https://example.com").isEmpty())
    }
}
