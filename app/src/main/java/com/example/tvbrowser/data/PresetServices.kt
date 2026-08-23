package com.example.tvbrowser.data

object PresetServices {

    fun all(now: Long = System.currentTimeMillis()): List<Bookmark> = listOf(
        bookmark("ARD Mediathek", "https://www.ardmediathek.de", 0, now),
        bookmark("ZDF", "https://www.zdf.de", 1, now),
        bookmark("RaiPlay", "https://www.raiplay.it", 2, now),
        bookmark("france.tv", "https://www.france.tv", 3, now),
        bookmark("NRK TV", "https://tv.nrk.no", 4, now)
    )

    private fun bookmark(title: String, url: String, sortOrder: Int, now: Long) = Bookmark(
        title = title,
        url = url,
        origin = Bookmark.originOf(url),
        uaMode = UaMode.DESKTOP,
        isPreset = true,
        sortOrder = sortOrder,
        createdAt = now
    )
}
