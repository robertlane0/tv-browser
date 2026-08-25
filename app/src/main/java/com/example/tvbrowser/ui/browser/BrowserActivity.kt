package com.example.tvbrowser.ui.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.fragment.app.FragmentActivity
import com.example.tvbrowser.R
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.ui.settings.SettingsActivity
import android.annotation.SuppressLint

class BrowserActivity : FragmentActivity() {

    private fun webViewFragment(): WebViewFragment? =
        supportFragmentManager.findFragmentById(R.id.browser_container) as? WebViewFragment

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

        supportFragmentManager.executePendingTransactions()
        webViewFragment()?.forceExitFullscreen()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fragment = webViewFragment()
        fragment?.notifyUserInteraction()
        if (fragment?.dispatchKeyEvent(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keyEvent = event ?: return super.onKeyDown(keyCode, event)
        val handled = webViewFragment()?.dispatchKeyDown(keyCode, keyEvent) ?: false
        return if (handled) true else super.onKeyDown(keyCode, keyEvent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SettingsActivity.REQUEST_SETTINGS &&
            resultCode == RESULT_OK &&
            data?.getBooleanExtra(SettingsActivity.RESULT_RELOAD_REQUESTED, false) == true
        ) {
            webViewFragment()?.reloadWithSessionBookmark()
        }
    }

    /**
     * Durability point for login sessions (spec 08 §4.1): the flush persists
     * in-memory cookies; it is async, so last-seconds loss on a hard kill is
     * documented and accepted. Cookies are never cleared here.
     */
    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BOOKMARK = "com.example.tvbrowser.extra.BOOKMARK"

        fun createIntent(context: Context, bookmark: Bookmark): Intent =
            Intent(context, BrowserActivity::class.java).putExtra(EXTRA_BOOKMARK, bookmark)
    }
}
