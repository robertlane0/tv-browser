package com.example.tvbrowser.ui.home

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.example.tvbrowser.R
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import com.example.tvbrowser.data.UaMode
import kotlinx.coroutines.launch

class BookmarkPlaybackStep : GuidedStepSupportFragment() {

    private var title = ""
    private var url = ""
    private var existing: Bookmark? = null
    private var duplicateWarning = false

    private lateinit var repository: BookmarkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        title = args.getString(ARG_TITLE).orEmpty()
        url = args.getString(ARG_URL).orEmpty()
        @Suppress("DEPRECATION")
        existing = args.getParcelable(ARG_BOOKMARK)
        duplicateWarning = args.getBoolean(ARG_DUPLICATE_WARNING, false)
        repository = BookmarkRepository(AppDatabase.getInstance(requireContext()).bookmarkDao())
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val base = getString(R.string.bookmark_playback_description)
        val description = if (duplicateWarning) "$base\n${getString(R.string.bookmark_details_duplicate_warning)}" else base
        return GuidanceStylist.Guidance(
            getString(R.string.bookmark_playback_title),
            description,
            null,
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val bookmark = existing
        val debuggable =
            requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        uaActions(actions, bookmark?.uaMode ?: UaMode.DESKTOP, debuggable)
        zoomActions(actions, bookmark?.textZoomPercent ?: 100)
        bannerActions(actions, bookmark?.bannerUri)
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_USE_DEFAULTS)
                .title(getString(R.string.bookmark_action_use_defaults))
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SAVE)
                .title(getString(R.string.bookmark_action_save))
                .build()
        )
    }

    private fun uaActions(actions: MutableList<GuidedAction>, selected: UaMode, debuggable: Boolean) {
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_UA_DESKTOP)
                .title(getString(R.string.ua_mode_desktop))
                .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                .checked(selected == UaMode.DESKTOP)
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_UA_MOBILE)
                .title(getString(R.string.ua_mode_mobile))
                .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                .checked(selected == UaMode.MOBILE)
                .build()
        )
        if (debuggable) {
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_UA_NATIVE_TV)
                    .title(getString(R.string.ua_mode_native_tv_debug))
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .checked(selected == UaMode.NATIVE_TV)
                    .build()
            )
        }
    }

    private fun zoomActions(actions: MutableList<GuidedAction>, selectedPercent: Int) {
        listOf(
            Triple(ACTION_ZOOM_100, R.string.text_zoom_standard, 100),
            Triple(ACTION_ZOOM_125, R.string.text_zoom_large, 125),
            Triple(ACTION_ZOOM_150, R.string.text_zoom_extra_large, 150)
        ).forEach { (id, labelRes, percent) ->
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(id)
                    .title(getString(labelRes))
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .checked(selectedPercent == percent)
                    .build()
            )
        }
    }

    private fun bannerActions(actions: MutableList<GuidedAction>, selectedUri: String?) {
        listOf(
            Triple(ACTION_BANNER_DEFAULT, R.string.banner_default, null as String?),
            Triple(ACTION_BANNER_AURORA, R.string.banner_aurora, "preset_banner_aurora"),
            Triple(ACTION_BANNER_SUNSET, R.string.banner_sunset, "preset_banner_sunset"),
            Triple(ACTION_BANNER_FOREST, R.string.banner_forest, "preset_banner_forest")
        ).forEach { (id, labelRes, entryName) ->
            val uri = entryName?.let { "android.resource://${requireContext().packageName}/$it" }
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(id)
                    .title(getString(labelRes))
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .checked((selectedUri ?: "") == (uri ?: ""))
                    .build()
            )
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_USE_DEFAULTS -> save(useDefaults = true)
            ACTION_SAVE -> save(useDefaults = false)
        }
    }

    internal fun save(useDefaults: Boolean) {
        val bookmark = existing
        val uaMode = if (useDefaults) {
            UaMode.DESKTOP
        } else when (findCheckedActionId(ACTION_UA_DESKTOP, ACTION_UA_MOBILE, ACTION_UA_NATIVE_TV)) {
            ACTION_UA_MOBILE -> UaMode.MOBILE
            ACTION_UA_NATIVE_TV -> UaMode.NATIVE_TV
            else -> UaMode.DESKTOP
        }
        val textZoom = if (useDefaults) {
            100
        } else when (findCheckedActionId(ACTION_ZOOM_100, ACTION_ZOOM_125, ACTION_ZOOM_150)) {
            ACTION_ZOOM_125 -> 125
            ACTION_ZOOM_150 -> 150
            else -> 100
        }
        val bannerUri = if (useDefaults) {
            bookmark?.bannerUri
        } else when (findCheckedActionId(
            ACTION_BANNER_DEFAULT,
            ACTION_BANNER_AURORA,
            ACTION_BANNER_SUNSET,
            ACTION_BANNER_FOREST
        )) {
            ACTION_BANNER_AURORA -> presetBannerUri("preset_banner_aurora")
            ACTION_BANNER_SUNSET -> presetBannerUri("preset_banner_sunset")
            ACTION_BANNER_FOREST -> presetBannerUri("preset_banner_forest")
            else -> null
        }

        val saved = (bookmark ?: Bookmark(title = title, url = url, origin = Bookmark.originOf(url)))
            .copy(title = title, url = url, origin = Bookmark.originOf(url), uaMode = uaMode, textZoomPercent = textZoom, bannerUri = bannerUri)

        viewLifecycleOwner.lifecycleScope.launch {
            repository.upsert(saved)
            Toast.makeText(
                requireContext(),
                getString(R.string.toast_service_saved, saved.title),
                Toast.LENGTH_SHORT
            ).show()
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    private fun findCheckedActionId(vararg ids: Long): Long? =
        ids.firstOrNull { id -> findActionById(id)?.isChecked == true }

    private fun presetBannerUri(entryName: String): String =
        "android.resource://${requireContext().packageName}/$entryName"

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_URL = "url"
        private const val ARG_BOOKMARK = "bookmark"
        private const val ARG_DUPLICATE_WARNING = "duplicate_warning"

        private const val ACTION_UA_DESKTOP = 10L
        private const val ACTION_UA_MOBILE = 11L
        private const val ACTION_UA_NATIVE_TV = 12L
        private const val ACTION_ZOOM_100 = 13L
        private const val ACTION_ZOOM_125 = 14L
        private const val ACTION_ZOOM_150 = 15L
        private const val ACTION_BANNER_DEFAULT = 16L
        private const val ACTION_BANNER_AURORA = 17L
        private const val ACTION_BANNER_SUNSET = 18L
        private const val ACTION_BANNER_FOREST = 19L
        private const val ACTION_USE_DEFAULTS = 20L
        private const val ACTION_SAVE = 21L

        fun newInstance(title: String, url: String, existing: Bookmark?, duplicateWarning: Boolean): BookmarkPlaybackStep =
            BookmarkPlaybackStep().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_URL, url)
                    putParcelable(ARG_BOOKMARK, existing)
                    putBoolean(ARG_DUPLICATE_WARNING, duplicateWarning)
                }
            }
    }
}
