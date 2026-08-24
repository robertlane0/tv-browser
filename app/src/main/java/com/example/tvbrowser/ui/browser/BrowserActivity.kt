package com.example.tvbrowser.ui.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fragment = supportFragmentManager.findFragmentById(R.id.browser_container) as? WebViewFragment
        if (fragment?.dispatchKeyEvent(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keyEvent = event ?: return super.onKeyDown(keyCode, event)
        val handled = (supportFragmentManager.findFragmentById(R.id.browser_container) as? WebViewFragment)
            ?.dispatchKeyDown(keyCode, keyEvent) ?: false
        return if (handled) true else super.onKeyDown(keyCode, keyEvent)
    }

    companion object {
        const val EXTRA_BOOKMARK = "com.example.tvbrowser.extra.BOOKMARK"

        fun createIntent(context: Context, bookmark: Bookmark): Intent =
            Intent(context, BrowserActivity::class.java).putExtra(EXTRA_BOOKMARK, bookmark)
    }
}
