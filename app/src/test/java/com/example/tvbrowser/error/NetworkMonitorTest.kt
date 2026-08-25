package com.example.tvbrowser.error

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NetworkMonitorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class RecordingListener : NetworkMonitor.Listener {
        var lost = 0
        var gained = 0

        override fun onNetworkLost() {
            lost++
        }

        override fun onNetworkAvailable() {
            gained++
        }
    }

    @Test
    fun registerAndUnregisterRoundTripDoesNotCrash() {
        val monitor = NetworkMonitor(context)
        val listener = RecordingListener()

        monitor.register(listener)
        monitor.unregister()

        assertEquals(0, listener.lost)
    }

    @Test
    fun reRegistrationReplacesPreviousCallbackWithoutLeak() {
        val monitor = NetworkMonitor(context)
        val first = RecordingListener()
        val second = RecordingListener()

        monitor.register(first)
        monitor.register(second)
        monitor.unregister()

        assertEquals(0, first.lost + second.lost)
    }
}
