package com.example.tvbrowser.web

import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.UaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentProviderTest {

    private val provider = UserAgentProvider { "WebViewDefault/1.0" }

    private fun bookmark(mode: UaMode) = Bookmark(
        id = 1,
        title = "T",
        url = "https://x.tv",
        origin = "https://x.tv",
        uaMode = mode,
        textZoomPercent = 100
    )

    @Test
    fun desktopIsDefaultAndWindows() {
        val ua = provider.resolve(bookmark(UaMode.DESKTOP))
        assertTrue(ua.contains("Windows NT 10.0"))
        assertFalse(ua.contains("Mobile"))
    }

    @Test
    fun desktopMatchesPinnedTemplate() {
        assertEquals(UserAgentProvider.DESKTOP_UA, provider.resolve(bookmark(UaMode.DESKTOP)))
    }

    @Test
    fun mobileContainsMobileToken() {
        val ua = provider.resolve(bookmark(UaMode.MOBILE))
        assertTrue(ua.contains("Mobile Safari"))
    }

    @Test
    fun mobileMatchesPinnedTemplate() {
        assertEquals(UserAgentProvider.MOBILE_UA, provider.resolve(bookmark(UaMode.MOBILE)))
    }

    @Test
    fun nativeTvUsesWebViewDefault() {
        assertEquals(
            "WebViewDefault/1.0",
            provider.resolve(bookmark(UaMode.NATIVE_TV))
        )
    }

    @Test
    fun templatesPinSameChromeMajorToken() {
        val pattern = Regex("Chrome/(\\d+)\\.0\\.0\\.0")
        val desktop = pattern.find(UserAgentProvider.DESKTOP_UA)?.groupValues?.get(1)
        val mobile = pattern.find(UserAgentProvider.MOBILE_UA)?.groupValues?.get(1)
        assertEquals("126", desktop)
        assertEquals(desktop, mobile)
    }
}
