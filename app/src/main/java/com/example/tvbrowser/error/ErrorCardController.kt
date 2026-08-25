package com.example.tvbrowser.error

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.tvbrowser.R

/**
 * Binds the normative copy table and button set of spec 09 §4 onto the error
 * card views. Focus order is fixed: Retry → Switch User Agent → Home, with
 * Retry as default focus; renderer-exhaustion shows Home only.
 */
class ErrorCardController(
    private val card: ErrorCardView,
    private val iconView: ImageView,
    private val titleView: TextView,
    private val bodyView: TextView,
    private val retryButton: Button,
    private val switchUaButton: Button,
    private val homeButton: Button,
    private val onRetry: () -> Unit,
    private val onSwitchUserAgent: () -> Unit,
    private val onHome: () -> Unit,
    private val refocusPage: () -> Unit
) {

    private var shown: TvError? = null

    init {
        retryButton.setOnClickListener { onRetry() }
        switchUaButton.setOnClickListener { onSwitchUserAgent() }
        homeButton.setOnClickListener { onHome() }
        card.onDismissKey = { dismissedByUser() }
    }

    fun show(category: Category, httpCode: Int? = null, homeOnly: Boolean = false) {
        shown = TvError(category, httpCode)
        titleView.text = titleFor(category)
        bodyView.text = bodyFor(category, httpCode)
        iconView.contentDescription = titleFor(category)

        homeButton.isVisible = true
        retryButton.isVisible = !homeOnly
        switchUaButton.isVisible = !homeOnly

        card.isVisible = true
        if (homeOnly) homeButton.requestFocus() else retryButton.requestFocus()
    }

    fun isVisible(): Boolean = card.isVisible

    fun visibleCategory(): Category? = shown?.category?.takeIf { card.isVisible }

    /** Programmatic dismissal (retry fired); does not move focus back. */
    fun dismiss() {
        shown = null
        card.isVisible = false
    }

    private fun dismissedByUser() {
        val hadCategory = shown != null
        dismiss()
        if (hadCategory) refocusPage()
    }

    private fun titleFor(category: Category): String = card.context.getString(
        when (category) {
            Category.NETWORK -> R.string.error_title_network
            Category.HTTP_CLIENT -> R.string.error_title_http_client
            Category.HTTP_SERVER -> R.string.error_title_http_server
            Category.SSL -> R.string.error_title_ssl
            Category.BLOCKED -> R.string.error_title_blocked
            Category.DRM -> R.string.drm_error_title
            Category.RENDERER -> R.string.error_title_renderer
            Category.SAFEBROWSING -> R.string.error_title_safebrowsing
        }
    )

    private fun bodyFor(category: Category, httpCode: Int?): String {
        val res = card.context.resources
        return when (category) {
            Category.NETWORK -> res.getString(R.string.error_body_network)
            Category.HTTP_CLIENT -> res.getString(R.string.error_body_http_client, httpCode ?: 0)
            Category.HTTP_SERVER -> res.getString(R.string.error_body_http_server, httpCode ?: 0)
            Category.SSL -> res.getString(R.string.error_body_ssl)
            Category.BLOCKED -> res.getString(R.string.error_body_blocked)
            Category.DRM -> res.getString(R.string.drm_error_body)
            Category.RENDERER -> res.getString(R.string.error_body_renderer)
            Category.SAFEBROWSING -> res.getString(R.string.error_body_safebrowsing)
        }
    }
}
