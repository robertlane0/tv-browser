package com.example.tvbrowser.web

interface FullscreenController {

    fun isInFullscreen(): Boolean

    fun exitFullscreen()

    fun forceTeardown()
}
