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

class UaPickerStep : GuidedStepSupportFragment() {

    private lateinit var bookmark: Bookmark
    private lateinit var repository: BookmarkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        bookmark = requireArguments().getParcelable(ARG_BOOKMARK)!!
        repository = BookmarkRepository(AppDatabase.getInstance(requireContext()).bookmarkDao())
        super.onCreate(savedInstanceState)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            getString(R.string.ua_picker_title),
            getString(R.string.ua_picker_description),
            null,
            null
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val debuggable =
            requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_UA_DESKTOP)
                .title(getString(R.string.ua_mode_desktop))
                .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                .checked(bookmark.uaMode == UaMode.DESKTOP)
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_UA_MOBILE)
                .title(getString(R.string.ua_mode_mobile))
                .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                .checked(bookmark.uaMode == UaMode.MOBILE)
                .build()
        )
        if (debuggable) {
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_UA_NATIVE_TV)
                    .title(getString(R.string.ua_mode_native_tv_debug))
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .checked(bookmark.uaMode == UaMode.NATIVE_TV)
                    .build()
            )
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val mode = when (action.id) {
            ACTION_UA_DESKTOP -> UaMode.DESKTOP
            ACTION_UA_MOBILE -> UaMode.MOBILE
            ACTION_UA_NATIVE_TV -> UaMode.NATIVE_TV
            else -> null
        } ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            repository.upsert(bookmark.copy(uaMode = mode))
            Toast.makeText(requireContext(), R.string.toast_ua_changed, Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    companion object {
        private const val ARG_BOOKMARK = "bookmark"
        private const val ACTION_UA_DESKTOP = 1L
        private const val ACTION_UA_MOBILE = 2L
        private const val ACTION_UA_NATIVE_TV = 3L

        fun newInstance(bookmark: Bookmark): UaPickerStep =
            UaPickerStep().apply {
                arguments = Bundle().apply { putParcelable(ARG_BOOKMARK, bookmark) }
            }
    }
}
