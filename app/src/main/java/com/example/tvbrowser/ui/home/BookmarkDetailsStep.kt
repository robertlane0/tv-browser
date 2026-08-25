package com.example.tvbrowser.ui.home

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.example.tvbrowser.R
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import com.example.tvbrowser.util.UrlNormalizer
import kotlinx.coroutines.launch

class BookmarkDetailsStep : GuidedStepSupportFragment() {

    private var existing: Bookmark? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        existing = requireArguments().getParcelable(ARG_BOOKMARK)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            getString(
                if (existing == null) R.string.bookmark_details_title_add
                else R.string.bookmark_details_title_edit
            ),
            getString(R.string.bookmark_details_description),
            null,
            null
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val bookmark = existing

        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_TITLE)
                .editTitle(bookmark?.title.orEmpty())
                .description(getString(R.string.bookmark_action_title_hint))
                .editable(true)
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_URL)
                .editTitle(bookmark?.url.orEmpty())
                .description(getString(R.string.bookmark_action_url_hint))
                .editable(true)
                .inputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI)
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CONTINUE)
                .title(getString(R.string.bookmark_action_continue))
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_ERROR)
                .title("")
                .enabled(false)
                .focusable(false)
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id != ACTION_CONTINUE) return

        tryContinue(
            findActionById(ACTION_TITLE)?.editTitle?.toString()?.trim().orEmpty(),
            findActionById(ACTION_URL)?.editTitle?.toString()?.trim().orEmpty()
        )
    }

    internal var lastError: CharSequence? = null
        private set

    internal fun tryContinue(rawTitle: String, rawUrl: String): Boolean {
        val title = rawTitle.trim()
        val normalized = tryNormalize(rawUrl.trim())

        when {
            title.isEmpty() || title.length > MAX_TITLE_LENGTH -> {
                showError(getString(R.string.bookmark_details_error_title))
                return false
            }
            normalized == null -> {
                showError(getString(R.string.bookmark_details_error_url))
                return false
            }
            else -> viewLifecycleOwner.lifecycleScope.launch {
                val repository =
                    BookmarkRepository(AppDatabase.getInstance(requireContext()).bookmarkDao())
                val duplicate = repository.findByOrigin(Bookmark.originOf(normalized))
                openPlaybackStep(title, normalized, duplicate?.id != existing?.id)
            }
        }
        return true
    }

    private fun openPlaybackStep(title: String, url: String, duplicateWarning: Boolean) {
        val step = BookmarkPlaybackStep.newInstance(
            title = title,
            url = url,
            existing = existing,
            duplicateWarning = duplicateWarning
        )
        parentFragmentManager.beginTransaction()
            .add((requireActivity() as Host).stepContainerId(), step)
            .addToBackStack(null)
            .commit()
    }

    private fun showError(message: CharSequence) {
        lastError = message
        findActionById(ACTION_ERROR)?.title = message
        val position = findActionPositionById(ACTION_ERROR)
        if (position >= 0) notifyActionChanged(position)
    }

    private fun tryNormalize(raw: String): String? =
        runCatching { UrlNormalizer.normalize(raw) }.getOrNull()

    interface Host {
        fun stepContainerId(): Int
    }

    companion object {
        const val MAX_TITLE_LENGTH = 40
        private const val ARG_BOOKMARK = "bookmark"
        private const val ACTION_TITLE = 1L
        private const val ACTION_URL = 2L
        private const val ACTION_CONTINUE = 3L
        private const val ACTION_ERROR = 4L

        fun newInstance(existing: Bookmark?): BookmarkDetailsStep =
            BookmarkDetailsStep().apply {
                arguments = Bundle().apply { putParcelable(ARG_BOOKMARK, existing) }
            }
    }
}
