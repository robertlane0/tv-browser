package com.example.tvbrowser.input

import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import org.robolectric.Robolectric

class WebViewTestHost {

    val activity: FragmentActivity =
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    fun attachedWebView(): WebView =
        WebView(activity).also { root().addView(it) }

    fun <T : WebView> attach(view: T): T = view.also { root().addView(it) }

    fun detachedWebView(): WebView = WebView(activity)

    private fun root(): ViewGroup = activity.findViewById(android.R.id.content)
}
