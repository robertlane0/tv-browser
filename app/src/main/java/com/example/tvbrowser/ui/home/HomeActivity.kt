package com.example.tvbrowser.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.tvbrowser.R
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.PreferencesRepository
import com.example.tvbrowser.gate.WebViewProviderGate
import com.example.tvbrowser.ui.browser.BrowserActivity
import com.example.tvbrowser.ui.settings.SettingsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeActivity : FragmentActivity(), HomeFragment.Callbacks, GateBlockedStep.Host, BookmarkDetailsStep.Host {

    private lateinit var preferences: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        preferences = PreferencesRepository.getInstance(this)

        val (gateResult, gateAction) = WebViewProviderGate.evaluate(this)
        when (gateAction) {
            WebViewProviderGate.Action.PROCEED -> showHome()
            WebViewProviderGate.Action.WARNING -> {
                showHome()
                maybeShowOutdatedWarning(gateResult.majorVersion)
            }
            WebViewProviderGate.Action.BLOCKING_ERROR -> showBlockedScreen()
        }
    }

    private fun showHome() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.home_container, HomeFragment())
            .commit()
    }

    private fun showBlockedScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.home_container, GateBlockedStep())
            .commit()
        Toast.makeText(this, R.string.gate_blocked_title, Toast.LENGTH_LONG).show()
    }

    private fun maybeShowOutdatedWarning(majorVersion: Int) {
        lifecycleScope.launch {
            val warnedMajor = preferences.webviewVersionWarnedMajor().first()
            if (warnedMajor != majorVersion) {
                preferences.setWebviewVersionWarnedMajor(majorVersion)
                val banner = findViewById<TextView>(R.id.gate_warning_banner)
                banner.text = getString(R.string.gate_warning_text, majorVersion)
                banner.visibility = View.VISIBLE
                delay(WARNING_BANNER_DURATION_MS)
                banner.visibility = View.GONE
            }
        }
    }

    override fun onServiceLaunchRequested(bookmark: Bookmark) {
        lifecycleScope.launch {
            BookmarkRepository(AppDatabase.getInstance(applicationContext).bookmarkDao())
                .touchLaunched(bookmark.id)
        }
        startActivity(BrowserActivity.createIntent(this, bookmark))
    }

    override fun onAddServiceRequested() {
        supportFragmentManager.beginTransaction()
            .add(R.id.home_container, BookmarkDetailsStep.newInstance(null))
            .addToBackStack(null)
            .commit()
    }

    override fun onSettingsRequested() {
        startActivity(SettingsActivity.createIntent(this))
    }

    override fun stepContainerId(): Int = R.id.home_container

    override fun onServiceLongPressed(bookmark: Bookmark) {
        supportFragmentManager.beginTransaction()
            .add(R.id.home_container, ServiceActionsStep.newInstance(bookmark))
            .addToBackStack(null)
            .commit()
    }

    override fun onGateRetry() {
        recreate()
    }

    private fun toastNotAvailableYet() {
        Toast.makeText(this, R.string.toast_not_available_yet, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val WARNING_BANNER_DURATION_MS = 8_000L
    }
}
