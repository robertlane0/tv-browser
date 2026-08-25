package com.example.tvbrowser.ui.settings

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction

class SettingsRadioStep : GuidedStepSupportFragment() {

    interface Host {
        fun onRadioPicked(requestKey: String, value: String)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        readArgs()
        super.onCreate(savedInstanceState)
    }

    private var requestKey = ""
    private var selected = ""
    private lateinit var labels: List<String>
    private lateinit var values: List<String>

    private fun readArgs() {
        val args = requireArguments()
        requestKey = args.getString(ARG_REQUEST_KEY).orEmpty()
        selected = args.getString(ARG_SELECTED).orEmpty()
        labels = args.getStringArrayList(ARG_LABELS).orEmpty()
        values = args.getStringArrayList(ARG_VALUES).orEmpty()
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            requireArguments().getString(ARG_TITLE).orEmpty(),
            null,
            null,
            null
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        values.forEachIndexed { index, value ->
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(BASE_ID + index)
                    .title(labels[index])
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .checked(value == selected)
                    .build()
            )
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val index = (action.id - BASE_ID).toInt()
        if (index < 0 || index >= values.size) return
        (requireActivity() as Host).onRadioPicked(requestKey, values[index])
        parentFragmentManager.popBackStack()
    }

    companion object {
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_TITLE = "title"
        private const val ARG_LABELS = "labels"
        private const val ARG_VALUES = "values"
        private const val ARG_SELECTED = "selected"
        private const val BASE_ID = 100L

        fun newInstance(
            requestKey: String,
            title: String,
            labels: List<String>,
            values: List<String>,
            selected: String
        ): SettingsRadioStep =
            SettingsRadioStep().apply {
                arguments = Bundle().apply {
                    putString(ARG_REQUEST_KEY, requestKey)
                    putString(ARG_TITLE, title)
                    putStringArrayList(ARG_LABELS, ArrayList(labels))
                    putStringArrayList(ARG_VALUES, ArrayList(values))
                    putString(ARG_SELECTED, selected)
                }
            }
    }
}
