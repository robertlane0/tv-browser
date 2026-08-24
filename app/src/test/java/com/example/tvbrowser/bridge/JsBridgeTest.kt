package com.example.tvbrowser.bridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class JsBridgeTest {

    @Test
    fun drmErrorsReachListenerSynchronouslyFromMainThread() {
        val received = mutableListOf<String>()
        val bridge = JsBridge { received.add(it) }

        bridge.onDrmError("NotSupportedError: no key system")

        assertEquals(listOf("NotSupportedError: no key system"), received)
    }

    @Test
    fun nullDrmMessageIsDropped() {
        var calls = 0
        val bridge = JsBridge { calls++ }

        bridge.onDrmError(null)

        assertEquals(0, calls)
    }

    @Test
    fun defaultBridgeIsSafeWithoutListener() {
        val bridge = JsBridge()

        bridge.onDrmError("anything")
    }

    @Test
    fun interfaceNameIsStableForInjectedScript() {
        assertEquals("TvBrowser", JsBridge.JS_INTERFACE_NAME)
        assertTrue(
            "injected EME hook must call back through this interface",
            com.example.tvbrowser.web.EmeErrorHook.SCRIPT.contains(JsBridge.JS_INTERFACE_NAME)
        )
    }
}
