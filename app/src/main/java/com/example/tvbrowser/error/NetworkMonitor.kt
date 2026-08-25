package com.example.tvbrowser.error

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Connectivity watcher backing the offline banner of spec 09 §6.3. Uses a
 * thin [ConnectivityManager.NetworkCallback] registration (API 21+).
 */
class NetworkMonitor(context: Context) {

    interface Listener {
        fun onNetworkLost()

        fun onNetworkAvailable()
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var registered: ConnectivityManager.NetworkCallback? = null

    fun register(listener: Listener) {
        unregister()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                listener.onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                listener.onNetworkLost()
            }
        }
        registered = callback
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }
        // Reflect current state so the banner is correct without a transition.
        if (isOffline()) listener.onNetworkLost()
    }

    fun unregister() {
        val callback = registered ?: return
        registered = null
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun isOffline(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        connectivityManager.activeNetwork == null
    } else {
        @Suppress("DEPRECATION")
        connectivityManager.activeNetworkInfo?.isConnected != true
    }
}
