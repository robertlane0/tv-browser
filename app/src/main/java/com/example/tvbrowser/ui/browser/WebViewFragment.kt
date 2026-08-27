package com.example.tvbrowser.ui.browser

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tvbrowser.R
import com.example.tvbrowser.bridge.JsBridge
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import com.example.tvbrowser.data.PreferencesRepository
import com.example.tvbrowser.data.UaMode
import com.example.tvbrowser.filter.CleanupInjector
import com.example.tvbrowser.filter.CleanupRegistry
import com.example.tvbrowser.error.AutoRetryController
import com.example.tvbrowser.error.Category
import com.example.tvbrowser.error.DrmCardController
import com.example.tvbrowser.error.ErrorCardController
import com.example.tvbrowser.error.ErrorCardView
import com.example.tvbrowser.error.ErrorClassifier
import com.example.tvbrowser.error.NetworkMonitor
import com.example.tvbrowser.error.RedirectLoopDetector
import com.example.tvbrowser.error.RendererRecoveryPolicy
import com.example.tvbrowser.error.RetryPolicy
import com.example.tvbrowser.error.TvError
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class WebViewFragment :
    Fragment(),
    AddressInputStep.Host,
    DpadKeyGridStep.Host,
    NetworkMonitor.Listener {

    private var webView: WebView? = null
    private var inputHandler: RemoteInputHandler? = null
    private var chromeClient: TvWebChromeClient? = null
    private var audioFocus: AudioFocusController? = null
    private var drmCard: DrmCardController? = null
    private var overlay: BrowserOverlay? = null
    private var errorCard: ErrorCardController? = null
    private var autoRetry: AutoRetryController? = null
    private var jsBridge: JsBridge? = null
    private var mediaKeys: MediaKeyInjector? = null
    private var networkMonitor: NetworkMonitor? = null
    private lateinit var launchBookmark: Bookmark
    private lateinit var repository: BookmarkRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var userAgentProvider: UserAgentProvider
    private lateinit var classifier: ErrorClassifier
    private lateinit var loopDetector: RedirectLoopDetector
    private var cleanupRegistry: CleanupRegistry? = null
    private var cleanupInjector: CleanupInjector? = null
    @Volatile
    private var isCleanupEnabled: Boolean = false

    internal var rendererRecovery: RendererRecoveryPolicy = RendererRecoveryPolicy()

    private val bookmarkOrigins = mutableSetOf<String>()
    private var currentUrl: String? = null
    private var lastFailedUrl: String? = null

    private val pageListener = object : TvWebViewClient.Listener {
        override fun onMainFrameNavigation(url: String) {
            // Track only; error surfaces stay until explicitly dismissed,
            // retried, or replaced so loop-blocked cards survive redirects.
            currentUrl = url
        }

        override fun onWebError(error: TvError) = showErrorCard(error)

        override fun onRendererGone(didCrash: Boolean) = handleRendererGone()
    }

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
        launchBookmark = bookmark

        repository = BookmarkRepository(AppDatabase.getInstance(requireContext()).bookmarkDao())
        preferencesRepository = PreferencesRepository.getInstance(requireContext())

        val userAgentProvider = UserAgentProvider {
            WebSettings.getDefaultUserAgent(requireContext())
        }
        this.userAgentProvider = userAgentProvider
        classifier = ErrorClassifier()
        loopDetector = RedirectLoopDetector()

        cleanupRegistry = runCatching {
            CleanupRegistry.loadFromAssets(requireContext(), "cleanup_registry.json")
        }.getOrElse { CleanupRegistry.parse("""{"version":0,"generic":{}}""") }
        cleanupInjector = cleanupRegistry?.let { reg ->
            CleanupInjector(reg) { isCleanupEnabled }
        }

        val activity = requireActivity()

        val drmCard = DrmCardController(
            activity.findViewById(R.id.drm_error_card)
        ) { webView?.requestFocus() }
        this.drmCard = drmCard

        jsBridge = JsBridge { drmCard.show() }

        val cardView = activity.findViewById<ErrorCardView>(R.id.error_card)
        errorCard = ErrorCardController(
            card = cardView,
            iconView = activity.findViewById(R.id.error_icon),
            titleView = activity.findViewById(R.id.error_title),
            bodyView = activity.findViewById(R.id.error_body),
            retryButton = activity.findViewById(R.id.btn_error_retry),
            switchUaButton = activity.findViewById(R.id.btn_error_switch_ua),
            homeButton = activity.findViewById(R.id.btn_error_home),
            onRetry = ::retryManually,
            onSwitchUserAgent = { launchSettings(launchBookmark) },
            onHome = { activity.finish() },
            refocusPage = ::returnFocusToPage
        )
        cardView.onAnyKeyWhileVisible = {
            autoRetry?.cancelPending()
        }

        autoRetry = AutoRetryController(
            classifier,
            RetryPolicy(),
            Handler(Looper.getMainLooper()),
            onRetry = ::performAutomaticRetry
        )

        networkMonitor = NetworkMonitor(requireContext().applicationContext)

        // Synchronous initial read so the first page load respects the stored opt-in
        // (subsequent updates arrive via the collector below). Best-effort: if
        // DataStore is unavailable, default to disabled and rely on the flow.
        isCleanupEnabled = runCatching {
            runBlocking { preferencesRepository.contentFilterEnabled().first() }
        }.getOrDefault(false)

        installWebView(
            savedInstanceState?.getString(KEY_RESTORE_URL)?.takeUnless { it.isBlank() }
                ?: bookmark.url
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferencesRepository.contentFilterEnabled().collect { enabled ->
                    isCleanupEnabled = enabled
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeAll().collect { bookmarks ->
                    bookmarkOrigins.clear()
                    bookmarkOrigins.addAll(bookmarks.map { it.origin })
                    overlay?.refresh()
                }
            }
        }
    }

    fun dispatchKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        autoRetry?.cancelPending()
        if (hasModalStep() || webView == null) return false
        return inputHandler?.onKeyDown(keyCode, event) ?: false
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        autoRetry?.cancelPending()
        if (hasModalStep() || webView == null) return false
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
        clearErrorSurface(resetRetries = true)
        loopDetector.reset()
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

    internal val activeErrorCard: ErrorCardController?
        get() = errorCard

    /**
     * Creates and wires a fully configured WebView (spec 03 matrix) inside the
     * fragment container. Re-run after renderer death to recover with a fresh
     * engine (spec 09 §5).
     */
    private fun installWebView(initialUrl: String) {
        val activity = requireActivity()
        val root = requireView()
        overlay?.destroy()

        val container = root.findViewById<FrameLayout>(R.id.web_view_container)
        container.removeAllViews()

        val webView = WebView(activity).apply {
            id = R.id.web_view
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(webView)
        this.webView = webView

        if (isDebuggable()) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        WebViewConfigurator(userAgentProvider).configure(webView, launchBookmark)

        val bridge = jsBridge ?: JsBridge { drmCard?.show() }.also { jsBridge = it }
        webView.addJavascriptInterface(bridge, JsBridge.JS_INTERFACE_NAME)
        val emeHook = EmeErrorHook()
        emeHook.attach(webView)

        val mediaKeys = MediaKeyInjector(webView)
        this.mediaKeys = mediaKeys
        audioFocus?.abandon()
        val audioFocus = AudioFocusController(requireContext(), mediaKeys)
        this.audioFocus = audioFocus

        val chromeClient = TvWebChromeClient(
            activity,
            activity.findViewById(R.id.fullscreen_container),
            webView,
            activity.findViewById<ProgressBar>(R.id.web_progress),
            titleCallback = { activity.title = it },
            audioFocus = audioFocus
        )
        this.chromeClient = chromeClient
        webView.webChromeClient = chromeClient

        webView.webViewClient = TvWebViewClient(
            CssInjector { name ->
                requireContext().assets.open(name).bufferedReader().use { it.readText() }
            },
            chromeClient,
            emeHook::injectIfNeeded,
            loopDetector,
            classifier,
            pageListener,
            cleanupInjector
        )

        val overlayBar = root.findViewById<View>(R.id.browser_overlay)
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
            onSettingsRequested = { launchSettings(launchBookmark) }
        )
        this.overlay = overlay

        inputHandler = RemoteInputHandler(
            webView,
            overlay,
            mediaKeys,
            onExit = { activity?.finish() },
            fullscreen = chromeClient
        )

        currentUrl = initialUrl
        webView.loadUrl(initialUrl)
    }

    private fun showErrorCard(error: TvError, homeOnly: Boolean = false) {
        val card = errorCard ?: return
        autoRetry?.cancelPending()
        lastFailedUrl = currentUrl ?: webView?.url ?: launchBookmark.url
        webView?.visibility = View.INVISIBLE
        overlay?.hide()
        card.show(error.category, error.httpCode, homeOnly)
        autoRetry?.scheduleAfterFailure(error)
    }

    private fun clearErrorSurface(resetRetries: Boolean) {
        if (resetRetries) autoRetry?.reset() else autoRetry?.cancelPending()
        errorCard?.dismiss()
        webView?.visibility = View.VISIBLE
    }

    private fun returnFocusToPage() {
        val target = webView ?: return
        target.visibility = View.VISIBLE
        // Defer until the pending focus-clear from the dismissed card has been
        // processed, otherwise the request is overwritten.
        target.post { if (webView === target) target.requestFocus() }
    }

    private fun retryManually() {
        val target = lastFailedUrl ?: return
        loopDetector.reset()
        autoRetry?.reset()
        errorCard?.dismiss()
        returnFocusToPage()
        loadInWebView(target)
    }

    private fun performAutomaticRetry() {
        val target = lastFailedUrl ?: return
        errorCard?.dismiss()
        returnFocusToPage()
        loadInWebView(target)
    }

    private fun loadInWebView(url: String) {
        autoRetry?.cancelPending()
        currentUrl = url
        webView?.loadUrl(url)
    }

    private fun handleRendererGone() {
        autoRetry?.cancelPending()
        if (!rendererRecovery.shouldAutoRecover()) {
            webView = null
            showErrorCard(TvError(Category.RENDERER), homeOnly = true)
            return
        }
        installWebView(lastKnownUrl())
    }

    private fun lastKnownUrl(): String =
        currentUrl ?: lastFailedUrl ?: launchBookmark.url

    override fun onNetworkLost() {
        activity?.findViewById<View>(R.id.offline_banner)?.isVisible = true
        autoRetry?.cancelPending()
    }

    override fun onNetworkAvailable() {
        activity?.findViewById<View>(R.id.offline_banner)?.isVisible = false
        errorCard?.visibleCategory()
            ?.takeIf { it == Category.NETWORK }
            ?.let { autoRetry?.retryNowIfEligible(it) }
    }

    override fun onStart() {
        super.onStart()
        networkMonitor?.register(this)
    }

    override fun onStop() {
        networkMonitor?.unregister()
        super.onStop()
    }

    override fun onAddressCommitted(normalizedUrl: String) {
        overlay?.setPinned(false)
        overlay?.hide()
        clearErrorSurface(resetRetries = true)
        loopDetector.reset()
        loadInWebView(normalizedUrl)
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
        autoRetry?.cancelPending()
        webView?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_RESTORE_URL, currentUrl ?: webView?.url)
    }

    override fun onDestroyView() {
        autoRetry?.cancelPending()
        networkMonitor?.unregister()
        overlay?.destroy()
        overlay = null
        audioFocus?.abandon()
        drmCard = null
        errorCard = null
        autoRetry = null
        chromeClient = null
        inputHandler = null
        jsBridge = null
        mediaKeys = null
        networkMonitor = null
        cleanupInjector = null
        cleanupRegistry = null
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
        const val KEY_RESTORE_URL = "com.example.tvbrowser.extra.RESTORE_URL"

        fun newInstance(bookmark: Bookmark): WebViewFragment =
            WebViewFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(BrowserActivity.EXTRA_BOOKMARK, bookmark)
                }
            }
    }
}
