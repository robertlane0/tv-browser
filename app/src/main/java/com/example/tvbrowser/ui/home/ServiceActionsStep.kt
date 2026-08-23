package com.example.tvbrowser.ui.home

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
import kotlinx.coroutines.launch

class ServiceActionsStep : GuidedStepSupportFragment() {

    private lateinit var bookmark: Bookmark

    override fun onCreate(savedInstanceState: Bundle?) {
        bookmark = requireArguments().getParcelable(ARG_BOOKMARK)!!
        super.onCreate(savedInstanceState)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(bookmark.title, null, null, null)

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_EDIT)
                .title(getString(R.string.context_edit))
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CHANGE_UA)
                .title(getString(R.string.context_change_ua))
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_DELETE)
                .title(getString(R.string.context_delete))
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_EDIT, ACTION_CHANGE_UA -> {
                Toast.makeText(
                    requireContext(),
                    R.string.toast_not_available_yet,
                    Toast.LENGTH_SHORT
                ).show()
            }
            ACTION_DELETE -> {
                val repository =
                    BookmarkRepository(AppDatabase.getInstance(requireContext()).bookmarkDao())
                lifecycleScope.launch {
                    repository.delete(bookmark)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_deleted, bookmark.title),
                        Toast.LENGTH_SHORT
                    ).show()
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }

    companion object {
        private const val ARG_BOOKMARK = "bookmark"
        private const val ACTION_EDIT = 1L
        private const val ACTION_CHANGE_UA = 2L
        private const val ACTION_DELETE = 3L

        fun newInstance(bookmark: Bookmark): ServiceActionsStep =
            ServiceActionsStep().apply {
                arguments = Bundle().apply { putParcelable(ARG_BOOKMARK, bookmark) }
            }
    }
}
