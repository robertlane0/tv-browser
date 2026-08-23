package com.example.tvbrowser.ui.browser

interface BrowserOverlayController {

    val isVisible: Boolean

    fun show()

    fun hide()

    fun toggle()
}
