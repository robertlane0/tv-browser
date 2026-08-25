package com.example.tvbrowser.ui.browser

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.tvbrowser.R
import com.example.tvbrowser.util.UrlNormalizer

class DpadKeyGridStep : androidx.fragment.app.Fragment() {

    private var committed = false
    private var draft = ""
    private var errored = false

    private lateinit var draftView: TextView
    private lateinit var errorView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        draft = requireArguments().getString(ARG_DRAFT).orEmpty()
        return buildGrid(container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        draftView = view.findViewById(R.id.key_grid_draft)
        errorView = view.findViewById(R.id.key_grid_error)
        renderDraft()
        view.requestFocus()
    }

    private fun buildGrid(container: ViewGroup?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE000000"))
            val pad = dp(48)
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        draftView = TextView(context).apply {
            id = R.id.key_grid_draft
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(56)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(
            draftView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        errorView = TextView(context).apply {
            id = R.id.key_grid_error
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.security_cleartext))
            visibility = View.GONE
        }
        root.addView(
            errorView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val grid = GridLayout(context).apply { columnCount = GRID_COLUMNS }
        root.addView(grid)

        KEY_ROWS.flatMap { it.toList() }.forEach { char ->
            grid.addView(
                keyButton(char.toString()) {
                    append(char)
                }
            )
        }
        grid.addView(keyButton(getString(R.string.key_grid_delete)) { deleteLast() })
        grid.addView(keyButton(getString(R.string.key_grid_clear)) { clearAll() })
        grid.addView(keyButton(getString(R.string.key_grid_done)) { commit() })

        return root
    }

    private fun keyButton(label: String, onClick: () -> Unit): Button =
        Button(requireContext()).apply {
            text = label
            textSize = 20f
            isFocusable = true
            minWidth = dp(72)
            minimumHeight = dp(56)
            setOnClickListener {
                onClick()
                renderDraft()
            }
        }

    private fun renderDraft() {
        if (!errored) {
            draftView.text = draft.ifEmpty { getString(R.string.address_step_title) }
        }
    }

    private fun markEdited() {
        errored = false
        errorView.visibility = View.GONE
    }

    internal fun append(char: Char) {
        markEdited()
        draft += char
    }

    internal fun deleteLast() {
        markEdited()
        if (draft.isNotEmpty()) draft = draft.dropLast(1)
    }

    internal fun clearAll() {
        markEdited()
        draft = ""
    }

    internal fun commit() {
        val normalized = runCatching { UrlNormalizer.normalize(draft) }.getOrNull()
        if (normalized == null) {
            errored = true
            errorView.text = getString(R.string.address_step_error)
            errorView.visibility = View.VISIBLE
            return
        }
        committed = true
        (parentFragment as Host).onAddressCommitted(normalized)
        parentFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (!committed) (parentFragment as Host).onAddressEntryCancelled()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    internal val currentDraft: String
        get() = draft

    internal fun errorShown(): Boolean =
        ::errorView.isInitialized && errorView.visibility == View.VISIBLE

    interface Host {
        fun onAddressCommitted(normalizedUrl: String)

        fun onAddressEntryCancelled()
    }

    companion object {
        private const val ARG_DRAFT = "draft"
        private const val GRID_COLUMNS = 7
        private val KEY_ROWS = listOf("abcdefg", "hijklmn", "opqrstu", "vwxyz./", "0123456")

        fun newInstance(draftText: String): DpadKeyGridStep =
            DpadKeyGridStep().apply {
                arguments = Bundle().apply { putString(ARG_DRAFT, draftText) }
            }
    }
}
