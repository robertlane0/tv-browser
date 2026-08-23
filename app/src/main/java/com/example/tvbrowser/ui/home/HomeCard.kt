package com.example.tvbrowser.ui.home

import com.example.tvbrowser.data.Bookmark

sealed class HomeCard {

    data class Service(val bookmark: Bookmark) : HomeCard()

    object GhostAddFirst : HomeCard()

    object AddService : HomeCard()

    object Settings : HomeCard()
}
