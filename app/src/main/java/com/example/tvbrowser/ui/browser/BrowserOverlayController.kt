package com.example.tvbrowser.ui.browser

interface BrowserOverlayController {

    val isVisible: Boolean

    val isPinned: Boolean get() = false

    fun show()

    fun hide()

    fun toggle()

    fun setPinned(pinned: Boolean) {}

    fun onUserInteraction() {}
}
