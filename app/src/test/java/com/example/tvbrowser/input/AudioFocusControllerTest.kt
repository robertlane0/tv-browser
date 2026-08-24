package com.example.tvbrowser.input

import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AudioFocusControllerTest {

    private lateinit var host: WebViewTestHost
    private lateinit var webView: android.webkit.WebView
    private lateinit var controller: AudioFocusController

    @Before
    fun setUp() {
        host = WebViewTestHost()
        webView = host.attachedWebView()
        controller = AudioFocusController(host.activity, MediaKeyInjector(webView))
    }

    @Test
    fun focusLossPausesWithoutEverResuming() {
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        val js = shadowOf(webView).lastEvaluatedJavascript!!
        assertTrue(js.contains("v.pause()"))
        assertTrue("loss must never trigger playback", !js.contains("v.play()"))
        assertTrue(js.contains("if(!v||v.paused)return"))
    }

    @Test
    fun transientFocusLossPausesPlayback() {
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        assertTrue(shadowOf(webView).lastEvaluatedJavascript!!.contains("v.pause()"))
    }

    @Test
    fun focusGainNeverAutoResumes() {
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun duckingIsIgnoredUntilLossSurfaces() {
        controller.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun requestGrantsAndAbandonReleasesListener() {
        assertTrue(controller.request())

        controller.abandon()

        assertSame(controller, shadowOf(host.activity.getSystemService(
            android.content.Context.AUDIO_SERVICE) as AudioManager
        ).lastAbandonedAudioFocusListener)
    }
}
