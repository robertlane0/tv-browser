package com.example.tvbrowser.ui.browser

import android.os.Bundle
import android.text.InputType
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.example.tvbrowser.R
import com.example.tvbrowser.util.UrlNormalizer

class AddressInputStep : GuidedStepSupportFragment() {

    private var committed = false

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            getString(R.string.address_step_title),
            getString(R.string.address_step_description),
            null,
            null
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val prefill = requireArguments().getString(ARG_CURRENT_URL).orEmpty()

        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_INPUT)
                .editTitle(prefill)
                .description(R.string.overlay_address_hint)
                .editable(true)
                .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_DONE)
                .title(getString(R.string.address_step_done))
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_USE_GRID)
                .title(getString(R.string.address_step_use_grid))
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
        when (action.id) {
            ACTION_DONE -> handleAddressInput(
                findActionById(ACTION_INPUT)?.editTitle?.toString().orEmpty()
            )
            ACTION_USE_GRID -> showKeyGrid()
        }
    }

    internal fun handleAddressInput(raw: String): Boolean {
        val normalized = tryNormalize(raw)
        if (normalized == null) {
            showError(getString(R.string.address_step_error))
            return false
        }
        committed = true
        (parentFragment as Host).onAddressCommitted(normalized)
        parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        return true
    }

    private fun showKeyGrid() {
        val draft = findActionById(ACTION_INPUT)?.editTitle?.toString().orEmpty()
        parentFragmentManager.beginTransaction()
            .add(R.id.overlay_step_container, DpadKeyGridStep.newInstance(draft))
            .addToBackStack(null)
            .commit()
    }

    private fun showError(message: CharSequence) {
        findActionById(ACTION_ERROR)?.title = message
        val position = findActionPositionById(ACTION_ERROR)
        if (position >= 0) notifyActionChanged(position)
    }

    private fun tryNormalize(raw: String): String? =
        runCatching { UrlNormalizer.normalize(raw) }.getOrNull()

    interface Host {
        fun onAddressCommitted(normalizedUrl: String)

        fun onAddressEntryCancelled()
    }

    companion object {
        private const val ARG_CURRENT_URL = "current_url"
        private const val ACTION_INPUT = 1L
        private const val ACTION_DONE = 2L
        private const val ACTION_USE_GRID = 3L
        private const val ACTION_ERROR = 4L

        fun newInstance(currentUrl: String): AddressInputStep =
            AddressInputStep().apply {
                arguments = Bundle().apply { putString(ARG_CURRENT_URL, currentUrl) }
            }
    }
}
