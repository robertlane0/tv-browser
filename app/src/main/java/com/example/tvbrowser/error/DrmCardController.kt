package com.example.tvbrowser.error

import android.view.View

class DrmCardController(
    private val card: DrmCardView,
    private val refocus: () -> Unit
) {

    init {
        card.isFocusable = true
        card.onDismissKey = ::dismiss
    }

    fun show() {
        card.visibility = View.VISIBLE
        card.requestFocus()
    }

    fun isVisible(): Boolean = card.visibility == View.VISIBLE

    fun dismiss() {
        card.visibility = View.GONE
        refocus()
    }
}
