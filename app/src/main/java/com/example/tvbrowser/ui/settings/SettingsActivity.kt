package com.example.tvbrowser.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.example.tvbrowser.R
import com.example.tvbrowser.data.Bookmark

class SettingsActivity : FragmentActivity(), SettingsRadioStep.Host {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)

        if (supportFragmentManager.findFragmentById(R.id.settings_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment.newInstance(intent))
                .commit()
        }
    }

    override fun onRadioPicked(requestKey: String, value: String) {
        (supportFragmentManager.findFragmentById(R.id.settings_container) as? SettingsFragment)
            ?.handleRadioPicked(requestKey, value)
    }

    companion object {
        const val EXTRA_BOOKMARK = "com.example.tvbrowser.extra.BOOKMARK"
        const val RESULT_RELOAD_REQUESTED = "com.example.tvbrowser.result.RELOAD"
        const val REQUEST_SETTINGS = 4101

        fun createIntent(context: Context, sessionBookmark: Bookmark? = null): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                sessionBookmark?.let { putExtra(EXTRA_BOOKMARK, it) }
            }
    }
}
