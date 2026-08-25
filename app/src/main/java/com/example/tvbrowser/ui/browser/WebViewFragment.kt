package com.example.tvbrowser.ui.browser

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tvbrowser.R
import com.example.tvbrowser.bridge.JsBridge
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import com.example.tvbrowser.data.UaMode
import com.example.tvbrowser.error.DrmCardController
import com.example.tvbrowser.input.AudioFocusController
import com.example.tvbrowser.input.CssInjector
import com.example.tvbrowser.input.MediaKeyInjector
import com.example.tvbrowser.input.RemoteInputHandler
import com.example.tvbrowser.ui.settings.SettingsActivity
import com.example.tvbrowser.web.EmeErrorHook
import com.example.tvbrowser.web.TvWebViewClient
import com.example.tvbrowser.web.TvWebChromeClient
import com.example.tvbrowser.web.UserAgentProvider
import com.example.tvbrowser.web.WebViewConfigurator
import kotlinx.coroutines.launch

class WebViewFragment : Fragment(), AddressInputStep.Host, DpadKeyGridStep.Host {

    private var webView: WebView? = null
    private var inputHandler: RemoteInputHandler? = null
    private var chromeClient: TvWebChromeClient? = null
    private var audioFocus: AudioFocusController? = null
    private var drmCard: DrmCardController? = null
    private var overlay: BrowserOverlay? = null
    private lateinit var repository: BookmarkRepository
    private lateinit var userAgentProvider: UserAgentProvider

    private val bookmarkOrigins = mutableSetOf<String>()

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

        repository = BookmarkRepository(AppDatabase.getInstance(requireContext()).bookmarkDao())

        val webView = view.findViewById<WebView>(R.id.web_view)
        this.webView = webView

        if (isDebuggable()) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val userAgentProvider = UserAgentProvider {
            WebSettings.getDefaultUserAgent(requireContext())
        }
        this.userAgentProvider = userAgentProvider
        WebViewConfigurator(userAgentProvider).configure(webView, bookmark)

        val drmCard = DrmCardController(
            requireActivity().findViewById(R.id.drm_error_card)
        ) { webView.requestFocus() }
        this.drmCard = drmCard

        webView.addJavascriptInterface(
            JsBridge { drmCard.show() },
            JsBridge.JS_INTERFACE_NAME
        )
        val emeHook = EmeErrorHook()
        emeHook.attach(webView)

        val mediaKeys = MediaKeyInjector(webView)
        val audioFocus = AudioFocusController(requireContext(), mediaKeys)
        this.audioFocus = audioFocus

        val chromeClient = TvWebChromeClient(
            requireActivity(),
            requireActivity().findViewById(R.id.fullscreen_container),
            webView,
            requireActivity().findViewById<ProgressBar>(R.id.web_progress),
            titleCallback = { activity?.title = it },
            audioFocus = audioFocus
        )
        this.chromeClient = chromeClient
        webView.webChromeClient = chromeClient

        webView.webViewClient = TvWebViewClient(
            CssInjector { name ->
                requireContext().assets.open(name).bufferedReader().use { it.readText() }
            },
            chromeClient,
            emeHook::injectIfNeeded
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeAll().collect { bookmarks ->
                    bookmarkOrigins.clear()
                    bookmarkOrigins.addAll(bookmarks.map { it.origin })
                    overlay?.refresh()
                }
            }
        }

        val overlayBar = view.findViewById<View>(R.id.browser_overlay)
        val overlay = BrowserOverlay(
            bar = overlayBar,
            webView = webView,
            fullscreen = chromeClient,
            isPlaybackActive = { mediaKeys.isPlayingAssumed },
            isCurrentPageBookmarked = {
                webView.url?.let { bookmarkOrigins.contains(Bookmark.originOf(it)) } == true
            },
            onHomeRequested = { activity?.finish() },
            onAddressClicked = { openAddressEntry() },
            onBookmarkToggled = { toggleBookmarkForCurrentPage() },
            onSettingsRequested = { launchSettings(bookmark) }
        )
        this.overlay = overlay

        inputHandler = RemoteInputHandler(
            webView,
            overlay,
            mediaKeys,
            onExit = { activity?.finish() },
            fullscreen = chromeClient
        )

        webView.loadUrl(bookmark.url)
    }

    fun dispatchKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (hasModalStep()) return false
        return inputHandler?.onKeyDown(keyCode, event) ?: false
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hasModalStep()) return false
        val handler = inputHandler ?: return false
        if (!handler.isMediaKey(event.keyCode)) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            handler.onKeyDown(event.keyCode, event)
        }
        return true
    }

    fun notifyUserInteraction() {
        overlay?.onUserInteraction()
    }

    fun forceExitFullscreen() {
        chromeClient?.exitFullscreen()
    }

    fun reloadWithSessionBookmark() {
        val session = sessionBookmark() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val fresh = repository.findById(session.id) ?: return@launch
            val target = webView ?: return@launch
            target.settings.userAgentString = userAgentProvider.resolve(fresh)
            target.settings.textZoom = fresh.textZoomPercent
            target.reload()
        }
    }

    internal val activeChromeClient: TvWebChromeClient?
        get() = chromeClient

    internal val activeDrmCard: DrmCardController?
        get() = drmCard

    internal val activeOverlay: BrowserOverlay?
        get() = overlay

    override fun onAddressCommitted(normalizedUrl: String) {
        overlay?.setPinned(false)
        overlay?.hide()
        webView?.loadUrl(normalizedUrl)
    }

    override fun onAddressEntryCancelled() {
        if (!hasModalStep()) {
            overlay?.setPinned(false)
        }
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
        overlay?.destroy()
        overlay = null
        audioFocus?.abandon()
        drmCard = null
        chromeClient = null
        inputHandler = null
        webView?.let { dying ->
            (dying.parent as? ViewGroup)?.removeView(dying)
            dying.destroy()
        }
        webView = null
        super.onDestroyView()
    }

    private fun hasModalStep(): Boolean =
        childFragmentManager.fragments.isNotEmpty()

    private fun openAddressEntry() {
        val currentUrl = webView?.url.orEmpty()
        val step = if (imeAvailable()) {
            AddressInputStep.newInstance(currentUrl)
        } else {
            DpadKeyGridStep.newInstance(currentUrl)
        }
        childFragmentManager.beginTransaction()
            .add(R.id.overlay_step_container, step, MODAL_STEP_TAG)
            .addToBackStack(null)
            .commit()
    }

    private fun imeAvailable(): Boolean = runCatching {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.isNotEmpty()
    }.getOrDefault(true)

    private fun toggleBookmarkForCurrentPage() {
        val url = webView?.url ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val origin = Bookmark.originOf(url)
            val existing = repository.findByOrigin(origin)
            if (existing != null) {
                repository.delete(existing)
            } else {
                repository.upsert(
                    Bookmark(
                        title = activity?.title?.toString().orEmpty().ifEmpty { url },
                        url = url,
                        origin = origin,
                        uaMode = sessionBookmark()?.uaMode ?: UaMode.DESKTOP,
                        textZoomPercent = sessionBookmark()?.textZoomPercent ?: 100
                    )
                )
            }
            bookmarkOrigins.run {
                if (existing != null) remove(origin) else add(origin)
            }
            overlay?.refresh()
        }
    }

    private fun sessionBookmark(): Bookmark? =
        arguments?.getParcelable(BrowserActivity.EXTRA_BOOKMARK)

    private fun launchSettings(sessionBookmark: Bookmark) {
        requireActivity().startActivityForResult(
            SettingsActivity.createIntent(requireContext(), sessionBookmark),
            SettingsActivity.REQUEST_SETTINGS
        )
    }

    private fun isDebuggable(): Boolean =
        requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        const val MODAL_STEP_TAG = "overlay_modal_step"

        fun newInstance(bookmark: Bookmark): WebViewFragment =
            WebViewFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(BrowserActivity.EXTRA_BOOKMARK, bookmark)
                }
            }
    }
}
