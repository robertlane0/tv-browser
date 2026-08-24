package com.example.tvbrowser.ui.browser

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.example.tvbrowser.R
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.input.CssInjector
import com.example.tvbrowser.input.MediaKeyInjector
import com.example.tvbrowser.input.RemoteInputHandler
import com.example.tvbrowser.web.TvWebViewClient
import com.example.tvbrowser.web.UserAgentProvider
import com.example.tvbrowser.web.WebViewConfigurator

class WebViewFragment : Fragment() {

    private var webView: WebView? = null
    private var inputHandler: RemoteInputHandler? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_web_view, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        val bookmark = arguments?.getParcelable<Bookmark>(BrowserActivity.EXTRA_BOOKMARK) ?: run {
            activity?.finish()
            return
        }

        val webView = view.findViewById<WebView>(R.id.web_view)
        this.webView = webView

        if (isDebuggable()) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        WebViewConfigurator(UserAgentProvider {
            WebSettings.getDefaultUserAgent(requireContext())
        }).configure(webView, bookmark)

        webView.webViewClient = TvWebViewClient(
            CssInjector { name ->
                requireContext().assets.open(name).bufferedReader().use { it.readText() }
            }
        )

        val overlay = object : BrowserOverlayController {
            override val isVisible: Boolean = false
            override fun show() {}
            override fun hide() {}
            override fun toggle() {}
        }

        inputHandler = RemoteInputHandler(
            webView,
            overlay,
            MediaKeyInjector(webView),
            onExit = { activity?.finish() }
        )

        webView.loadUrl(bookmark.url)
    }

    fun dispatchKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        inputHandler?.onKeyDown(keyCode, event) ?: false

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = inputHandler ?: return false
        if (!handler.isMediaKey(event.keyCode)) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            handler.onKeyDown(event.keyCode, event)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroyView() {
        webView?.let { dying ->
            (dying.parent as? ViewGroup)?.removeView(dying)
            dying.destroy()
        }
        webView = null
        super.onDestroyView()
    }

    private fun isDebuggable(): Boolean =
        requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        fun newInstance(bookmark: Bookmark): WebViewFragment =
            WebViewFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(BrowserActivity.EXTRA_BOOKMARK, bookmark)
                }
            }
    }
}
