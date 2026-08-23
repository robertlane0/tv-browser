package com.example.tvbrowser.ui.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.tvbrowser.data.Bookmark

class BrowserActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    companion object {
        const val EXTRA_BOOKMARK = "com.example.tvbrowser.extra.BOOKMARK"

        fun createIntent(context: Context, bookmark: Bookmark): Intent =
            Intent(context, BrowserActivity::class.java).putExtra(EXTRA_BOOKMARK, bookmark)
    }
}
