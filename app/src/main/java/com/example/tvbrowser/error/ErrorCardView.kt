package com.example.tvbrowser.error

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Full-screen error card container of spec 09 §4.1. While visible it observes
 * every key (auto-retry cancellation) and dismisses on Back, returning focus
 * to the page behind the card.
 */
class ErrorCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onDismissKey: (() -> Unit)? = null

    var onAnyKeyWhileVisible: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (visibility != VISIBLE) return super.dispatchKeyEvent(event)
        onAnyKeyWhileVisible?.invoke()
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.repeatCount == 0 && event.action == KeyEvent.ACTION_DOWN) {
                onDismissKey?.invoke()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
