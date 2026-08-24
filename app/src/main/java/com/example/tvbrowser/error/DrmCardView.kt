package com.example.tvbrowser.error

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

class DrmCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onDismissKey: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dismissKey = event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        if (visibility == VISIBLE && dismissKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                onDismissKey?.invoke()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
