package com.example.tvbrowser.ui.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.example.tvbrowser.R
import com.example.tvbrowser.data.Bookmark

class BrowserActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        @Suppress("DEPRECATION")
        val bookmark = intent.getParcelableExtra<Bookmark>(EXTRA_BOOKMARK) ?: run {
            finish()
            return
        }

        if (supportFragmentManager.findFragmentById(R.id.browser_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.browser_container, WebViewFragment.newInstance(bookmark))
                .commit()
        }
    }

    companion object {
        const val EXTRA_BOOKMARK = "com.example.tvbrowser.extra.BOOKMARK"

        fun createIntent(context: Context, bookmark: Bookmark): Intent =
            Intent(context, BrowserActivity::class.java).putExtra(EXTRA_BOOKMARK, bookmark)
    }
}
